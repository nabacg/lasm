(ns lasm.parser
  (:require [instaparse.core :as insta]))


(def parser
  (insta/parser
"
Prog := TopLevelExpr (ws expr-delim+ ws TopLevelExpr)*
<TopLevelExpr> := FunDefExpr | FunCallExpr
<Expr> := TopLevelExpr | InteropCallExpr | StringExpr | NumExpr | VarExpr
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
<TypeAnnotation> := ws <':'> ws TypeExpr
FunDefExpr := <'fn'>ws symbol ws<'('> ws params ws  <')'> TypeAnnotation ws <'=>'> ws body
TypeExpr := 'string' | 'int'
params := VarExpr? (ws <','> ws VarExpr)*
body := <'{'>?  wc Expr ws (expr-delim ws Expr)* wc <'}'>?
FunCallExpr :=  symbol <'('> comma-delimited-exprs? <')'>
InteropCallExpr :=  <'.'>symbol <'('> symbol ws comma ws  comma-delimited-exprs? <')'>"))

;; ^java.lang.String "Hello ".concat(x)
;;java.lang.String->concat(\"Hello\", x)

(parser
"fn h(x: string): string =>  { \"AAAA\" }
fn helloWorld (s: string): string => { .concat(java.lang.String,  \"Hello\", s) }
fn main ():string => { helloWorld(\"Johnny\")}
main()")


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

(defn trans-type [[_ type-expr]]
  ;; TODO for now this is good enough, but this needs to get smarter
  ;; once we want to support class names and java objects
  (keyword type-expr))

(defn trans-param [[_ arg-id type-expr]]
  {:id arg-id
   :type (trans-type type-expr)})

(defmulti  trans-to-ast first)

(defmethod trans-to-ast :StringExpr [[_ str-val]] str-val)

(defmethod trans-to-ast :NumExpr [[_ num-val]] num-val )

(defmethod trans-to-ast :VarExpr [[_ id]] [:VarRef id])

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

(defn parse-tree-to-ast [[_ & exprs]]
  (mapv trans-to-ast exprs))


(comment
  (require '[lasm.ast :as ast]
           '[lasm.emit :as emitter])



  (->
   "fn HelloWorld(x: string): string => { .concat(java.lang.String,  \"Hello \", x) }
fn Main():string => { HelloWorld(\"Johnny\") }
Main()"
   ;; leaving top level call expr for later
   parser
   parse-tree-to-ast
   ast/build-program
   ;; TODO this won't return since build-program creates entry-point with :return-type :void
   ;; figuring out how to parameterize this for different return types and command args would be useful!
   emitter/emit-and-run!)



  (->
   "fn HelloWorld(x: string): string => { .concat(java.lang.String,  \"Hello \", x) }
fn NewMain(n: string):string => { HelloWorld(n) }"
   ;; leaving top level call expr for later
   parser
   parse-tree-to-ast
   ast/build-program
   ;; TODO this won't return since build-program creates entry-point with :return-type :void
   ;; figuring out how to parameterize this for different return types and command args would be useful!
   emitter/emit!)

  (NewMain/invoke "Stefan")





                                        ;=>
  [[:FunDef "HelloWorld"
    {:args [{:id "x" :type :string}]
     :return-type :string}
    [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
   [:FunDef "Main"
    {:args []
     :return-type :void}
    [:FunCall "HelloWorld" "Johnny"]]])
