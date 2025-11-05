#!/usr/bin/env clojure

;; Run the Pong example directly
(require '[lasm.parser :as parser]
         '[lasm.ast :as ast]
         '[lasm.emit :as emit]
         '[instaparse.core :as insta])

(println "Loading Pong example...")

(let [code (slurp "examples/03_pong.lasm")
      _ (println "\n=== CODE ===")
      _ (println code)
      _ (println "\n=== PARSING ===")
      parsed (parser/parser code)]

  (if (insta/failure? parsed)
    (do
      (println "\n❌ PARSE FAILED!")
      (println (pr-str parsed))
      (System/exit 1))
    (do
      (println "✓ Parse successful")
      (println "\n=== BUILDING AST ===")
      (let [ast-tree (parser/parse-tree-to-ast parsed)]
        (println "✓ AST built")
        (println "\n=== BUILDING PROGRAM ===")
        (let [program (ast/build-program ast-tree)]
          (println "✓ Program built")
          (println "\n=== RUNNING ===")
          (emit/emit-and-run! program)
          (println "\n✓ Complete!")
          (println "\nKeeping window open - press Ctrl+C to exit")
          (Thread/sleep Long/MAX_VALUE))))))
