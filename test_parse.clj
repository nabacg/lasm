(load-file "src/lasm/parser.clj")
(require '[instaparse.core :as insta])

(println "Testing: java.lang.Math/abs(-42)")
(let [result (lasm.parser/parser "java.lang.Math/abs(-42)")]
  (if (insta/failure? result)
    (do
      (println "FAILED:")
      (clojure.pprint/pprint result))
    (do  
      (println "SUCCESS:")
      (clojure.pprint/pprint result))))

(println "\nTesting: result:int = java.lang.Math/abs(-42)")  
(let [result (lasm.parser/parser "result:int = java.lang.Math/abs(-42)\nprintint(result)")]
  (if (insta/failure? result)
    (do
      (println "FAILED:")
      (clojure.pprint/pprint result))
    (do
      (println "SUCCESS:")
      (clojure.pprint/pprint result))))
