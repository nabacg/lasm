#!/usr/bin/env clojure

;; Smoke test - verify examples actually parse and compile
(require '[lasm.parser :as parser]
         '[lasm.ast :as ast]
         '[instaparse.core :as insta])

(defn test-file [filename]
  (println (str "\n=== Testing " filename " ==="))
  (try
    (let [code (slurp filename)
          parsed (parser/parser code)]
      (if (insta/failure? parsed)
        (do
          (println "❌ PARSE FAILED!")
          (println parsed)
          false)
        (do
          (println "✓ Parse OK")
          (try
            (let [ast-tree (parser/parse-tree-to-ast parsed)
                  program (ast/build-program ast-tree)]
              (println "✓ AST OK")
              (println "✓ Program built OK")
              true)
            (catch Exception e
              (println "❌ AST/Program build FAILED!")
              (println (.getMessage e))
              false)))))
    (catch Exception e
      (println "❌ EXCEPTION!")
      (println (.getMessage e))
      false)))

(println "==========================================")
(println "  LASM Examples - Smoke Test")
(println "==========================================")

(def test-files
  ["examples/01_simple_window.lasm"
   "examples/02_window_with_label.lasm"
   "examples/03_pong.lasm"
   "examples/04_animated_pong.lasm"
   "examples/05_keyboard_test.lasm"
   "examples/06_pong_full_game.lasm"])

(def results (map test-file test-files))
(def passed (count (filter identity results)))
(def total (count results))

(println "\n==========================================")
(println (str "  Results: " passed "/" total " passed"))
(println "==========================================")

(if (= passed total)
  (do
    (println "\n✅ ALL TESTS PASSED!")
    (System/exit 0))
  (do
    (println "\n❌ SOME TESTS FAILED!")
    (System/exit 1)))
