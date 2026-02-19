(ns lasm.parser-test
  (:require [clojure.test :as t]
            [lasm.parser :as p]
            [instaparse.core :as insta]))



(t/deftest top-level-expression-parser-tests
  (t/are [in expected] (= expected (p/parser in))
    "f(x)"     [:Prog [:FunCallExpr "f" [:VarExpr "x"]]]
    "x.toString(b+1)" [:Prog
                       [:InteropCallExpr
                        [:VarExpr "x"]
                        "toString"
                        [:BinOpExpr [:VarExpr "b"] [:BinOp "+"] [:NumExpr "1"]]]]

     "x:int = x + 23 +g(55-1)" [:Prog
                                          [:VarDefExpr
                                           [:VarExpr "x" [:TypeExpr "int"]]
                                           [:BinOpExpr
                                            [:VarExpr "x"]
                                            [:BinOp "+"]
                                            [:BinOpExpr
                                             [:NumExpr "23"]
                                             [:BinOp "+"]
                                             [:FunCallExpr
                                              "g"
                                              [:BinOpExpr [:NumExpr "55"] [:BinOp "-"] [:NumExpr "1"]]]]]]]
    "x:int = 42"  [:Prog [:VarDefExpr [:VarExpr "x" [:TypeExpr "int"]] [:NumExpr "42"]]]
    "fn f(x:int):int => x + 1" [:Prog
                                [:FunDefExpr
                                 "f"
                                 [:params [:VarExpr "x" [:TypeExpr "int"]]]
                                 [:TypeExpr "int"]
                                 [:body [:BinOpExpr [:VarExpr "x"] [:BinOp "+"] [:NumExpr "1"]]]]]

    "x:java.swing.JFrame = new java.swing.JFrame(\"hello world swing\")"
    [:Prog          
     [:VarDefExpr
      [:VarExpr "x" [:TypeExpr "java.swing.JFrame"]]
      [:CtorInteropExpr
       "new"
       "java.swing.JFrame"
       [:StringExpr "hello world swing"]]]]
    
    
    "fn HelloWorld(x: string): string => { x.concat(\"Hello \", x) }
fn Main():string => { HelloWorld(\"Johnny\") }
printstr(Main().replace(\"H\", \"->\"))"
    [:Prog
     [:FunDefExpr
      "HelloWorld"
      [:params [:VarExpr "x" [:TypeExpr "string"]]]
      [:TypeExpr "string"]
      [:body
       [:InteropCallExpr
        [:VarExpr "x"]
        "concat"
        [:StringExpr "Hello "]
        [:VarExpr "x"]]]]
     [:FunDefExpr
      "Main"
      [:params]
      [:TypeExpr "string"]
      [:body [:FunCallExpr "HelloWorld" [:StringExpr "Johnny"]]]]
     [:FunCallExpr
      "printstr"
      [:InteropCallExpr
       [:FunCallExpr "Main"]
       "replace"
       [:StringExpr "H"]
       [:StringExpr "->"]]]]))


(t/deftest ast-generation-tests
  (t/are [in expected] (= expected (p/parse-tree-to-ast
                                    (p/parser in)))

    "x:int = 42" [[:VarDef {:var-id "x", :var-type :int} [:Int 42]]]
    ;; Recursive function definition    
    "fn fib(x:int): int =>
  if x < 2
     1
  else
      fib(x-1) + fib(x-2)"
    [[:FunDef
      {:args [{:id "x", :type :int}], :fn-name "fib", :return-type :int}
      [:If
       [:< [:VarRef {:var-id "x"}] [:Int 2]]
       [:Int 1]
       [:AddInt
        [:FunCall "fib" [:SubInt [:VarRef {:var-id "x"}] [:Int 1]]]
        [:FunCall "fib" [:SubInt [:VarRef {:var-id "x"}] [:Int 2]]]]]]]

    ;; function, variable and built-int printing 
    "fn Fib(x:int): int =>
  if x <= 2
     1
  else
      Fib(x-1) + Fib(x-2)
  s:string = \"Hello\"

  r:int = Fib(15)
  printstr(s)
  printstr(\"Result of Fib(15) is: \")
  printint(r)"
    [[:FunDef
      {:args [{:id "x", :type :int}], :fn-name "Fib", :return-type :int}
      [:If
       [:<= [:VarRef {:var-id "x"}] [:Int 2]]
       [:Int 1]
       [:AddInt
        [:FunCall "Fib" [:SubInt [:VarRef {:var-id "x"}] [:Int 1]]]
        [:FunCall "Fib" [:SubInt [:VarRef {:var-id "x"}] [:Int 2]]]]]]
     [:VarDef
      {:var-id "s", :var-type [:class "java.lang.String"]}
      [:String "Hello"]]
     [:VarDef {:var-id "r", :var-type :int} [:FunCall "Fib" [:Int 15]]]
     [:FunCall "printstr" [:VarRef {:var-id "s"}]]
     [:FunCall "printstr" [:String "Result of Fib(15) is: "]]
     [:FunCall "printint" [:VarRef {:var-id "r"}]]]

    ;; StaticInterop mixed with function calls and built-in printing   
    "fn f(x:int):int => 2*x + 2
printint(java.lang.Math/abs(100 - f(2+8*100)))"
    [[:FunDef
      {:args [{:id "x", :type :int}], :fn-name "f", :return-type :int}
      [:MulInt [:Int 2] [:AddInt [:VarRef {:var-id "x"}] [:Int 2]]]]
     [:FunCall
      "printint"
      [:StaticInteropCall
       {:class-name "java.lang.Math", :method-name "abs"}
       [:SubInt
        [:Int 100]
        [:FunCall
         "f"
         [:AddInt [:Int 2] [:MulInt [:Int 8] [:Int 100]]]]]]]]

    ;; Non-static interop (instance method call) in multiple fns calls       
    "fn HelloWorld(x: string): string => { \"Hello \".concat(x) }
fn Main():string => { HelloWorld(\"Johnny\") }
printstr(Main().replace(\"H\", \"->\"))"
    [[:FunDef
      {:args [{:id "x", :type [:class "java.lang.String"]}],
       :fn-name "HelloWorld",
       :return-type [:class "java.lang.String"]}
      [:InteropCall
       {:this-expr [:String "Hello "] :method-name "concat"}
       [:VarRef {:var-id "x"}]]]
     [:FunDef
      {:args [],
       :fn-name "Main",
       :return-type [:class "java.lang.String"]}
      [:FunCall "HelloWorld" [:String "Johnny"]]]
     [:FunCall
      "printstr"
      [:InteropCall
       {:this-expr [:FunCall "Main"], :method-name "replace"}
       [:String "H"]
       [:String "->"]]]]

    "new java.swing.JFrame(\"hello world swing\")"    
    [[:CtorInteropCall
      {:class-name "java.swing.JFrame"}
      [:String "hello world swing"]]]

    "fn Welcome(x: string): string => { x.concat(\" Welcome \") }
fn Main():string => {Welcome(\"Johnny\") }
printstr(Main().replace(\"H\", \"->\"))"
    [[:FunDef
      {:args [{:id "x", :type [:class "java.lang.String"]}],
       :fn-name "Welcome",
       :return-type [:class "java.lang.String"]}
      [:InteropCall
       {:this-expr [:VarRef {:var-id "x"}], :method-name "concat"}
       [:String " Welcome "]]]
     [:FunDef
      {:args [],
       :fn-name "Main",
       :return-type [:class "java.lang.String"]}
      [:FunCall "Welcome" [:String "Johnny"]]]
     [:FunCall
      "printstr"
      [:InteropCall
       {:this-expr [:FunCall "Main"], :method-name "replace"}
       [:String "H"]
       [:String "->"]]]])


(t/deftest multi-method-proxy-parsing
  (t/testing "Single-method proxy parses"
    (let [code "fn test(): int => {
  al:java.awt.event.ActionListener = proxy java.awt.event.ActionListener {
    actionPerformed(e:java.awt.event.ActionEvent): void => { printstr(\"click\") }
  }
  42
}
test()"]
      (t/is (not (insta/failure? (p/parser code))))))

  (t/testing "Three-method proxy (KeyListener) parses"
    (let [code "fn test(): int => {
  kl:java.awt.event.KeyListener = proxy java.awt.event.KeyListener {
    keyPressed(e:java.awt.event.KeyEvent): void => { printstr(\"pressed\") }
    keyReleased(e:java.awt.event.KeyEvent): void => { printstr(\"released\") }
    keyTyped(e:java.awt.event.KeyEvent): void => { printstr(\"typed\") }
  }
  42
}
test()"]
      (t/is (not (insta/failure? (p/parser code))))))

  (t/testing "Five-method proxy (MouseListener) parses"
    (let [code "fn test(): int => {
  ml:java.awt.event.MouseListener = proxy java.awt.event.MouseListener {
    mouseClicked(e:java.awt.event.MouseEvent): void => { printstr(\"c\") }
    mousePressed(e:java.awt.event.MouseEvent): void => { printstr(\"p\") }
    mouseReleased(e:java.awt.event.MouseEvent): void => { printstr(\"r\") }
    mouseEntered(e:java.awt.event.MouseEvent): void => { printstr(\"en\") }
    mouseExited(e:java.awt.event.MouseEvent): void => { printstr(\"ex\") }
  }
  42
}
test()"]
      (t/is (not (insta/failure? (p/parser code))))))

  (t/testing "Multiple proxies in same function parse"
    (let [code "fn test(): int => {
  al:java.awt.event.ActionListener = proxy java.awt.event.ActionListener {
    actionPerformed(e:java.awt.event.ActionEvent): void => { printstr(\"action\") }
  }
  kl:java.awt.event.KeyListener = proxy java.awt.event.KeyListener {
    keyPressed(e:java.awt.event.KeyEvent): void => { printstr(\"p\") }
    keyReleased(e:java.awt.event.KeyEvent): void => { printstr(\"r\") }
    keyTyped(e:java.awt.event.KeyEvent): void => { printstr(\"t\") }
  }
  42
}
test()"]
      (t/is (not (insta/failure? (p/parser code))))))

  (t/testing "Proxy AST has correct structure"
    (let [code "fn test(): int => {
  kl:java.awt.event.KeyListener = proxy java.awt.event.KeyListener {
    keyPressed(e:java.awt.event.KeyEvent): void => { printstr(\"pressed\") }
    keyReleased(e:java.awt.event.KeyEvent): void => { printstr(\"released\") }
  }
  42
}
test()"
          ast (p/parse-tree-to-ast (p/parser code))
          fundef (first ast)
          vardef (nth fundef 2)
          proxy-node (nth vardef 2)
          methods (get-in proxy-node [1 :methods])]
      (t/is (= 2 (count methods)))
      (t/is (= "keyPressed" (:method-name (first methods))))
      (t/is (= "keyReleased" (:method-name (second methods)))))))

  (comment
    ;; expected error

    (instaparse.failure/pprint-failure   (p/parser "getF(12)(x)"))


    (p/parser "getF(12)(x)")
    {:index 8,
     :reason
     [{:tag :string, :expecting "*"}
      {:tag :string, :expecting "/"}
      {:tag :string, :expecting "-"}
      {:tag :string, :expecting "+"}
      {:tag :string, :expecting " "}
      {:tag :string, :expecting "!="}
      {:tag :string, :expecting "=="}
      {:tag :string, :expecting "<="}
      {:tag :string, :expecting ">="}
      {:tag :string, :expecting "<"}
      {:tag :string, :expecting ">"}
      {:tag :string, :expecting "."}
      {:tag :string, :expecting "\n"}
      {:tag :string, :expecting ";"}],
     :line 1,
     :column 9,
     :text "getF(12)(x)"}

    ))
