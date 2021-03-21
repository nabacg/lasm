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

(defn errorf [msg args & [ expr]]
  (throw (ex-info (apply format msg args)
                  {:expr expr
                   :msg msg})))


(defmulti ast-to-ir (fn [expr _]
                      (first expr)))

(defn do-asts-in-env [env exprs]
  (reduce (fn [[env irs] expr]
            (let [[env' ir] (ast-to-ir expr env)]
              [env' (conj irs ir)]))
          [env []] exprs))

(defmethod ast-to-ir :FunCall [[_ fn-name & fn-args] tenv]
  (if-let [fn-type  (tenv fn-name)]
    (let [{:keys [args return-type]} fn-type
          [_ args-ir] (do-asts-in-env tenv fn-args)]
      [tenv
       (if (empty? args-ir)
         [:call-fn [(keyword fn-name "invoke") args return-type]]
         (conj args-ir
               [:call-fn [(keyword fn-name "invoke") args return-type]]))])
    (error (format "Unknown function signature for fn-name: %s"))))



(defmethod ast-to-ir :FunDef [[_ fn-name {:keys [args return-type] :as fn-sign} & body] tenv]
  (let [tenv' (assoc tenv fn-name fn-sign)
        [tenv'' body-irs] (do-asts-in-env tenv' body)]
    [tenv''
     {fn-name {:args args
               :return-type return-type
               :body body-irs}}]))

(ast-to-ir [:FunCall "Main"] {"Main" {:args [:string]
                                      :return-type :string}})

(ast-to-ir [:FunDef "Main"  {:args [:string]
                             :return-type :string}
            [:FunCall "Other"]
            [:FunCall "Main"]] {"Other" {:args []
                                          :return-type :string}})


(:fns {"HelloWorld" {:args [:string]
                     :return-type :string
                     :body [[:string {:value "Hello "}]
                            [:arg {:value 0}]
                            [:interop-call [:java.lang.String/concat [ :string] :string]]]}}
      {"Main" {:args []
               :return-type :void
               :body [[:string {:value "Cherry Bomb"}]
                      [:call-fn [:HelloWorld/invoke [:string] :string]]]}})
