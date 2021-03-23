(ns lasm.ast
  (:require [clojure.string :as string]))


[:Prog
 [:VarDefExpr
  [:VarExpr "f"]
  [:LambdaExpr
   [:params [:VarExpr "a" [:TypeExpr "Int"]]]
   [:TypeExpr "Int"]
   [:body [:BinOpExpr [:VarExpr "x"] [:BinOp "+"] [:VarExpr "a"]]]]]]


[[:FunDef "HelloWorld"
  {:args [{:sym "x" :type :string}]
   :return-type :string}
  [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
 [:FunDef "Main"
  {:args [] :return-type :void}
  [:FunCall "HelloWorld" "Johnny"]]
 [:FunCall "Main" ]]

(defn error [expr & [msg ]]
  (throw (ex-info (str  "Invalid-expression: " msg)
                  {:expr expr
                   :msg msg})))

(defn errorf [msg & args]
  (throw (ex-info (apply format msg args)
                  {:msg msg})))


(defmulti ast-to-ir (fn [expr _]
                      (cond
                        (vector? expr)  (first expr)
                        :else (type expr))))

(defn args-to-locals [args]
  (vec (mapcat identity
               (map-indexed (fn [i {:keys [id type]}]
                              [[:arg {:value i}]
                               [:def-local {:var-id id :var-type type}]])
                            args))))

(defn map-ast-to-ir [env exprs]
  (reduce (fn [[env irs] expr]
            (let [[env' ir] (ast-to-ir expr env)]
              [env' (into irs ir)]))
          [env []] exprs))

(defn ops-to-ir [ops tenv]
  (vec (mapcat #(second (ast-to-ir % tenv))
               ops)))

(defmethod ast-to-ir :AddInt [[_ & ops] tenv]
  [tenv
   (conj (ops-to-ir ops tenv)
         [:add-int])])

(defmethod ast-to-ir :SubInt [[_ & ops] tenv]
  [tenv
   (conj (ops-to-ir ops tenv)
         [:sub-int])])

(defmethod ast-to-ir :MulInt [[_ & ops] tenv]
  [tenv
   (conj (vec (mapcat #(second (ast-to-ir % tenv))
                      ops))
         [:mul-int])])

(defmethod ast-to-ir :DivInt [[_ & ops] tenv]
  [tenv
   (conj (mapv #(first (second (ast-to-ir % tenv)))
               ops)
         [:div-int])])

(defmethod ast-to-ir java.lang.String [str-val env]
  [env [[:string {:value str-val}]]])

(defmethod ast-to-ir java.lang.Long [int-val env]
  [env [[:int {:value int-val}]]])

(defmethod ast-to-ir java.lang.Integer [int-val env]
  [env [[:int {:value int-val}]]])

(defmethod ast-to-ir :VarRef [[_ id] env]
  (if-let [{:keys [var-type]} (env id)]
    [env [[:ref-local {:var-id id}]]]
    (errorf "Unknown variable found %s" id)))

(defn resolve-cmp-type [arg0 arg1 tenv] ;;TODO Implement me
  :int)

(defmethod ast-to-ir :If [[_ pred truthy-expr falsy-expr] tenv]
  (let [[_ truthy-ir] (ast-to-ir truthy-expr tenv)
        [_ falsy-ir]  (ast-to-ir falsy-expr tenv)
        [cmp-op arg0 arg1] pred
        cmp-type (resolve-cmp-type truthy-expr falsy-expr tenv)
        [_ arg0] (ast-to-ir arg0 tenv)
        [_ arg1] (ast-to-ir arg1 tenv)
        truthy-lbl (name (gensym "truthy_lbl_"))
        exit-lbl   (name (gensym "exit_lbl"))]
    [tenv
     (vec
      (concat
       arg0
       arg1
       [[:jump-cmp {:value truthy-lbl :compare-op cmp-op :compare-type cmp-type}]]
       falsy-ir
       [[:jump {:value exit-lbl}]]
       [[:label {:value truthy-lbl}]]
       truthy-ir
       [[:label {:value exit-lbl}]]
       ))]))


(ast-to-ir
 [:FunDef "Hello"
  {:args [{:id "x" :type :int}]
   :return-type :int}
  [:If [:>  [:VarRef "x"] 119] 42 -1]]
 {})

(emit! {:fns [{:args [:int],
               :return-type :int,
               :body
               [[:arg {:value 0}]
                [:int {:value 119}]
                [:jump-cmp {:value "truthy" :compare-op :> :compare-type :int}]
                [:int {:value -1}]
                [:jump {:value "exit"}]
                [:label {:value "truthy"}]
                [:int {:value 42}]
                [:label {:value "exit"}]],
               :class-name "Cond12"}]
        :entry-point "Cond12"})

(defmethod ast-to-ir :InteropCall [[_ method-name & method-args] tenv]
  (if-let [fn-type  (tenv method-name)]
    (let [{:keys [args return-type]} fn-type
          [_ args-ir] (map-ast-to-ir tenv method-args)]
      [tenv
       (if (empty? args-ir)
         [[:interop-call [(keyword method-name) args return-type]]]
         (conj args-ir
               [:interop-call [(keyword method-name) args return-type]]))])
    (errorf "Unknown method signature for method: %s" method-name)))

(defmethod ast-to-ir :FunCall [[_ fn-name & fn-args] tenv]
  (if-let [fn-type  (tenv fn-name)]
    (let [{:keys [args return-type]} fn-type
          [_ args-ir] (map-ast-to-ir tenv fn-args)]
      [tenv
       (if (empty? args-ir)
         [[:call-fn [(keyword fn-name "invoke") args return-type]]]
         (conj args-ir
               [:call-fn [(keyword fn-name "invoke") args return-type]]))])
    (errorf "Unknown function signature for fn-name: %s" fn-name fn-args)))


(defmethod ast-to-ir :FunDef [[_ fn-name {:keys [args return-type]} & body] tenv]
  (let [arg-types (mapv :type args)
        tenv'     (assoc tenv fn-name {:args arg-types :return-type return-type})
        tenv-with-params (into tenv' (map (juxt :id :type) args))
        [tenv'' body-irs] (map-ast-to-ir tenv-with-params body)]
    [tenv'
     [{:class-name fn-name
       :args arg-types
       :return-type return-type
       :body (into (args-to-locals args)
                   body-irs)}]]))


(defn init-tenv []
  {"java.lang.String/concat" {:args [:string]
                              :return-type :string}})

(defn build-program [exprs]
  (let [{:keys [FunDef] :as expr-by-type} (group-by first exprs)
        top-level-exprs    (mapcat identity (vals (dissoc expr-by-type :FunDef))) ;; take everything but :FunDef
        [tenv fn-defs-ir]  (map-ast-to-ir (init-tenv) FunDef)
        ;; TODO does this really need to be that complicated? we could probably return a {FnName -> Fn} map from above fn
        ;fn-defs-ir         (apply merge fn-defs-ir)
        main-fn-name       (name (gensym "Main_"))
        [_ [main-fn-ir]]     (map-ast-to-ir
                            tenv
                            [(into
                              [:FunDef main-fn-name
                               {:args []
                                :return-type :int}]
                              top-level-exprs)])]
    ;;TODO check if all top level IR expr are maps of {FnNamestr Fn-expr}
    {:fns (conj (vec fn-defs-ir) main-fn-ir)
     :entry-point main-fn-name}))



(comment
  (ast-to-ir [:FunCall "Main"]
             {"Main" {:args [{:id "x" :type :string}]
                      :return-type :string}})

  (ast-to-ir [:DivInt 23 1]
             {})




  (Cond12/invoke 120)


  (ast-to-ir [:FunDef "Main"  {:args [{:id "x" :type :string}]
                               :return-type :string}
              [:FunCall "Other"]
              [:FunCall "Main" "Arg1"]]
             {"Other" {:args []
                       :return-type :string}})


  (ast-to-ir [:FunDef "HelloWorld"
              {:args [{:id "x" :type :string}]
               :return-type :string}
              [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
             { "java.lang.String/concat"
              {:args [:string]
               :return-type :string}})

  (map-ast-to-ir (init-tenv)
                  [[:FunDef "HelloWorld"
                    {:args [{:id "x" :type :string}]
                     :return-type :string}
                    [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
                   [:FunDef "Main"
                    {:args []
                     :return-type :void}
                    [:FunCall "HelloWorld" "Johnny"]]]
                  )
  (ast-to-ir [:FunDef "f" {:args [], :return-type :int} [:AddInt 2 [:MulInt 2 4]]] {})

  (build-program [[:FunDef "f" {:args [], :return-type :int} [:AddInt 2 2]]
                  [:FunCall "f"]])

  (build-program
   [[:FunDef "HelloWorld"
     {:args [{:id "x" :type :string}]
      :return-type :string}
     [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
    [:FunDef "Main"
     {:args []
      :return-type :void}
     [:FunCall "HelloWorld" "Johnny"]]
    [:FunCall "Main"]]))




(comment
  (require '[lasm.emit :as emit])


  (-> (build-program
       [[:FunDef "Hello"
         {:args [{:id "x" :type :string}]
          :return-type :string}
         [:InteropCall "java.lang.String/concat" "Hello " [:VarRef "x"]]]
        [:FunDef "Main112"
         {:args []
          :return-type :string}
         [:FunCall "Hello" "Johnny"]]]
       "Main112")
      emit/emit-and-run!)




  (emit/make-fn   {:class-name "TestConcat"
                   :args [:string],
                   :return-type :string,
                   :body
                   [[:arg {:value 0}]
                    [:def-local {:var-id "x", :var-type :string}]
                    [:string {:value "Hello "}]
                    [:ref-local {:var-id "x"}]
                    [:interop-call [:java.lang.String/concat [:string] :string]]]},)


  (TestConcat/invoke "Johnny Von Neuman")

  (emit/make-fn {:class-name "MA1"
                 :args [],
                 :return-type :void,
                 :body
                 [[:string {:value "Johnny"}]
                  [:call-fn
                   [:Hello/invoke [:string] :string]]
                  [:print {:args [:string]}]]})

  (MA1/invoke))
