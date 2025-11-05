(require '[lasm.parser :as p] '[instaparse.core :as insta])

(println "Testing static method call parsing...")
(let [code "result:int = java.lang.Math/abs(-42)\nprintint(result)"
      parsed (p/parser code)]
  (if (insta/failure? parsed)
    (do
      (println "\n❌ PARSE FAILED:")
      (clojure.pprint/pprint parsed))
    (do
      (println "\n✅ PARSE SUCCESS:")
      (clojure.pprint/pprint parsed))))
