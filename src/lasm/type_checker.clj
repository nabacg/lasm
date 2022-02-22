(ns lasm.type-checker
  (:require [clojure.string :as string]))

(defn matches-type [expected-type {:keys [type expr env] :as ctx}]
  (if (= expected-type type)
    ctx
    (throw (ex-info "Type error" {:expected expected-type
                                  :found type
                                  :expr expr
                                  :env env}))))

(declare check synth augment augment-sub-expr)

(defn augment-then-synth [ctx]
  (synth (assoc ctx :expr (augment (dissoc ctx :type)))))


;; ctx -> ctx
;; but mostly to throw an error if check fails
(defn check [{:keys [type expr env] :as ctx}]
  (case (first expr)
    :Int (matches-type :int ctx)
    :VarRef (matches-type (synth ctx) ctx)
    :DivInt (do
              ;; check all Argos
              (let [[_ & ops] expr]
                (doseq [op ops]
                  ;; soon enough would need to augment-then-synth for more complex op
                  (matches-type (synth (assoc ctx :expr op))
                                (assoc ctx :expr op))))
              (matches-type :int ctx))
    :String (matches-type :string ctx)
    :>  (matches-type :boolean ctx)
    :== (matches-type :boolean ctx)
    :<  (matches-type :boolean ctx)
    :>= (matches-type :boolean ctx)
    :<= (matches-type :boolean ctx)
    :If (let [[_ pred truthy falsy] expr
              pred-type (augment-then-synth (assoc ctx :expr pred))
              truthy-type (augment-then-synth (assoc ctx :expr truthy))
              falsy-type  (augment-then-synth (assoc ctx :expr falsy))]
          ;; TODO extract this assert into separate method?
          (assert (= truthy-type falsy-type)
                  (format
                   "both branches of If need to have the same type, Found truthy-type: %s, falsy-type: %s"
                   truthy-type falsy-type))
          (matches-type pred-type (assoc ctx :expr pred :type :boolean))
          (matches-type truthy-type (assoc ctx :expr truthy))
          (matches-type falsy-type (assoc ctx :expr falsy)))
    
    :FunDef (let [[_ {:keys [args return-type]} & body] expr
                  env' (into env (map (juxt :id :type) args)) ;; extend the env with params
                  tail-expr (last body)]

              ;;TODO check each expr in body, but that should happen in augment 
              #_(doseq [e body]
                  ;; soon enough would need to augment-then-synth for more complex op
                  (matches-type (synth (assoc ctx :expr e :env env'))
                                (assoc ctx :expr e :env env')))

              (mapv (augment-sub-expr (assoc ctx :env env')) body)
              
              (matches-type return-type
                            {:type (synth (assoc ctx :expr tail-expr))
                             :env env'
                             :expr tail-expr})
              (synth ctx)
              )
    (throw (ex-info "No matching check" {:expr expr :type type}))))


;; ctx -> type
(defn synth [{:keys [expr env] :as ctx}]
  (case (first expr)
    :Int :int
    :String :string
    :If (synth { :expr (last expr) :env env})
    :> :boolean
    :== :boolean
    :< :boolean
    :>= :boolean
    :<= :boolean
    :VarRef (if-let [ty (env (:var-id (second expr)))]
              ty
              (throw (ex-info "Unknown Variable found" {:var-id (:var-id (second expr))
                                                        :expr expr
                                                        :env env})))
    :FunDef (let [[_ {:keys [args return-type]} & _] expr]
              [:fn (mapv :type args) return-type ])
    (throw (ex-info "No Matching synth" {:expr expr :env env})))
  )

;; ctx -> expr
(defn augment-sub-expr [{:keys [env type expr] :as parent-ctx}]
  (fn [e]
    ;; first augment the sub-expr in the parent-ctx, but without parent :type
    (let [res (augment (assoc (dissoc parent-ctx :type) :expr e))]
      (check {:env env
              :expr res  
              :type (synth {:env env
                            :expr res})})
      res)))

;; ctx -> expr
(defn augment [{:keys [env type expr] :as ctx}]
  (when type
    (:expr (check ctx))) ;; TODO should this return an expr not a ctx ?

  (if (not (vector? expr))    
    (throw (ex-info "augment can only be called with :expr [...]" {:expr expr
                                                                   :env env
                                                                   :type type})))

  (case (first expr)
    :VarRef expr
    :Int expr
    :String expr
    (into [(first expr)]
          (mapv (augment-sub-expr ctx)
                (rest expr)))))





(comment
  ;; this should pass
  (check {:expr [:FunDef
                 {:args [{:id "x" :type :int}]
                  :fn-name "Hello"
                  :return-type :int}
                 [:If [:>  [:VarRef {:var-id "x"}] [:Int{:value 119}]]
                  [:Int {:value  42}]
                  [:Int {:value -1}]]]
          :type [:fn [:int] :int]
          :env {}
          })

  
;; this should fail
  (check {:expr [:FunDef
                 {:args [{:id "x" :type :int}]
                  :fn-name "Hello"
                  :return-type :int}
                 [:If [:>  [:VarRef {:var-id "x"}] [:Int {:value 119}]]
                  [:Int {:value  42}]
                  [:Int {:value -1}]]
                 [:String "Boom"]]
          :type  [:fn [:int] :int] #_{:type :fn
                                      :args [:int]
                                      :return-type :int}
          :env {}
          })



  (check {:expr [:Int 32]
          :type :int
          :env {}})

  (check {:expr [:DivInt
                 [:Int 42]
                 [:Int 2]]
          :type :int
          :env {}})


  (augment {:expr [:DivInt
                   [:Int 42]
                   [:Int 2]]
            :type :int
            :env {}})

  (augment {:expr [:DivInt
                   [:Int 42]
                   [:String "2"]]
            :type :int
            :env {}})


  (check {:expr [:DivInt
                 [:String "Boom"]
                 [:Int 2]]
          :type :int
          :env {}}))






