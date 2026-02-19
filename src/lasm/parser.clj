(ns lasm.parser
  (:require [clojure.string :as string]
            [instaparse.core :as insta]))


(def parser
  (insta/parser
"
Prog := wc TopLevelExpr (ws expr-delim+ ws TopLevelExpr)* wc
<TopLevelExpr> := FunDefExpr | FunCallExpr | VarDefExpr | InteropCallExpr | StaticInteropCallExpr | StaticFieldAccessExpr | CtorInteropExpr | ProxyExpr
<Expr> := TopLevelExpr | BoolExpr | StringExpr | NumExpr | VarExpr | IfExpr | BinOpExpr | EqOpExpr
BlockExpr := <'{'> wc Expr (wc expr-delim+ wc Expr)* wc <'}'>
<IfBranch> := BlockExpr | Expr
<expr-delim> := <';'> | newline
<newline> := <'\n'>
<ws> := <' '>*
<wc> := (ws | newline)*
<symbol> :=  #'^(?!true|false)[a-zA-Z][a-zA-Z0-9_]*'
<fullyQualifiedType> := #'[a-zA-Z][\\.a-zA-Z0-9]*'
<comma> := <','>
<comma-delimited-exprs> := Expr (ws comma ws Expr)*
VarExpr := symbol[TypeAnnotation]
StringExpr := <'\"'> #'[.[^\"]]*' <'\"'>
NumExpr := #'[0-9]+'
BoolExpr := 'true' | 'false'
VarDefExpr := VarExpr ws <'='> ws Expr
IfExpr := <'if'> ws EqOpExpr wc  IfBranch wc <'else'> wc IfBranch wc
BinOpExpr  := Expr ws BinOp ws Expr
EqOpExpr  := Expr ws EqOp ws Expr
BinOp :=  '+' | '-' | '/' | '*'
EqOp := '>' | '<' | '>=' | '<=' | '==' | '!='
<TypeAnnotation> := ws <':'> ws TypeExpr
FunDefExpr := <'fn'> ws symbol ws<'('> ws params ws  <')'> TypeAnnotation ws <'=>'> ws body
TypeExpr := 'bool' | 'string' | 'int' | 'void' | fullyQualifiedType
params := VarExpr? (ws <','> ws VarExpr)*
body := <'{'> wc Expr (wc expr-delim+ wc Expr)* wc <'}'> | wc Expr ws (expr-delim ws Expr)*
proxy-body := <'{'> wc Expr (wc expr-delim+ wc Expr)* wc <'}'>
FunCallExpr := symbol  <'('> comma-delimited-exprs? <')'>
StaticInteropCallExpr := fullyQualifiedType<'/'>symbol <'('> comma-delimited-exprs? <')'>
StaticFieldAccessExpr := fullyQualifiedType<'/'>symbol
InteropCallExpr := ( StringExpr | VarExpr | FunCallExpr )<'.'>symbol<'('> comma-delimited-exprs? <')'>
CtorInteropExpr := 'new' ws fullyQualifiedType<'('> comma-delimited-exprs? <')'>
ProxyExpr := <'proxy'> ws fullyQualifiedType ws <'{'> wc ProxyMethod (wc ProxyMethod)* wc <'}'>
ProxyMethod := symbol ws <'('> ws params ws <')'> TypeAnnotation ws <'=>'> ws proxy-body"))



(defn error [expr & msg-args]
  (throw (ex-info   "Invalid input "
                    {:expr expr
                     :msg (apply format msg-args)})))

(defn trans-type [[_ type-str]]
  ;; TODO for now this is good enough, but this needs to get smarter
  ;; once we want to support class names and java objects
  (cond
    (= type-str "string") [:class "java.lang.String"]
    (= type-str "bool") :bool
    (string/includes? type-str ".") [:class type-str]
    :else
    (keyword type-str)))


(defn trans-param [[_ arg-id type-expr]]
  {:id arg-id
   :type (trans-type type-expr)})

(defmulti  trans-to-ast (fn [expr]
                          (if (insta/failure? expr)
                            (error expr "invalid parse tree, probably failed parse")
                            (first expr))))

(defmethod trans-to-ast :TypeExpr [type-expr]  (trans-type type-expr))

(defmethod trans-to-ast :StringExpr [[_ str-val]] [:String str-val])

(defmethod trans-to-ast :NumExpr [[_ num-val]] [:Int (Integer/parseInt num-val)])

(defmethod trans-to-ast :BoolExpr [[_ bool-val]] [:Bool  (= bool-val "true") ])

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

(defmethod trans-to-ast :FunDefExpr [[_ fn-name params-node return-type body-node]]
  (let [[_ & params] params-node
        [_ & body] body-node]
    (into
     [:FunDef {:args (mapv trans-param params)
               :fn-name fn-name
               :return-type (trans-type return-type)}]
     (mapv trans-to-ast body))))

(defmethod trans-to-ast :FunCallExpr [[_ fn-name & arg-exprs]]
  (into
   [:FunCall fn-name]
   (mapv trans-to-ast arg-exprs)))

(defmethod trans-to-ast :CtorInteropExpr [[_ _ class-name & args]]
  (into
   [:CtorInteropCall
    {:class-name class-name}]
   (mapv trans-to-ast args)))

(defmethod trans-to-ast :InteropCallExpr [[_ this-expr method-name  & args]]
  (into
   [:InteropCall {:this-expr (trans-to-ast  this-expr)
                  :method-name method-name}]
   (mapv trans-to-ast args)))

;;(trans-to-ast (first (rest (parser "x.toString(b)"))))

(defmethod trans-to-ast :StaticInteropCallExpr [[_  class-name method-name & args]]
  (into
   [:StaticInteropCall
    {:class-name  class-name
     :method-name method-name}]
   (mapv trans-to-ast args)))

(defmethod trans-to-ast :StaticFieldAccessExpr [[_ class-name field-name]]
  [:StaticFieldAccess
   {:class-name class-name
    :field-name field-name}])

(defmethod trans-to-ast :BlockExpr [[_ & exprs]]
  (into [:Block] (mapv trans-to-ast exprs)))

(defmethod trans-to-ast :ProxyMethod [[_ method-name params-node return-type body-node]]
  (let [[_ & params] params-node
        [_ & body] body-node]
    {:method-name method-name
     :args (mapv trans-param params)
     :return-type (trans-type return-type)
     :body (mapv trans-to-ast body)}))

(defmethod trans-to-ast :ProxyExpr [[_ class-or-interface & methods]]
  [:Proxy
   {:class-or-interface class-or-interface
    :methods (mapv trans-to-ast methods)}])

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

  (fib/invoke 16)

  (map #(fib/invoke %) (range 20))


  ;; TODO start here
  ;; it breaks with
;;  Unhandled clojure.lang.ExceptionInfo
;;  augment can only be called with :expr [...]

  (-> (parser "fn Fib(x:int): int =>
       if x <= 2
          1
       else
           Fib(x-1) + Fib(x-2)
       printint(Fib(4))")
      parse-tree-to-ast  
      ast/build-program #_
      emitter/emit!)


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
      parse-tree-to-ast
      ast/build-program
      emitter/emit-and-run!)


  (-> "fn f(x:int):int => 2*x + 2
printint(java.lang.Math/abs(100 - f(2+8*100)))"
      ;; leaving top level call expr for later
      parser 
      parse-tree-to-ast 
      ast/build-program 
      emitter/emit-and-run!)

  
  (-> (parser "fn Fib(x:int): int =>
       if x <= 2
          1
       else
           Fib(x-1) + Fib(x-2)
       println(Fib(4))")
      parse-tree-to-ast 
      ast/build-program #_
      emitter/emit!)


  (-> (parser "fn fact(x:int): int =>
  if x <= 1
     1
  else
      x *fact(x-1)
  fact(4)")
      parse-tree-to-ast #_
      ast/build-program #_
      emitter/emit!)



  (fact/invoke 15)

  (->
   (parser
    "fn Welcome(x: string): string => { x.concat(\", welcome! \") }
fn Main():string => {Welcome(\"Johnny\") }
s:string = Main()
printstr(s.replace(\"H\", \"->\"))")
   ;; leaving top level call expr for later
   parse-tree-to-ast 
   ast/build-program
   emitter/emit-and-run!)


;;; THIS Still doesn't work Main().replace(.. ) breaks it
  (->
   (parser
    "fn Welcome(x: string): string => { x.concat(\", welcome! \") }
fn Main():string => {Welcome(\"Johnny\") }
printstr(Main().replace(\"H\", \"->\"))")
   ;; leaving top level call expr for later
   parse-tree-to-ast 
   ast/build-program
   emitter/emit-and-run!)


  (require '[clojure.reflect :as reflect])

  (->> (reflect/reflect java.io.PrintStream)
       :members
       (filter (fn [{:keys [name]}] (= name 'println))))

  (Class/forName "java.io.PrintStream")

  
  (->
   (parser  "fn HelloWorld(x: string): string => {  \"Hello \".concat(x) }
fn NewMain(n: string):string => { HelloWorld(n) }")
   ;; leaving top level call expr for later
   parse-tree-to-ast 
   ast/build-program 
   ;; TODO this won't return since build-program creates entry-point with :return-type :void
   ;; figuring out how to parameterize this for different return types and command args would be useful!
   emitter/emit!)

  (NewMain/invoke "Johnny !")

  (-> "fn MakeFrame(): int => {
       frame:javax.swing.JFrame = new javax.swing.JFrame(\"hello\")
       label:javax.swing.JLabel = new javax.swing.JLabel(\"Hello World\")
       container:java.awt.Container = frame.getContentPane()
       container.add(label)
       frame.pack()
       frame.setVisible(true)
       42
     }"
      
      parser 
      parse-tree-to-ast 
      ast/build-program 
      emitter/emit!)


  (javax.swing.SwingUtilities/invokeLater (fn [] (MakeFrame/invoke)))



  (-> "frame:javax.swing.JFrame = new javax.swing.JFrame(\"BOOM!!!\")
       label:javax.swing.JLabel = new javax.swing.JLabel(\"Hello World from lasm\")
       container:java.awt.Container = frame.getContentPane()
       container.add(label)
       d:java.awt.Dimension = new java.awt.Dimension(400, 300)
       frame.setPreferredSize(d)       
       frame.pack()       
       frame.setVisible(true)
       frame.toFront()
       frame.repaint()"      
      parser 
      parse-tree-to-ast 
      ast/build-program 
      emitter/emit-and-run!)



  ;; STACK OVERFLOW, unbounded recursion here
  (-> (parser "fn f(x: int):int => 2 + f(x)
f(1)")
      parse-tree-to-ast
      ast/build-program
      emitter/emit-and-run!))

;;4659 4228 5414 0508
