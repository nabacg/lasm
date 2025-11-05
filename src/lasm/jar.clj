(ns lasm.jar
  "Compile lasm programs to standalone executable JAR files"
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [lasm.parser :as parser]
            [lasm.ast :as ast]
            [lasm.emit :as emit])
  (:import [java.util.jar JarOutputStream JarEntry Manifest Attributes$Name]
           [java.io File FileOutputStream ByteArrayOutputStream]
           [org.objectweb.asm ClassWriter]))

(defn make-manifest
  "Create a JAR manifest with the given main class"
  [main-class]
  (let [manifest (Manifest.)]
    (.. manifest getMainAttributes (put Attributes$Name/MANIFEST_VERSION "1.0"))
    (.. manifest getMainAttributes (put Attributes$Name/MAIN_CLASS main-class))
    manifest))

(defn write-class-entry
  "Write a class file to the JAR"
  [^JarOutputStream jar-out class-name ^bytes bytecode]
  (let [entry-name (str (string/replace class-name "." "/") ".class")
        entry (JarEntry. entry-name)]
    (.putNextEntry jar-out entry)
    (.write jar-out bytecode)
    (.closeEntry jar-out)))

(defn compile-to-bytecode
  "Compile a lasm program and return a map of {class-name -> bytecode}"
  [lasm-code]
  (let [parsed (parser/parser lasm-code)
        _ (when (parser/insta/failure? parsed)
            (throw (ex-info "Parse error" {:error (str parsed)})))
        ast-tree (parser/parse-tree-to-ast parsed)
        program (ast/build-program ast-tree)
        {:keys [fns entry-point]} program

        ;; Compile each function to bytecode
        bytecode-map (into {}
                           (map (fn [fn-def]
                                  (let [class-name (:class-name fn-def)
                                        bytecode (emit/make-fn-bytecode fn-def)]
                                    [class-name bytecode]))
                                fns))]
    {:bytecode-map bytecode-map
     :entry-point entry-point}))

(defn create-jar
  "Create a standalone JAR file from lasm source code.

   Args:
     lasm-code - The lasm source code as a string
     output-jar-path - Path where the JAR file should be written

   Returns:
     The path to the created JAR file

   Example:
     (create-jar \"fn main(): void => printstr(\\\"Hello!\\\")\\nmain()\" \"hello.jar\")"
  [lasm-code output-jar-path]
  (let [{:keys [bytecode-map entry-point]} (compile-to-bytecode lasm-code)
        manifest (make-manifest entry-point)
        jar-file (io/file output-jar-path)]

    ;; Create parent directories if needed
    (io/make-parents jar-file)

    ;; Write JAR file
    (with-open [jar-out (JarOutputStream. (FileOutputStream. jar-file) manifest)]
      (doseq [[class-name bytecode] bytecode-map]
        (write-class-entry jar-out class-name bytecode)))

    (println "✓ Created JAR:" output-jar-path)
    (println "  Entry point:" entry-point)
    (println "  Classes:" (count bytecode-map))
    (println "\nRun with: java -jar" output-jar-path)
    output-jar-path))

(defn compile-file
  "Compile a .lasm file to a .jar file.

   Args:
     lasm-file-path - Path to the .lasm source file
     output-jar-path (optional) - Path for output JAR (defaults to same name with .jar extension)

   Returns:
     The path to the created JAR file

   Example:
     (compile-file \"examples/03_pong.lasm\" \"pong.jar\")"
  [lasm-file-path & [output-jar-path]]
  (let [lasm-code (slurp lasm-file-path)
        output-path (or output-jar-path
                        (string/replace lasm-file-path #"\.lasm$" ".jar"))]
    (println "Compiling" lasm-file-path "->" output-path)
    (create-jar lasm-code output-path)))

(comment
  ;; Compile Pong to JAR
  (compile-file "examples/03_pong.lasm" "pong.jar")

  ;; Run it:
  ;; java -jar pong.jar

  ;; Compile inline code
  (create-jar
   "fn main(): void => {
      printstr(\"Hello from JAR!\")
    }
    main()"
   "hello.jar")

  ;; Compile all examples
  (doseq [file ["examples/01_simple_window.lasm"
                "examples/02_window_with_label.lasm"
                "examples/03_pong.lasm"
                "examples/04_pong_with_logic.lasm"]]
    (compile-file file))
  )
