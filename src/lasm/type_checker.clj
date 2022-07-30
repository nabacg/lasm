(ns lasm.type-checker
  (:require [clojure.string :as string]
            [clojure.reflect :as reflect])
  (:import [org.objectweb.asm Type]
           [org.objectweb.asm.commons Method]))

(defn jvm-type-to-ir [java-type-sym]
  (let [type-name (name java-type-sym)]
    ;; TODO this should really be a check on whether this is a simple type by (#{int float long double char} ) etc.
    (if (string/includes? type-name ".")
      [:class type-name]
      (keyword type-name))))

(defn ast-expr-to-ir-type [[expr-type & exprs :as expr] tenv]
  (case expr-type
    ;; TODO this clearly needs loads of work
    :Bool   :bool
    :Int    :int
    ;; this solves fuzzy-type-equals actually, but only for strings
    :String [:class "java.lang.String"]
    :string [:class "java.lang.String"]
    :AddInt :int
    :SubInt :int
    :MulInt :int
    :DivInt :int
    :VarRef  (if-let [var-type (tenv (:var-id (first exprs)))]
               var-type)
    :FunCall (if-let [{:keys [return-type]} (tenv (first exprs))]
               return-type)
    :class expr))

(defn get-class-ancestors [klass]
  ;; keeps calling `klass`'s getSuperclass until we get all the way to java.lang.Object
  ;; needed because it turns out just passing `true` to (reflect/reflect klass :ancestors `true`)
  ;; doesn't really do anything, one needs to pass a list of ancestor classes
  (take-while #(not= % Object) (iterate #(.getSuperclass %) klass)))


(defn can-conform-types? [param-class arg-expr tenv]
  (assert (symbol? param-class)
          "Param-class needs to be a symbol")
  (println "arg-expr=" arg-expr " param-class=" param-class)
  (if (keyword? arg-expr)
    (case arg-expr
          :bool (= param-class 'boolean)
          false)
    (let [arg-type (ast-expr-to-ir-type arg-expr tenv)]
      (cond
        (= param-class arg-type)
        true
        (and (vector? arg-type) (= :class (first arg-type)))
        (let [[_ class-name] arg-type
              java-class (Class/forName class-name)]
          (or (= class-name (name param-class))
              (contains?             
               (into #{} (map #(.getName %)  (get-class-ancestors java-class)))
               (name param-class))))
        (= (name param-class) (name arg-type))
        true))))

(defn lookup-interop-method-signature
  ([class-name method-name call-arg-exprs static? tenv]
   (lookup-interop-method-signature class-name method-name call-arg-exprs static? tenv []))
  ([class-name method-name call-arg-exprs static? tenv ancestors]
   ;; because of the way we put 'this' as first argument
   ;; our call-args for non static will always have 1 more arg than
   ;; their corresponding java signature, so we need to (dec (count call-arg-expr))
   (let [call-arg-count (count call-arg-exprs)
         matching-methods
         (->> (reflect/reflect (Class/forName class-name)  :ancestors ancestors)
              :members
              (filter (comp #{(symbol method-name)} :name))
              ;;TODO also use args-maybe? to filter results on matching arity and parameter types!
              (filter (fn [{:keys [parameter-types]}]
                        (and (=
                              (count parameter-types)
                              call-arg-count)
                             (every? true?
                                     (map (fn [param arg-e]
                                            (can-conform-types? param arg-e tenv))
                                          parameter-types
                                          call-arg-exprs)))))
              (map (fn [{:keys [return-type parameter-types name]}]
                     {:return-type (jvm-type-to-ir return-type)
                      :args (mapv jvm-type-to-ir parameter-types)
                      :name (clojure.core/name name)}))
              distinct
              ;; TODO check how many matches we found and throw an error if there is ambiguity
              )
         matching-methods (if (and (empty? matching-methods) (empty? ancestors))
                            (lookup-interop-method-signature class-name method-name call-arg-exprs static? tenv
                                                             (get-class-ancestors (Class/forName class-name)))
                            matching-methods)]
     (when (not= (count matching-methods) 1)
       (throw (ex-info "Ambiguous Interop Method signature, found more then 1 match or no matches at all"
                       {:class-name class-name
                        :method-name method-name
                        :call-arg-exprs call-arg-exprs
                        :static? static?
                        :matching-methods matching-methods})))
     (first matching-methods))))

(defn symbol->type [sym]
  (case sym
    void Type/VOID_TYPE
    int Type/INT_TYPE
    long Type/LONG_TYPE
    boolean Type/BOOLEAN_TYPE
    :string (Type/getType java.lang.String)
    (cond (string/includes? (name sym) "<>")
          (case (name sym)
            "byte<>" (Type/getType "[B")
            (Type/getType (format "[L%s;"  (string/replace
                                            (string/replace  (name sym) "<>" "")
                                            "." "/"))))
          (and (symbol? sym) (= "Array" (namespace sym)))
          (case (name sym)
            "int" (Type/getType "[I")
            "byte" (Type/getType "[B")
            ;; TODO add other primitive types
            (Type/getType (format "[L%s;" (string/replace (name sym) "." "/"))))
          
          
          :else (Type/getType ^Class  (Class/forName (name sym))))))

(defn to-type-array [syms]
  (into-array Type (map symbol->type syms)))


(declare check synth augment augment-sub-expr)


(defn create-ctor-method [{:keys [arg-types]}]
  (Method. "<init>" Type/VOID_TYPE (to-type-array arg-types)))

(defn make-asm-type [class-name]
  (Type/getType (Class/forName class-name)))

(defn make-asm-ctor [tenv class-name ctor-args]
  (let [class-type (make-asm-type class-name)
        arg-types (map (fn [a] (synth {:expr a :env tenv})) ctor-args)
        method (create-ctor-method {:arg-types arg-types})]
    {:method method
     :owner class-type}))

(defn matches-type [expected-type {:keys [type expr env] :as ctx}]
  (if (= expected-type type)
    ctx
    (throw (ex-info "Type error" {:expected expected-type
                                  :found type
                                  :expr expr
                                  :env env}))))



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
    :Bool :bool
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
    (throw (ex-info "No Matching synth" {:expr expr :env env}))))

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











;;; type_checker bound

(comment
  ;; TODO needs a lot of work this

  (lookup-interop-method-signature "java.lang.Math"  "abs"
                                   [[:FunCall "f"  [:IntExpr 2]]]
                                   true
                                   {"f" {:args [:int]
                                         :return-type :int}})

  (lookup-interop-method-signature "java.lang.String" "concat"
                                   (list [:String " Hello" ] [:VarRef {:var-id "x"}])
                                   false
                                   {"x" [:class "java.lang.String"]})


  (lookup-interop-method-signature "java.lang.String" "replace"
                                   (list [:FunCall "Main"]
                                         [:String " Hello" ]
                                         [:VarRef {:var-id "x"}])
                                   false
                                   {"x" :string
                                    "Main" {:args [[:class "java.lang.String"]]
                                            :return-type [:class "java.lang.String"]}})

  (->> (reflect/reflect String)
       :members
       (filter (fn [{:keys [name]}] (= name 'replace))))

  ;; TODO How about that?
  {:return-type [:class "java.lang.String"],
   :args [:char :char],
   :name "replace"}
  )


(comment
  ;; TODO START HERE
  ;; this works
  (can-conform-types?   (symbol "java.lang.CharSequence") [:String "aa"] {})

  ;; this doesn
  (can-conform-types? (symbol  "java.lang.CharSequence") [:FunCall "Main"] { "Main" {:args [:string]
                                                                                     :return-type :string}})

  ;; but this would, if that's how we store types in tevn
  (can-conform-types? (symbol  "java.lang.CharSequence") [:FunCall "Main"]
                      { "Main" {:args [:class "java.lang.String"]
                                :return-type [:class "java.lang.String"]}})

  (reflect/reflect
   (class "java.lang.String"))

  (reflect/reflect String :ancestors true)

  (reflect/reflect String)

  (->> 
   (reflect/reflect (Class/forName "java.lang.String"))
   :members
   (filter (comp #{(symbol "concat")} :name)))
  )
