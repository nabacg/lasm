(ns lasm.jar-test
  (:require [clojure.test :refer :all]
            [lasm.jar :as jar]
            [clojure.java.io :as io])
  (:import [java.util.jar JarFile]))

(deftest compile-to-bytecode-test
  (testing "Compiles simple program to bytecode map"
    (let [result (jar/compile-to-bytecode "fn hello(): int => {\n  printstr(\"hi\")\n  42\n}\nhello()")]
      (is (map? (:bytecode-map result)))
      (is (string? (:entry-point result)))
      (is (pos? (count (:bytecode-map result)))))))

(deftest create-jar-test
  (testing "Creates a valid JAR file"
    (let [jar-path "/tmp/lasm_test_create.jar"
          result (jar/create-jar "fn hello(): int => {\n  printstr(\"hi\")\n  42\n}\nhello()" jar-path)]
      (is (= jar-path (:jar-path result)))
      (is (.exists (io/file jar-path)))
      ;; Verify it's a valid JAR
      (let [jf (JarFile. jar-path)]
        (is (.getManifest jf))
        (is (pos? (count (enumeration-seq (.entries jf)))))
        (.close jf))
      (io/delete-file jar-path true))))

(deftest compile-file-test
  (testing "Compiles example file to JAR"
    (let [jar-path "/tmp/lasm_test_example.jar"
          result (jar/compile-file "examples/01_simple_window.lasm" jar-path)]
      (is (.exists (io/file jar-path)))
      (is (= jar-path (:jar-path result)))
      (is (pos? (:class-count result)))
      (io/delete-file jar-path true))))

(deftest jar-has-main-class-test
  (testing "JAR manifest specifies Main-Class"
    (let [jar-path "/tmp/lasm_test_main.jar"
          result (jar/create-jar "fn hello(): int => {\n  printstr(\"hi\")\n  42\n}\nhello()" jar-path)
          jf (JarFile. jar-path)
          manifest (.getManifest jf)
          main-class (.getValue (.getMainAttributes manifest) "Main-Class")]
      (is (= (:entry-point result) main-class))
      (.close jf)
      (io/delete-file jar-path true))))

(deftest multi-function-jar-test
  (testing "Compiles multi-function program"
    (let [jar-path "/tmp/lasm_test_multi.jar"
          result (jar/compile-file "examples/03_pong.lasm" jar-path)]
      (is (> (:class-count result) 1) "Should have multiple classes")
      (io/delete-file jar-path true))))
