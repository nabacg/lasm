(ns lasm.type-checker
  (:require [clojure.string :as string]
            [clojure.reflect :as reflect])
  (:import [org.objectweb.asm Type]
           [org.objectweb.asm.commons Method]))

(defn jvm-type-to-ir [java-type-sym]
  ;; Handle Java Class objects from reflection
  (if (instance? Class java-type-sym)
    (let [class-name (.getName ^Class java-type-sym)]
      (case class-name
        "int" :int
        "long" :long
        "boolean" :boolean
        "void" :void
        "double" :double
        "float" :float
        "byte" :byte
        "short" :short
        "char" :char
        ;; For object types, return [:class "fully.qualified.Name"]
        [:class class-name]))
    ;; Handle symbols/keywords
    (let [type-name (name java-type-sym)]
      ;; TODO this should really be a check on whether this is a simple type by (#{int float long double char} ) etc.
      (if (string/includes? type-name ".")
        [:class type-name]
        (keyword type-name)))))

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
  (if (keyword? arg-expr)
    (case arg-expr
          :bool (= param-class 'boolean)
          :int (= param-class 'int)
          false)
    (let [arg-type (ast-expr-to-ir-type arg-expr tenv)]
      (cond
        (= param-class arg-type)
        true
        ;; Handle primitive type matching
        (and (= arg-type :bool) (= param-class 'boolean))
        true
        (and (= arg-type :int) (= param-class 'int))
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
  ;; Handle [:class "..."] format
  (if (and (vector? sym) (= :class (first sym)))
    (Type/getType ^Class (Class/forName (second sym)))
    (case sym
      void Type/VOID_TYPE
      int Type/INT_TYPE
      long Type/LONG_TYPE
      boolean Type/BOOLEAN_TYPE
      :string (Type/getType java.lang.String)
      :int  Type/INT_TYPE
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


            :else (Type/getType ^Class  (Class/forName (name sym)))))))

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
  (cond
    (= expected-type type)
    ctx

    ;; Handle :string <=> [:class "java.lang.String"] equivalence
    (and (= type :string) (= expected-type [:class "java.lang.String"]))
    ctx

    (and (= expected-type :string) (= type [:class "java.lang.String"]))
    ctx

    :else
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
                  ;; Thread environment through body expressions
                  [final-env augmented-body]
                  (reduce (fn [[acc-env acc-exprs] e]
                            (let [[new-env aug-expr] (augment {:expr e :env acc-env})]
                              [new-env (conj acc-exprs aug-expr)]))
                          [env' []]
                          body)
                  tail-expr (last augmented-body)
                  fun-type (synth ctx)]
              (matches-type return-type
                            {:type (synth {:expr tail-expr :env final-env})
                             :env final-env
                             :expr tail-expr})
              ctx)
    :FunCall (let [[_ fun-name & fun-args] expr]
               (if-let [f-ty (env fun-name) ]
                 (let [{:keys [return-type args]} f-ty]
                   (matches-type return-type ctx)
                   (doseq [[expected-ty found-expr] (map vector args fun-args)]
                     (matches-type expected-ty
                                   {:type (synth {:expr found-expr :env env})
                                    :expr found-expr
                                    :env env}))
                   ctx)
                 (throw (ex-info (format "unknown function in FunCall expr: %s" fun-name)
                                 {:fun-name fun-name
                                  :expr expr
                                  :env env}))))
    :CtorInteropCall (let [[_ {:keys [class-name]} & ctor-args] expr]
                       ;; For now, just check that it matches the expected class type
                       ;; TODO: validate constructor argument types
                       (matches-type [:class class-name] ctx))
    :VarDef (let [[_ {:keys [var-type]} init-expr] expr]
              ;; Check that the initializer expression matches the variable type
              (when init-expr
                (let [init-type (synth {:expr init-expr :env env})]
                  (matches-type var-type
                                {:type init-type
                                 :expr init-expr
                                 :env env})))
              ;; VarDef doesn't have a type itself, just return ctx
              ctx)
    :InteropCall (let [[_ {:keys [this-expr method-name]} & method-args] expr
                       method-return-type (synth ctx)]
                   (matches-type method-return-type ctx))
    :StaticInteropCall (let [method-return-type (synth ctx)]
                         (matches-type method-return-type ctx))
    :StaticFieldAccess (let [field-type (synth ctx)]
                         (matches-type field-type ctx))
    :Proxy (let [[_ {:keys [class-or-interface]}] expr
                 proxy-type [:class class-or-interface]]
             ;; TODO: validate that all required methods are implemented
             (matches-type proxy-type ctx))
    (throw (ex-info "No matching check" {:expr expr :type type}))))


;; ctx -> type
(defn synth [{:keys [expr env] :as ctx}]

  (if (not (vector? expr))
    (throw (ex-info "augment can only be called with :expr [...]" {:expr expr
                                                                   :env env
                                                                   :type type})))

  (case (first expr)
    :Int :int
    :Bool :bool
    :String :string
    :AddInt :int
    :SubInt :int
    :MulInt :int
    :DivInt :int
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
    :VarDef (let [[_ {:keys [var-type]}] expr]
              (if (not (nil? var-type))
                var-type
                (throw (ex-info "VarDef with unknown type, expected :var-type prop to be not empty"
                                {:expr expr
                                 :env env}))))
    :FunDef (let [[_ {:keys [args return-type]} & _] expr]
              [:fn (mapv :type args) return-type ])
    :FunCall (let [[_ fun-name & args] expr]
               (if-let [ty (env fun-name)]
                 (:return-type ty)
                 (throw (ex-info (format "Undefined Function called found: %s" fun-name)
                                 {:var-id (:var-id (second expr))
                                  :expr expr
                                  :env env}))))
    :CtorInteropCall (let [[_ {:keys [class-name]} & args] expr]
                       [:class class-name])
    :InteropCall (let [[_ {:keys [this-expr method-name]} & method-args] expr
                       this-type (synth {:expr this-expr :env env})
                       [_ class-name] (if (vector? this-type)
                                        this-type
                                        (ast-expr-to-ir-type [this-type] env))
                       method-sig (lookup-interop-method-signature class-name method-name method-args false env)]
                   (:return-type method-sig))
    :StaticInteropCall (let [[_ {:keys [class-name method-name]} & method-args] expr
                             method-sig (lookup-interop-method-signature class-name method-name method-args true env)]
                         (:return-type method-sig))
    :StaticFieldAccess (let [[_ {:keys [class-name field-name]}] expr
                             clazz (Class/forName class-name)
                             field (.getDeclaredField clazz field-name)
                             field-type (.getType field)]
                         (jvm-type-to-ir field-type))
    :Proxy (let [[_ {:keys [class-or-interface]}] expr]
             [:class class-or-interface])
    (throw (ex-info "No Matching synth" {:expr expr :env env}))))

;; ctx -> expr
(defn augment-sub-expr [{:keys [env type expr] :as parent-ctx}]
  (fn [e]
    ;; first augment the sub-expr in the parent-ctx, but without parent :type
    (try
      (let [[env' augmented-expr] (augment (assoc (dissoc parent-ctx :type) :expr e))]
        (check {:env env'
                :expr augmented-expr
                :type (synth {:env env'
                              :expr augmented-expr})})
        augmented-expr)
      (catch  clojure.lang.ExceptionInfo e
        (throw (ex-info "augment-sub-expr Exception" {:parent-ctx parent-ctx
                                                  :type type
                                                  :expr expr
                                                  :env env
                                                      :sub-expr e
                                                      :original-exception (.data e)})))
      (catch  Exception e
        (throw e)))))


;;TODO this is a mess, should really write down all Type checker types (map structure)
;; and decide what each of below returns
;; - synth
;; - augment
;; - check


;; ctx -> expr
(defn augment [{:keys [env type expr] :as ctx}]
  (when type
    (:expr (check ctx))) ;; TODO should this return an expr not a ctx ?

  (if (not (vector? expr))
    (throw (ex-info "augment can only be called with :expr [...]" {:expr expr
                                                                   :env env
                                                                   :type type})))

  (case (first expr)
    :VarRef [env expr]
    :Int [env expr]
    :String [env expr]
    :FunDef
    (let [fun-type (synth {:expr expr :env env})
          [_ {:keys [fn-name args return-type]} & body] expr
          _ (check {:expr expr :env env :type fun-type})
          ;; Add function to environment
          arg-types (mapv :type args)
          env' (assoc env fn-name {:args arg-types :return-type return-type})]
      [env' expr])
    :FunCall
    (let [{:keys [expr env]}   (check
                                (assoc ctx :type (synth ctx)))]
      [env expr])
    :CtorInteropCall
    (let [ctor-type (synth {:expr expr :env env})]
      (check {:expr expr :env env :type ctor-type})
      [env expr])
    :VarDef
    (let [[_ {:keys [var-id var-type] :as metadata} init-expr] expr]
      (if init-expr
        ;; If there's an initializer, augment it, then add var to env
        (let [[env' augmented-init] (augment (assoc ctx :expr init-expr))
              env'' (assoc env' var-id var-type)]
          [env'' [:VarDef metadata augmented-init]])
        ;; No initializer, just add var to env
        [(assoc env var-id var-type) expr]))
    :InteropCall
    (let [{:keys [expr env]}   (check
                                (assoc ctx :type (synth ctx)))]
      [env expr])
    :StaticInteropCall
    (let [{:keys [expr env]}   (check
                                (assoc ctx :type (synth ctx)))]
      [env expr])
    :StaticFieldAccess
    (let [{:keys [expr env]}   (check
                                (assoc ctx :type (synth ctx)))]
      [env expr])
    :Proxy
    (let [proxy-type (synth {:expr expr :env env})]
      (check {:expr expr :env env :type proxy-type})
      [env expr])
    ;; Default case: reconstruct expression with augmented sub-expressions
    [env (into [(first expr)]
               (mapv (augment-sub-expr ctx)
                     (rest expr)))]))





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






(defn matrix [x y]
  (->> (range 1 (inc (* x y)))
       (partition y)
       (map vec)
       (into [])))

(defn inc-x [dims]
  (map (fn [[x y]] [(inc x) y])
       dims))

(defn inc-y [dims]
  (map (fn [[x y]] [x (inc y)])
       dims))

(defn path [direction x y]
  (cond
    (or (= x 0) (= y 0))     []
    (and (= x y 1))     [[0 0]]
    :else
    (case direction
      :right (concat
              (mapv #(vector 0 %) (range 0  y))
              (inc-x (path :down (dec x) y)))
      :down (concat
             (mapv #(vector % (dec y)) (range 0 x))
             (path :left x (dec y)))
      :left  (concat
              (mapv #(vector (dec x) %) (reverse (range 0  y)))
              (path :up (dec x) y))
      :up (concat
           (mapv #(vector % 0) (reverse (range 0 x)))
           (inc-y (path :right x (dec y))))
      )))

(defn spiral [m]
  (->> (path :right  (count m) (count (first m)))
       (mapv #(get-in m %))))

(comment 

  (spiral (matrix 2 2))
  ;; => [1 2 4 3]
  (spiral (matrix 3 3))
  ;; => [1 2 3 6 9 8 7 4 5]
  (spiral (matrix 4 4))
  ;; => [1 2 3 4 8 12 16 15 14 13 9 5 6 7 11 10]


  (spiral (matrix 0 0));; => ;[]



  (spiral (matrix 4 2))
  ;; => [1 2 4 6 8 7 5 3]

  (spiral (matrix 2 4))
  ;; => [1 2 3 4 8 7 6 5]


  (spiral (matrix 4 3));; => [1 2 3 6 9 12 11 10 7 4 5 8]


  (/ 3900.0 40)
  ) 
