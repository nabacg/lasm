(ns lasm.ast-test
  (:require [clojure.test :as t]
            [lasm.ast :as ast])
  (:import [org.objectweb.asm Type]))


;; AST-expr
;;  [:FunDef {:args [] :fn-name :return-type}]
;;  [:FunCall "fn-name" args...]
;;  [:InteropCall {:class-name :method-name} args]
;;  [:StaticInteropCall {:class-name :method-name :static?} args]
;;  [:VarDef :expr :expr]
;;  [:VarRef {:var-id ""}]
;;  [:If :expr :expr2 :expr3]
;;  :AddInt |  :SubInt |  :MulInt |  :DivInt
;;  :== | :!= | :> | <: | :>= | :<=
;;  [:String ""]
;;  [:Int 0]
;;  [:class ""] | :type
(t/deftest ast-to-ir-single-exprs
  (with-redefs [gensym (fn [prefix-string] (str prefix-string "AAA"))]
    (let [env (assoc (ast/init-tenv) "f"  {:args [:int], :return-type :int})]
      (t/are [in expected] (= expected (let [[env' ir-exprs] (ast/ast-to-ir in env)]
                                         ir-exprs))

        ;; VarDef
        [:VarDef {:var-id "x", :var-type :int} [:Int 42]]
        [[:int {:value 42}] [:def-local {:var-id "x", :var-type :int}]]          

        ;; VarRef
        [:VarRef {:var-id "printint"}] [[:ref-local
                                         {:var-id "printint",
                                          :var-type
                                          {:args [:int], :return-type :int, :special-form :print}}]]
        ;; StaticInteropCall        
        [:StaticInteropCall
         {:class-name "java.lang.Math", :method-name "abs"}
         [:SubInt
          [:Int 10]
          [:Int 23]]]
        [[:int {:value 10}]          
         [:int {:value 23}]
         [:sub-int]
         [:static-interop-call [:java.lang.Math/abs [:int] :int]]]
        
        ;; InteropCall
        [:FunDef
         {:args [{:id "x", :type [:class "java.lang.String"]}],
          :fn-name "HelloWorld",
          :return-type [:class "java.lang.String"]}
         [:InteropCall
          {:this-expr [:VarRef {:var-id "x"}], :method-name "concat"}
          [:String "Hello "]]]
        [{:class-name "HelloWorld",          
          :args [[:class "java.lang.String"]],
          :return-type [:class "java.lang.String"],
          :body
          [[:arg {:value 0}]
           [:def-local {:var-id "x", :var-type [:class "java.lang.String"]}]           
           [:ref-local {:var-id "x", :var-type [:class "java.lang.String"]}]
           [:string {:value "Hello "}]
           [:interop-call
            [:java.lang.String/concat
             [[:class "java.lang.String"]]
             [:class "java.lang.String"]]]]}]
        ;; TODO this fails because of referenctial equality vs value equality on ASM objects (Type, Method)
        ;; we should probably remove those from AST and only add them in emit, looks like emit/resolve-type could be used for that 
        ;; CtorInteropCall
        ;; [:CtorInteropCall
        ;;    {:class-name "javax.swing.JFrame"}
        ;;    [:String "hello world swing"]]       
        ;; [[:new {:owner (Type/getType (Class/forName  "javax.swing.JFrame"))}]          
        ;;  [:string {:value "hello world swing"}]
        ;;  [:invoke-constructor (Type/getType (Class/forName  "javax.swing.JFrame"))
        ;;   :method  (ast/create-ctor-method {:arg-types [:string]})]]
        
        ;; :FunCall
        [:FunCall
         "f"
         [:AddInt [:Int 2] [:MulInt [:Int 8] [:Int 100]]]]
        [[:int {:value 2}]
         [:int {:value 8}]
         [:int {:value 100}]
         [:mul-int]
         [:add-int]
         [:call-fn [:f/invoke [:int] :int]]]

        ;; :FunDef
        [:FunDef
         {:args [{:id "x" :type :int}]
          :fn-name "Hello"
          :return-type :int}
         [:If [:>  [:VarRef {:var-id "x"}] [:Int{:value 119}]]
          [:Int {:value  42}]
          [:Int {:value -1}]]]  [{:class-name "Hello",
                                  :args [:int],
                                  :return-type :int,
                                  :body
                                  [[:arg {:value 0}]
                                   [:def-local {:var-id "x", :var-type :int}]
                                   [:ref-local {:var-id "x", :var-type :int}]
                                   [:int {:value {:value 119}}]
                                   [:jump-cmp
                                    {:value "truthy_lbl_AAA", :compare-op :>, :compare-type :int}]
                                   [:int {:value {:value -1}}]
                                   [:jump {:value "exit_lblAAA"}]
                                   [:label {:value "truthy_lbl_AAA"}]
                                   [:int {:value {:value 42}}]
                                   [:label {:value "exit_lblAAA"}]]}]))))




(t/deftest build-program
  (with-redefs [gensym (fn [prefix-string] (str prefix-string "AAA"))] ;; redefing gensym to remove random labels for IR jumps 
    (t/are [in expected] (= expected (ast/build-program in))
      

      [[:FunDef
        {:args [{:id "x", :type :int}], :fn-name "Fib", :return-type :int}
        [:If
         [:<= [:VarRef {:var-id "x"}] [:Int 2]]
         [:Int 1]
         [:AddInt
          [:FunCall "Fib" [:SubInt [:VarRef {:var-id "x"}] [:Int 1]]]
          [:FunCall "Fib" [:SubInt [:VarRef {:var-id "x"}] [:Int 2]]]]]]
       [:FunCall "printint" [:FunCall "Fib" [:Int 4]]]]
      {:fns
       [{:class-name "Fib",
         :args [:int],
         :return-type :int,
         :body
         [[:arg {:value 0}]
          [:def-local {:var-id "x", :var-type :int}]
          [:ref-local {:var-id "x", :var-type :int}]
          [:int {:value 2}]
          [:jump-cmp
           {:value "truthy_lbl_AAA",
            :compare-op :<=,
            :compare-type :int}]
          [:ref-local {:var-id "x", :var-type :int}]
          [:int {:value 1}]
          [:sub-int]
          [:call-fn [:Fib/invoke [:int] :int]]
          [:ref-local {:var-id "x", :var-type :int}]
          [:int {:value 2}]
          [:sub-int]
          [:call-fn [:Fib/invoke [:int] :int]]
          [:add-int]
          [:jump {:value "exit_lblAAA"}]
          [:label {:value "truthy_lbl_AAA"}]
          [:int {:value 1}]
          [:label {:value "exit_lblAAA"}]]}
        {:class-name "Main_AAA",
         :args [],
         :return-type :void,
         :body
         [[:int {:value 4}]
          [:call-fn [:Fib/invoke [:int] :int]]
          [:print]
          [:pop]]}],
       :entry-point "Main_AAA"})))
