(ns lasm.cli
  "CLI utilities for Lasm development and testing"
  (:require [lasm.parser :as parser]
            [lasm.ast :as ast]
            [lasm.emit :as emit]
            [clojure.pprint :as pprint]))

(defn parse
  "Parse Lasm source and return parse tree"
  [source]
  (parser/parser source))

(defn parse-pprint
  "Parse and pretty-print parse tree"
  [source]
  (pprint/pprint (parse source)))

(defn ast
  "Parse Lasm source and return AST"
  [source]
  (-> source
      parser/parser
      parser/parse-tree-to-ast))

(defn ast-pprint
  "Parse and pretty-print AST"
  [source]
  (pprint/pprint (ast source)))

(defn program
  "Build full program (AST + IR) from Lasm source"
  [source]
  (-> source
      parser/parser
      parser/parse-tree-to-ast
      ast/build-program))

(defn program-pprint
  "Build and pretty-print program"
  [source]
  (pprint/pprint (program source)))

(defn run
  "Compile and run Lasm source, return result"
  [source]
  (-> source
      parser/parser
      parser/parse-tree-to-ast
      ast/build-program
      emit/emit-and-run!))

(defn run-quiet
  "Compile and run Lasm source without debug output"
  [source]
  (binding [*out* (java.io.StringWriter.)]
    (run source)))

(defn compile-only
  "Compile Lasm source without running"
  [source]
  (-> source
      parser/parser
      parser/parse-tree-to-ast
      ast/build-program
      emit/emit!))

;; REPL helpers
(defn from-stdin
  "Read from stdin and return as string"
  []
  (slurp *in*))

(defn parse-stdin
  "Parse Lasm source from stdin and print parse tree"
  []
  (parse-pprint (from-stdin)))

(defn ast-stdin
  "Parse Lasm source from stdin and print AST"
  []
  (ast-pprint (from-stdin)))

(defn program-stdin
  "Build program from stdin and print it"
  []
  (program-pprint (from-stdin)))

(defn run-stdin
  "Run Lasm source from stdin"
  []
  (run (from-stdin)))

;; File helpers
(defn parse-file
  "Parse Lasm file and print parse tree"
  [filename]
  (parse-pprint (slurp filename)))

(defn ast-file
  "Parse Lasm file and print AST"
  [filename]
  (ast-pprint (slurp filename)))

(defn program-file
  "Build program from file and print it"
  [filename]
  (program-pprint (slurp filename)))

(defn run-file
  "Run Lasm file"
  [filename]
  (run (slurp filename)))

(defn -main
  "CLI entry point with subcommands"
  [& args]
  (if (empty? args)
    (println "Usage: clj -M -m lasm.cli <command> [file]
Commands:
  parse <file|->    Parse and show parse tree
  ast <file|->      Parse and show AST
  program <file|->  Build and show program (IR)
  run <file|->      Compile and run program

Use '-' to read from stdin

Examples:
  echo 'printint(42)' | clj -M -m lasm.cli run -
  clj -M -m lasm.cli ast examples/01_hello.lasm
  clj -M -m lasm.cli run examples/03_pong.lasm")
    (let [[cmd file-or-stdin] args
          source (if (= file-or-stdin "-")
                   (slurp *in*)
                   (slurp file-or-stdin))]
      (case cmd
        "parse"   (parse-pprint source)
        "ast"     (ast-pprint source)
        "program" (program-pprint source)
        "run"     (run source)
        (println "Unknown command:" cmd)))))

(comment
  ;; REPL usage examples:

  ;; Quick test
  (run "printint(42)")

  ;; Check AST
  (ast "fn f(x:int):int => x + 1")

  ;; Check IR
  (program "fn f(x:int):int => x + 1
            printint(f(41))")

  ;; From file
  (run-file "examples/01_hello.lasm")

  ;; Parse string and inspect
  (-> "x:int = 42" ast pprint/pprint))
