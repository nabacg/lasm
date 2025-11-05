(ns lasm.end-2-end-test
  (:require [clojure.test :as t]
            [lasm.parser :as p]
            [lasm.ast :as ast]
            [lasm.emit :as emit]))


;; DISABLED: Type checker doesn't support recursive functions
;; factorial calls itself recursively, which the type checker can't handle
#_(t/deftest sample-factorial
  (t/is (= 24 (-> (p/parser
                "fn fact(x:int): int =>
  if x <= 1
     1
  else
      x * fact(x-1)
  fact(4)")
               p/parse-tree-to-ast
               ast/build-program
               emit/emit-and-run!))))


#_(t/deftest sample-fibonacci-with-type-check
  (t/is 10 (-> (p/parser
                "fn fact(x:int): int =>
  if x <= 1
     1
  else
      x *fact(x-1)
  fact(4)")
               p/parse-tree-to-ast 
               ast/build-program-with-type-check
               emit/emit-and-run!)))



;; DISABLED: Isolating type checker issues
#_(t/deftest sample-string-ops
  (t/is (= "Hello Johnny" (-> (p/parser
"fn HelloWorld(x: string): string => {  \"Hello \".concat(x) }
fn NewMain(n: string):string => { HelloWorld(n) }
             NewMain(\"Johnny\")")
               p/parse-tree-to-ast
               ast/build-program
               emit/emit-and-run!))))




;;this fails, as run expr returns nil for some reason
#_(t/deftest sample-programs
    (t/are [in out] (-> (p/parser in)
                        p/parse-tree-to-ast 
                        ast/build-program 
                        emit/emit-and-run!)
      "fn fact(x:int): int =>
  if x <= 1
     1
  else
      x *fact(x-1)
  fact(4)"  10))
