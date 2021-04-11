(ns lasm.parser
  (:require [instaparse.core :as insta]))


(def parser
  (insta/parser
"
Prog := TopLevelExpr (ws expr-delim+ ws TopLevelExpr)*
<TopLevelExpr> := FunDefExpr | FunCallExpr | VarDefExpr
<Expr> := TopLevelExpr | InteropCallExpr | StaticInteropCallExpr | StringExpr | NumExpr | VarExpr | BinOpExpr | EqOpExpr | IfExpr
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

(defmethod trans-to-ast :TypeExpr [type-expr] (trans-type type-expr))

(defmethod trans-to-ast :StringExpr [[_ str-val]] [:String str-val])

(defmethod trans-to-ast :NumExpr [[_ num-val]] [:Int (Integer/parseInt num-val)])

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

(defmethod trans-to-ast :VarExpr [[_ id type-expr]]
  (if type-expr
    [:VarRef  {:var-id id
               :var-type (trans-to-ast type-expr)}]
    [:VarRef {:var-id id}]))

(defmethod trans-to-ast :VarDefExpr [[_ var-expr val-expr]]
  (let [[_ props] (trans-to-ast var-expr)]
    [:VarDef props (trans-to-ast val-expr)]))

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
   [:InteropCall {:class-name  class-name
                  :method-name method-name}]
   (mapv trans-to-ast args)))

(defmethod trans-to-ast :StaticInteropCallExpr [[_ method-name class-name & args]]
  (into
   [:InteropCall {:class-name  class-name
                  :method-name method-name
                  :static? true}]
   (mapv trans-to-ast args)))

(defn parse-tree-to-ast [[_ & exprs]]
  (mapv trans-to-ast exprs))


(comment
  (require '[lasm.ast :as ast]
           '[lasm.emit :as emitter])


  ;; First Fibonacci !!!
  (-> (parser "fn fib(x:int): int =>
  if x < 2
     1
  else
      fib(x-1) + fib(x-2)")
      parse-tree-to-ast
      ast/build-program
      emitter/emit!)

  (fib/invoke 15)

  (map #(fib/invoke %) (range 10))


  (-> (parser "fn Fib(x:int): int =>
  if x <= 2
     1
  else
      Fib(x-1) + Fib(x-2)
  s:string = \"Hello\"

  r:int = Fib(15)
  printstr(s)
  printstr(\"Result of Fib(15) is: \")
  printint(r)")
      parse-tree-to-ast #_
      ast/build-program #_
      emitter/emit-and-run!)

  (-> "fn f(x:int):int => 2*x + 2
printint(.abs(java.lang.Math, 100 - f(2+8*100)))"
      ;; leaving top level call expr for later
      parser
      parse-tree-to-ast
      ast/build-program
      emitter/emit-and-run!)



  (-> (parser "fn fact(x:int): int =>
  if x <= 1
     1
  else
      x *fact(x-1)
  fact(4)")
      parse-tree-to-ast #_#_
      ast/build-program
      emitter/emit!)



  (fact/invoke 15)

  (->
   (parser   "fn HelloWorld(x: string): string => { .concat(this java.lang.String,  \"Hello \", x) }
fn Main():string => { HelloWorld(\"Johnny\") }
printstr(.replace(this java.lang.String, Main(), \"H\", \"->\"))")
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
   ast/build-program
   ;; TODO this won't return since build-program creates entry-point with :return-type :void
   ;; figuring out how to parameterize this for different return types and command args would be useful!
   emitter/emit!)

  (NewMain/invoke "Anne !")





  ;; STACK OVERFLOW, unbounded recursion here
  (-> (parser "fn f(x: int):int => 2 + f(x)
f(1)")
      parse-tree-to-ast
      ast/build-program
      emitter/emit-and-run!)


)
