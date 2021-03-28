(ns lasm.parser
  (:require [instaparse.core :as insta]))


(def parser
  (insta/parser
"
Prog := TopLevelExpr (ws expr-delim+ ws TopLevelExpr)*
<TopLevelExpr> := FunDefExpr | FunCallExpr
<Expr> := TopLevelExpr | InteropCallExpr | StringExpr | NumExpr | VarExpr | BinOpExpr | EqOpExpr | IfExpr
<expr-delim> := <';'> | newline
<newline> := <'\n'>
<ws> := <' '>*
<wc> := (ws | newline)*
<symbol> :=  #'[a-zA-Z][\\.a-zA-Z0-9]*'
<comma> := <','>
<comma-delimited-exprs> := Expr (ws comma ws Expr)*
VarExpr := symbol[TypeAnnotation]
StringExpr := <'\"'> #'[.[^\"]]*' <'\"'>
NumExpr := #'[0-9]+'
VarDefExpr := VarExpr ws <'='> ws Expr
IfExpr := <'if'> ws EqOpExpr wc  Expr wc <'else'> wc Expr wc
BinOpExpr  := Expr ws BinOp ws Expr
EqOpExpr  := Expr ws EqOp ws Expr
BinOp :=  '+' | '-' | '/' | '*'
EqOp := '>' | '<' | '>=' | '<=' | '==' | '!='
<TypeAnnotation> := ws <':'> ws TypeExpr
FunDefExpr := <'fn'>ws symbol ws<'('> ws params ws  <')'> TypeAnnotation ws <'=>'> ws body
TypeExpr := 'string' | 'int'
params := VarExpr? (ws <','> ws VarExpr)*
body := <'{'>?  wc Expr ws (expr-delim ws Expr)* wc <'}'>?
FunCallExpr :=  symbol <'('> comma-delimited-exprs? <')'>
StaticInteropCallExpr :=  <'.'>symbol <'('> symbol ws comma ws  comma-delimited-exprs? <')'>
InteropCallExpr :=  <'.'>symbol <'('> ws <'this'> ws symbol ws comma ws  comma-delimited-exprs? <')'>"))


(defn trans-type [[_ type-expr]]
  ;; TODO for now this is good enough, but this needs to get smarter
  ;; once we want to support class names and java objects
  (keyword type-expr))

(defn trans-param [[_ arg-id type-expr]]
  {:id arg-id
   :type (trans-type type-expr)})

(defmulti  trans-to-ast first)

(defmethod trans-to-ast :StringExpr [[_ str-val]] [:StringExpr  str-val])

(defmethod trans-to-ast :NumExpr [[_ num-val]] [:IntExpr (Integer/parseInt num-val)])

(defmethod trans-to-ast :BinOp [[_ op]]
  (case op
    "+" :AddInt
    "-" :SubInt
    "*" :MulInt
    "/" :DivInt))

(defmethod trans-to-ast :EqOp [[_ op]]
  (keyword op))

(defmethod trans-to-ast :BinOpExpr [[_ arg0 bin-op arg1]] (mapv trans-to-ast [bin-op arg0 arg1]))

(defmethod trans-to-ast :EqOpExpr [[_ arg0 eq-op arg1]] (mapv trans-to-ast [eq-op arg0 arg1]))

(defmethod trans-to-ast :VarExpr [[_ id]] [:VarRef id])

(defmethod trans-to-ast :IfExpr [[_ & exprs]]
  (into [:If] (mapv trans-to-ast exprs)))

(defmethod trans-to-ast :FunDefExpr [[_ fn-name [_ & params] return-type  [_ & body]]]
  (into
   [:FunDef fn-name {:args (mapv trans-param params)
                     :return-type (trans-type return-type)}]
   (mapv trans-to-ast body)))

(defmethod trans-to-ast :FunCallExpr [[_ fn-name & arg-exprs]]
  (into
   [:FunCall fn-name]
   (mapv trans-to-ast arg-exprs)))


(defmethod trans-to-ast :InteropCallExpr [[_ method-name class-name & args]]
  (into
   [:InteropCall (str class-name "/" method-name)]
   (mapv trans-to-ast args)))

(defmethod trans-to-ast :StaticInteropCallExpr [[_ method-name class-name & args]]
  (into
   [:StaticInteropCall (str class-name "/" method-name)]
   (mapv trans-to-ast args)))

(defn parse-tree-to-ast [[_ & exprs]]
  (mapv trans-to-ast exprs))



(comment
  (require '[lasm.ast :as ast]
           '[lasm.emit :as emitter])


  ;; First Fibonacci !!!
  (-> (parser "fn fib(x:int): int =>
  if x <= 2
     1
  else
      fib(x-1) + fib(x-2)")
      parse-tree-to-ast
      ast/build-program
      emitter/emit!)

  (fib/invoke 10)


  (-> (parser "fn Fib(x:int): int =>
  if x <= 2
     1
  else
      Fib(x-1) + Fib(x-2)
\"Hello\"
  println(Fib(4))")
      parse-tree-to-ast
      ast/build-program #_
      emitter/emit!)



  (fact/invoke 9)

  (->
   (parser   "fn HelloWorld(x: string): string => { .concat(this java.lang.String,  \"Hello \", x) }
fn Main():string => { HelloWorld(\"Johnny\") }
println(Main())")
   ;; leaving top level call expr for later
   parse-tree-to-ast
   ast/build-program
   ;; TODO this won't return since build-program creates entry-point with :return-type :void
   ;; figuring out how to parameterize this for different return types and command args would be useful!
   emitter/emit-and-run!)



  (->
   "fn HelloWorld(x: string): string => { .concat(this java.lang.String,  \"Hello \", x) }
fn NewMain(n: string):string => { HelloWorld(n) }"
   ;; leaving top level call expr for later
   parser
   parse-tree-to-ast
   ast/build-program #_
   ;; TODO this won't return since build-program creates entry-point with :return-type :void
   ;; figuring out how to parameterize this for different return types and command args would be useful!
   emitter/emit!)

  (NewMain/invoke "Anne !")


  (-> "fn f(x:int):int => 2*x + 2
f(40)"
   ;; leaving top level call expr for later
   parser
   parse-tree-to-ast
   ast/build-program
   ;; TODO this won't return since build-program creates entry-point with :return-type :void
   ;; figuring out how to parameterize this for different return types and command args would be useful!
   emitter/emit-and-run!)


  (-> (parser "fn f(x: int):int => 2 + f(x)
f(1)")
      parse-tree-to-ast
      ast/build-program
      emitter/emit-and-run!)


  [:FunDef
   "helloWorld"
   [:params [:VarExpr "s" [:TypeExpr "string"]]]
   [:TypeExpr "string"]
   [:body
    [:InteropCall
     "concat"
     "java.lang.String"
     [:StringExpr "Hello"]
     [:VarExpr "s"]]]]



  ;; =>
  [[:FunDef "HelloWorld"
    {:args [{:id "x" :type :string}]
     :return-type :string}
    [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
   [:FunDef "Main"
    {:args []
     :return-type :void}
    [:FunCall "HelloWorld" "Johnny"]]])
