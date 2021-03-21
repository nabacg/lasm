(ns lasm.parser
  (:require [instaparse.core :as insta]))


(def parser
  (insta/parser
"
Prog := TopLevelExpr (ws expr-delim+ ws TopLevelExpr)*
<TopLevelExpr> := FunDef | FunCall
<Expr> := TopLevelExpr | StringExpr | NumExpr | VarExpr
<expr-delim> := <';'> | newline
<newline> := <'\n'>
<ws> := <' '>*
<wc> := (ws | newline)*
<symbol> :=  #'[a-zA-Z][a-zA-Z0-9]*'
<comma-delimited-exprs> := Expr (ws <','> ws Expr)*
VarExpr := symbol[TypeAnnotation]
StringExpr := <'\"'> #'[.[^\"\"].]*' <'\"'>
NumExpr := #'[0-9]+'
<TypeAnnotation> := ws <':'> ws TypeExpr
FunDef := <'fn'>ws symbol ws<'('> ws params? ws  <')'> TypeAnnotation ws <'=>'> ws body
TypeExpr := 'string' | 'int'
params := VarExpr (ws <','> ws VarExpr)*
body := <'{'>?  wc Expr ws (expr-delim ws Expr)* wc <'}'>?
FunCall :=  symbol <'('> comma-delimited-exprs? <')'>"))

;; ^java.lang.String "Hello ".concat(x)
;;java.lang.String->concat(\"Hello\", x)
(parser
"fn h(x: string): string =>  { \"AAAA\" }
fn helloWorld (x: string): string => { \"Hello\" }
fn main ():string => { helloWorld(\"Johnny\")}
main()")




"fn HelloWorld(x: string): string => { \"Hello\" }
fn Main():string => {HelloWorld(\"Johnny\")}
 Main()"

=>
[[:FunDef "HelloWorld"
  {:args [{:id "x" :type :string}]
   :return-type :string}
  [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
 [:FunDef "Main"
  {:args []
   :return-type :void}
  [:FunCall "HelloWorld" "Johnny"]]]
