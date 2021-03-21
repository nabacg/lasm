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
                        :else (type "sa"))))

(defn args-to-locals [args]
  (vec (mapcat identity
               (map-indexed (fn [i {:keys [id type]}]
                              [[:arg {:value i}]
                               [:def-local {:var-id id :var-type type}]])
                            args))))

(defn do-asts-in-env [env exprs]
  (reduce (fn [[env irs] expr]
            (let [[env' ir] (ast-to-ir expr env)]
              [env' (into irs ir)]))
          [env []] exprs))

(defmethod ast-to-ir java.lang.String [str-val env]
  [env [[:string {:value str-val}]]])

(defmethod ast-to-ir java.lang.Long [int-val env]
  [env [[:int {:value int-val}]]])

(defmethod ast-to-ir :VarRef [[_ id] env]
  (if-let [{:keys [var-type]} (env id)]
    [env [[:ref-local {:var-id id}]]]
    (errorf "Unknown variable found %s" id)))

(defmethod ast-to-ir :InteropCall [[_ method-name & method-args] tenv]
  (if-let [fn-type  (tenv method-name)]
    (let [{:keys [args return-type]} fn-type
          [_ args-ir] (do-asts-in-env tenv method-args)]
      [tenv
       (if (empty? args-ir)
         [[:interop-call [(keyword method-name) args return-type]]]
         (conj args-ir
               [:interop-call [(keyword method-name) args return-type]]))])
    (errorf "Unknown method signature for method: %s" method-name)))

(defmethod ast-to-ir :FunCall [[_ fn-name & fn-args] tenv]
  (if-let [fn-type  (tenv fn-name)]
    (let [{:keys [args return-type]} fn-type
          [_ args-ir] (do-asts-in-env tenv fn-args)]
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
        [tenv'' body-irs] (do-asts-in-env tenv-with-params body)]
    [tenv''
     [{fn-name {:args arg-types
                :return-type return-type
                :body (into (args-to-locals args)
                            body-irs)}}]]))


(defn init-tenv []
  {"java.lang.String/concat" {:args [:string]
                              :return-type :string}})

(defn build-program [asts entry-point]
  (let [[_ ir] (do-asts-in-env (init-tenv) asts)]
    ;;TODO check if all top level IR expr are maps of {FnNamestr Fn-expr}
    {:fns (apply merge ir)
     :entry-point entry-point}))


(ast-to-ir [:FunCall "Main"]
           {"Main" {:args [:string]
                    :return-type :string}})


(ast-to-ir [:FunDef "Main"  {:args [:string]
                             :return-type :string}
            [:FunCall "Other"]
            [:FunCall "Main" "Arg1"]] {"Other" {:args []
                                                :return-type :string}})


(ast-to-ir [:FunDef "HelloWorld"
            {:args [{:id "x" :type :string}]
             :return-type :string}
            [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
           { "java.lang.String/concat" {:args [:string]
                                        :return-type :string}})

(do-asts-in-env (init-tenv)
                [[:FunDef "HelloWorld"
                  {:args [{:id "x" :type :string}]
                   :return-type :string}
                  [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
                 [:FunDef "Main"
                  {:args []
                   :return-type :void}
                  [:FunCall "HelloWorld" "Johnny"]]]
                )

(build-program
 [[:FunDef "HelloWorld"
   {:args [{:id "x" :type :string}]
    :return-type :string}
   [:InteropCall "java.lang.String/concat" [:VarRef "x"]]]
  [:FunDef "Main"
   {:args []
    :return-type :void}
   [:FunCall "HelloWorld" "Johnny"]]]
 "Main")


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
      emit/build-and-run)




  (-> {:fns
       {"Hello"
        {:args [:string],
         :return-type :string,
         :body
         [[:arg {:value 0}]
          [:def-local {:var-id "x", :var-type :string}]
          [:string {:value "Hello "}]
          [:ref-local {:var-id "x"}]
          [:interop-call [:java.lang.String/concat [:string] :string]]]}
        "Main"
        {:args [],
         :return-type :void,
         :body
         [[:string {:value "Johnny"}]
          [:call-fn
           [:Hello/invoke [:string] :string]]]}
        :entry-point "Main"}}
      emit/build-and-run)

  (emit/make-fn   {:class-name "TestConcat"
                   :args [:string],
                   :return-type :string,
                   :body
                   [[:arg {:value 0}]
                    [:def-local {:var-id "x", :var-type :string}]
                    [:string {:value "Hello "}]
                    [:ref-local {:var-id "x"}]
                    [:interop-call [:java.lang.String/concat [:string] :string]]]},)


  (emit/make-fn {:class-name "MA1"
                 :args [],
                 :return-type :void,
                 :body
                 [[:string {:value "Johnny"}]
                  [:call-fn
                   [:Hello/invoke [:string] :string]]
                  [:print {:args [:string]}]]})

  (MA1/invoke)

  )



(:fns {"HelloWorld" {:args [:string]
                     :return-type :string
                     :body [[:string {:value "Hello "}]
                            [:arg {:value 0}]
                            [:interop-call [:java.lang.String/concat [ :string] :string]]]}}
      {"Main" {:args []
               :return-type :void
               :body [[:string {:value "Cherry Bomb"}]
                      [:call-fn [:HelloWorld/invoke [:string] :string]]]}})
