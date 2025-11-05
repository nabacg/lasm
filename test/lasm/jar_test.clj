(ns lasm.jar-test
  "Tests for JAR compilation"
  (:require [clojure.test :refer :all]
            [lasm.jar :as jar]
            [lasm.parser :as p]
            [lasm.ast :as ast]
            [clojure.java.io :as io])
  (:import [java.util.jar JarFile JarEntry]
           [java.io File]))

(deftest test-compile-to-bytecode
  (testing "Compile simple program to bytecode"
    (let [code "fn test(): int => 42\ntest()"
          result (jar/compile-to-bytecode code)]
      (is (map? result) "Should return a map")
      (is (:bytecode-map result) "Should have bytecode-map")
      (is (:entry-point result) "Should have entry-point")
      (is (> (count (:bytecode-map result)) 0) "Should have at least one class")
      (is (string? (:entry-point result)) "Entry point should be a string"))))

(deftest test-bytecode-map-contents
  (testing "Bytecode map contains valid class files"
    (let [code "fn add(x: int, y: int): int => x + y\nadd(1, 2)"
          {:keys [bytecode-map entry-point]} (jar/compile-to-bytecode code)]
      (is (seq bytecode-map) "Should have classes")
      (doseq [[class-name bytecode] bytecode-map]
        (is (string? class-name) "Class name should be a string")
        (is (bytes? bytecode) "Bytecode should be byte array")
        (is (> (count bytecode) 0) "Bytecode should not be empty")
        ;; Check for Java class file magic number (0xCAFEBABE)
        (is (= (byte 0xCA) (aget bytecode 0)) "Should have Java class magic number")
        (is (= (byte 0xFE) (aget bytecode 1)) "Should have Java class magic number")))))

(deftest test-create-jar-simple
  (testing "Create JAR file from simple code"
    (let [code "fn main(): void => printstr(\"Hello JAR!\")\nmain()"
          jar-path "/tmp/test-simple.jar"]
      (try
        (.delete (io/file jar-path)) ; Clean up if exists
        (catch Exception _))

      (jar/create-jar code jar-path)

      (is (.exists (io/file jar-path)) "JAR file should be created")

      ;; Verify JAR structure
      (with-open [jar-file (JarFile. jar-path)]
        (is (.getManifest jar-file) "Should have manifest")
        (let [manifest (.getManifest jar-file)
              main-class (.getValue (.getMainAttributes manifest) "Main-Class")]
          (is (string? main-class) "Should have Main-Class in manifest")
          (is (seq main-class) "Main-Class should not be empty"))

        ;; Check that JAR has class files
        (let [entries (enumeration-seq (.entries jar-file))
              class-entries (filter #(.endsWith (.getName %) ".class") entries)]
          (is (> (count class-entries) 0) "Should have at least one class file")))

      ;; Clean up
      (.delete (io/file jar-path)))))

(deftest test-compile-file
  (testing "Compile .lasm file to JAR"
    (let [lasm-code "fn test(): int => 123\ntest()"
          lasm-path "/tmp/test-compile.lasm"
          jar-path "/tmp/test-compile.jar"]

      ;; Create temporary lasm file
      (spit lasm-path lasm-code)

      (try
        (.delete (io/file jar-path))
        (catch Exception _))

      ;; Compile it
      (jar/compile-file lasm-path jar-path)

      (is (.exists (io/file jar-path)) "Output JAR should exist")

      ;; Clean up
      (.delete (io/file lasm-path))
      (.delete (io/file jar-path)))))

(deftest test-compile-file-default-output
  (testing "Compile file with default output name"
    (let [lasm-code "fn test(): int => 456\ntest()"
          lasm-path "/tmp/test-default.lasm"
          expected-jar "/tmp/test-default.jar"]

      (spit lasm-path lasm-code)

      (try
        (.delete (io/file expected-jar))
        (catch Exception _))

      ;; Compile without specifying output
      (jar/compile-file lasm-path)

      (is (.exists (io/file expected-jar)) "Should create JAR with default name")

      ;; Clean up
      (.delete (io/file lasm-path))
      (.delete (io/file expected-jar)))))

(deftest test-jar-with-interop
  (testing "Compile code with Java interop"
    (let [code "frame:javax.swing.JFrame = new javax.swing.JFrame(\"Test\")\nprintstr(\"ok\")"
          jar-path "/tmp/test-interop.jar"]

      (try
        (.delete (io/file jar-path))
        (catch Exception _))

      (jar/create-jar code jar-path)

      (is (.exists (io/file jar-path)) "Should compile interop code to JAR")

      (.delete (io/file jar-path)))))

(deftest test-jar-with-functions
  (testing "Compile code with multiple functions"
    (let [code "fn add(x: int, y: int): int => x + y\nfn mul(x: int, y: int): int => x * y\nprintint(add(mul(2, 3), 4))"
          jar-path "/tmp/test-functions.jar"]

      (try
        (.delete (io/file jar-path))
        (catch Exception _))

      (jar/create-jar code jar-path)

      (is (.exists (io/file jar-path)) "Should compile multiple functions")

      (with-open [jar-file (JarFile. jar-path)]
        (let [entries (enumeration-seq (.entries jar-file))
              class-entries (filter #(.endsWith (.getName %) ".class") entries)]
          (is (>= (count class-entries) 3) "Should have multiple class files (add, mul, main)")))

      (.delete (io/file jar-path)))))

(deftest test-example-files-compile-to-jar
  (testing "All example files can be compiled to JAR"
    (doseq [example ["examples/01_simple_window.lasm"
                     "examples/02_window_with_label.lasm"
                     "examples/03_pong.lasm"
                     "examples/04_pong_with_logic.lasm"]]
      (when (.exists (io/file example))
        (let [jar-path (str "/tmp/" (.getName (io/file example)) ".jar")]
          (try
            (.delete (io/file jar-path))
            (catch Exception _))

          (testing (str "Compiling " example)
            (jar/compile-file example jar-path)
            (is (.exists (io/file jar-path)) (str example " should compile to JAR"))
            (.delete (io/file jar-path))))))))
