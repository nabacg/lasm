#!/usr/bin/env clojure

;; Run the Pong example directly
(require '[lasm.parser :as parser]
         '[lasm.ast :as ast]
         '[lasm.emit :as emit])

(println "Loading Pong example...")

(-> (slurp "examples/03_pong.lasm")
    parser/parser
    parser/parse-tree-to-ast
    ast/build-program
    emit/emit-and-run!)

(println "\nKeeping window open - press Ctrl+C to exit")
(Thread/sleep Long/MAX_VALUE)
