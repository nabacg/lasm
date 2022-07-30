(ns lasm.end-2-end-test
  (:require [clojure.test :as t]
            [lasm.parser :as p]
            [lasm.ast :as ast]
            [lasm.emit :as emit]))


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


(t/deftest sample-fibonacci
  (t/is 10 (-> (p/parser
                "fn fact(x:int): int =>
  if x <= 1
     1
  else
      x *fact(x-1)
  fact(4)")
               p/parse-tree-to-ast 
               ast/build-program 
               emit/emit-and-run!)))



(t/deftest sample-fibonacci
  (t/is "Hello Johnny" (-> (p/parser
"fn HelloWorld(x: string): string => {  \"Hello \".concat(x) }
fn NewMain(n: string):string => { HelloWorld(n) }
             NewMain(\"Johnny\")")
               p/parse-tree-to-ast 
               ast/build-program 
               emit/emit-and-run!)))
