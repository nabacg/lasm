(ns lasm.cli
  "CLI utilities for Lasm development and testing"
  (:require [lasm.parser :as parser]
            [lasm.ast :as ast]
            [lasm.emit :as emit]
            [lasm.jar :as jar]
            [clojure.pprint :as pprint]))

(defn parse [source] (parser/parser source))

(defn ast [source]
  (-> source parser/parser parser/parse-tree-to-ast))

(defn program [source]
  (-> source parser/parser parser/parse-tree-to-ast ast/build-program))

(defn run [source]
  (-> source parser/parser parser/parse-tree-to-ast ast/build-program emit/emit-and-run!))

(defn run-file [filename] (run (slurp filename)))

(defn -main [& args]
  (if (empty? args)
    (println "Usage: clj -M -m lasm.cli <command> [file] [options]
Commands:
  run <file>              Compile and run program
  compile <file> [-o jar] Compile to JAR
  parse <file>            Show parse tree
  ast <file>              Show AST
  program <file>          Show program IR

Examples:
  clj -M -m lasm.cli run examples/04_animated_pong.lasm
  clj -M -m lasm.cli compile examples/03_pong.lasm -o pong.jar")
    (let [[cmd file & opts] args]
      (case cmd
        "parse"   (pprint/pprint (parse (slurp file)))
        "ast"     (pprint/pprint (ast (slurp file)))
        "program" (pprint/pprint (program (slurp file)))
        "run"     (run (slurp file))
        "compile" (let [output (when (= "-o" (first opts)) (second opts))]
                    (jar/compile-file file output))
        (println "Unknown command:" cmd)))))
