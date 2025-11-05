(ns run-example
  (:require [lasm.parser :as parser]
            [lasm.ast :as ast]
            [lasm.emit :as emit]
            [clojure.java.io :as io]))

(defn run-lasm-file [filename]
  (let [code (slurp filename)
        _    (println "Parsing:" filename)
        parsed (parser/parser code)
        _    (println "Parsed:" parsed)
        _    (when (parser/insta.core/failure? parsed)
               (throw (ex-info "Parse error" {:parsed parsed})))
        ast-tree (parser/parse-tree-to-ast parsed)
        _    (println "AST:" ast-tree)
        program (ast/build-program ast-tree)
        _    (println "Program:" program)]
    (emit/emit-and-run! program)))

(defn -main [& args]
  (if (empty? args)
    (println "Usage: clojure -M:run-example <lasm-file>")
    (run-lasm-file (first args))))

(when (seq *command-line-args*)
  (apply -main *command-line-args*))
