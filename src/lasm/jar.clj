(ns lasm.jar
  "Compile lasm programs to standalone executable JAR files"
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [lasm.parser :as parser]
            [lasm.ast :as ast]
            [lasm.emit :as emit]
            [instaparse.core :as insta])
  (:import [java.util.jar JarOutputStream JarEntry Manifest Attributes$Name]
           [java.io FileOutputStream]))

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
        _ (when (insta/failure? parsed)
            (throw (ex-info "Parse error" {:error (str parsed)})))
        ast-tree (parser/parse-tree-to-ast parsed)
        program (ast/build-program ast-tree)
        {:keys [fns entry-point]} program
        ;; Bind proxy bytecodes atom to collect proxy classes during compilation
        proxy-bytecodes (atom {})
        bytecode-map (binding [emit/*proxy-bytecodes* proxy-bytecodes]
                       (into {}
                             (map (fn [fn-def]
                                    (let [class-name (:class-name fn-def)
                                          bytecode (if (= class-name entry-point)
                                                     (emit/make-fn-bytecode-with-main fn-def)
                                                     (emit/make-fn-bytecode fn-def))]
                                      [class-name bytecode]))
                                  fns)))
        ;; Merge proxy classes into the bytecode map
        all-bytecodes (merge bytecode-map @proxy-bytecodes)]
    {:bytecode-map all-bytecodes
     :entry-point entry-point}))

(defn create-jar
  "Create a standalone JAR file from lasm source code."
  [lasm-code output-jar-path]
  (let [{:keys [bytecode-map entry-point]} (compile-to-bytecode lasm-code)
        manifest (make-manifest entry-point)
        jar-file (io/file output-jar-path)]
    (io/make-parents jar-file)
    (with-open [jar-out (JarOutputStream. (FileOutputStream. jar-file) manifest)]
      (doseq [[class-name bytecode] bytecode-map]
        (write-class-entry jar-out class-name bytecode)))
    {:jar-path output-jar-path
     :entry-point entry-point
     :class-count (count bytecode-map)}))

(defn compile-file
  "Compile a .lasm file to a .jar file."
  [lasm-file-path & [output-jar-path]]
  (let [lasm-code (slurp lasm-file-path)
        output-path (or output-jar-path
                        (string/replace lasm-file-path #"\.lasm$" ".jar"))]
    (println "Compiling" lasm-file-path "->" output-path)
    (let [result (create-jar lasm-code output-path)]
      (println "Created JAR:" output-path
               "| Entry point:" (:entry-point result)
               "| Classes:" (:class-count result))
      result)))
