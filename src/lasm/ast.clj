(ns lasm.ast
  (:require [clojure.string :as string]
            [clojure.reflect :as reflect]))


(defn error [expr & [msg ]]
  (throw (ex-info (str  "Invalid-expression: " msg)
                  {:expr expr
                   :msg msg})))

(defn errorf [msg & args]
  (throw (ex-info (apply format msg args)
                  {:msg msg
                   :args args})))


(defmulti ast-to-ir (fn [expr _] (first expr)))

(defn args-to-locals [args]
  (vec
   (mapcat
    identity
    (map-indexed
     (fn [i {:keys [id type]}]
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

(defmethod ast-to-ir :String [[_ str-val] env]
  [env [[:string {:value str-val}]]])

(defmethod ast-to-ir :Int [[_  int-val] env]
  [env [[:int {:value int-val}]]])

(defmethod ast-to-ir :VarRef [[_ {:keys [var-id var-type]}] env]
  (let [declared-type var-type
        var-type (env var-id)]
    (cond
      (and (nil? declared-type) (nil? var-type))
      (errorf "Unknown variable found %s" var-id)

      (and declared-type var-type (not= declared-type var-type))
      (errorf "Incompatible variable types found, declared-type: %s vs env-type: %s" declared-type var-type)
      :else
      [env [[:ref-local {:var-id var-id
                         ;; if at least one type is not empty, use it for var-type
                         :var-type (or var-type declared-type)}]]])))

(defmethod ast-to-ir :VarDef [[_ {:keys [var-id var-type]} val-expr] tenv]
  (let [[_ val-ir] (ast-to-ir val-expr tenv)]
    [(assoc tenv var-id var-type)
     (conj val-ir
           [:def-local {:var-id var-id :var-type var-type}])]))

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
       [[:label {:value exit-lbl}]]))]))


(defn jvm-type-to-ir [java-type-sym]
  (let [type-name (name java-type-sym)]
    ;; TODO this should really be a check on whether this is a simple type by (#{int float long double char} ) etc.
    (if (string/includes? type-name ".")
      [:class type-name]
      (keyword type-name))))

(defn ast-expr-to-ir-type [[expr-type & exprs] tenv]
  (case expr-type
      ;; TODO this clearly needs loads of work
    :Int    :int
    ;; this solves fuzzy-type-equals actually, but only for strings
    :String [:class "java.lang.String"]
    :AddInt :int
    :SubInt :int
    :MulInt :int
    :DivInt :int
    :VarRef  (if-let [var-type (tenv (:var-id (first exprs)))]
               var-type)
    :FunCall (if-let [{:keys [return-type]} (tenv (first exprs))]
               return-type)))

(defn can-conform-types? [param-class arg-expr tenv]
  (assert (symbol? param-class)
          "Param-class needs to be a symbol")
  (let [arg-type (ast-expr-to-ir-type arg-expr tenv)]
    (cond
      (= param-class arg-type)
      true
      (and (vector? arg-type) (= :class (first arg-type)))
      (let [[_ class-name] arg-type
            java-class (Class/forName class-name)]
        (or (= class-name (name param-class))
            (contains?
             (:bases (reflect/reflect java-class))
             param-class)))
      (= (name param-class) (name arg-type))
      true)))



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

  (reflect/reflect String))


(defn lookup-interop-method-signature [class-name method-name call-arg-exprs static? tenv]
  ;; because of the way we put 'this' as first argument
  ;; our call-args for non static will always have 1 more arg than
  ;; their corresponding java signature, so we need to (dec (count call-arg-expr))
  (let [call-arg-count (if static?
                         (count call-arg-exprs)
                         (dec (count call-arg-exprs)))
        matching-methods
        (->> (reflect/reflect (Class/forName class-name))
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
             ;; TODO check how many matches we found and throw an error if there is ambiguity
             )]
    (when (not= (count matching-methods) 1)
      (throw (ex-info "Ambiguous Interop Method signature, found more then 1 match or no matches at all"
                      {:class-name class-name
                       :method-name method-name
                       :call-arg-exprs call-arg-exprs
                       :static? static?
                       :matching-methods matching-methods})))
    (first matching-methods)))

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


(defmethod ast-to-ir :InteropCall [[_ {:keys [class-name method-name static?]} & method-args] tenv]
  #_(println ":InteropCall=" [class-name method-name static?] " method-args = " method-args " tenv= " tenv)
  (let [{:keys [args return-type] :as env-type}  (tenv method-name)
        ;; TODO should interop take a map with params instead positional?
        jvm-type (lookup-interop-method-signature class-name method-name method-args static? tenv)
        [_ args-ir]      (map-ast-to-ir tenv method-args)
        ir-op   (if static? :static-interop-call :interop-call)
        {:keys [return-type args]}  (merge-with #(or %1 %2)   jvm-type env-type )]

   #_ (println "env-type= " env-type " jvm-type= " jvm-type)
   #_ (println "class-name=" class-name " method-name=" method-name "args=" method-args)
    (if  (or env-type jvm-type)
      [tenv
       (conj (vec args-ir) ;; in case args-ir is empty, thus '()
             [ir-op [(keyword (str class-name "/" method-name)) args return-type]])]
      (errorf "Unknown method signature for class/method: %s/%s" class-name method-name))))


(defmethod ast-to-ir :FunCall [[_ fn-name & fn-args] tenv]
  (if-let [fn-type  (tenv fn-name)]
    (let [{:keys [args return-type special-form]} fn-type
          [_ args-ir] (map-ast-to-ir tenv fn-args)
          ]
      [tenv
       (cond
         (and special-form
              (empty? args-ir)) [special-form]
         special-form           (conj args-ir
                                      [special-form])
         (empty? args-ir)
         [[:call-fn [(keyword fn-name "invoke") args return-type]]]
         :else
         (conj args-ir
               [:call-fn [(keyword fn-name "invoke") args return-type]]))])
    (errorf "Unknown function signature for fn-name: %s" fn-name fn-args)))


(defmethod ast-to-ir :FunDef [[_ {:keys [args return-type fn-name]} & body] tenv]
;;  (println [args return-type fn-name] body )
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
  {"printstr"  {:args [[:class "java.lang.String"]]
                :return-type [:class "java.lang.String"]
               :special-form :print-str}
   "printint"  {:args [:int]
                :return-type :int
                :special-form :print}})

(defn build-program [exprs]
  (let [{:keys [FunDef] :as expr-by-type} (group-by first exprs)
        top-level-exprs    (mapcat identity (vals (dissoc expr-by-type :FunDef))) ;; take everything but :FunDef
        [tenv fn-defs-ir]  (map-ast-to-ir (init-tenv) FunDef)
        ;; TODO does this really need to be that complicated? we could probably return a {FnName -> Fn} map from above fn
        ;fn-defs-ir         (apply merge fn-defs-ir)
        main-fn-name       (name (gensym "Main_"))
        [_ [main-fn-ir]]   (map-ast-to-ir
                            tenv
                            [(into
                              [:FunDef
                               {:args []
                                :fn-name main-fn-name
                                :return-type :void}]
                              top-level-exprs)])]
    ;;TODO check if all top level IR expr are maps of {FnNamestr Fn-expr}
    {:fns (conj (vec fn-defs-ir) main-fn-ir)
     :entry-point main-fn-name}))



(comment
  ;; IF
  (ast-to-ir
   [:FunDef
    {:args [{:id "x" :type :int}]
     :fn-name "Hello"
     :return-type :int}
    [:If [:>  [:VarRef {:var-id "x"}] [:Int{:value 119}]]
     [:Int {:value  42}]
     [:Int {:value -1}]]]
   {})

  (require '[lasm.emit :as emit])

  (emit/emit! {:fns [{:args [:int],
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

  (Cond12/invoke 120)

  (ast-to-ir [:FunCall "Main"]
             {"Main" {:args [{:id "x" :type :string}]
                      :return-type :string}})

  (ast-to-ir [:DivInt 23 1]
             {})


  [:InteropCall
   {:class-name "java.lang.Math", :method-name "abs", :static? true}
   [:SubInt
    [:Int 100]
    [:FunCall
     "f"
     [:AddInt [:Int 2] [:MulInt [:Int 8] [:Int 100]]]]]]

  (Cond12/invoke 120)


  (ast-to-ir [:FunDef  {:fn-name "Main"
                        :args [{:id "x" :type :string}]
                        :return-type :string}
              [:FunCall "Other"]
              [:FunCall "Main" [:String "Arg1"]]]
             {"Other" {:args []
                       :return-type :string}})


  (ast-to-ir [:FunDef
              {:fn-name "HelloWorld"
               :args [{:id "x" :type :string}]
               :return-type :string}
              [:InteropCall {:class-name "java.lang.String"
                             :method-name "concat"}
               [:String "Hello "]
               [:VarRef
                {:var-id "x"}]]]
             {})

  (map-ast-to-ir (init-tenv)
                 [[:FunDef
                    {:fn-name "HelloWorld"
                     :args [{:id "x" :type :string}]
                     :return-type :string}
                    [:InteropCall {:class-name "java.lang.String"
                                   :method-name "concat"}
                     [:String "Hello "]
                     [:VarRef
                      {:var-id "x"}]]]
                  [:FunDef
                   {:args []
                    :fn-name "Main"
                     :return-type :void}
                   [:FunCall "HelloWorld"

                    [:String "Johnny"]]]])

  (ast-to-ir [:FunDef {:args [], :fn-name "f" :return-type :int}
              [:AddInt [:Int 2] [:MulInt
                                 [:Int 2]
                                 [:Int 4]]]] {})

  (build-program [[:FunDef {:args [], :return-type :int :fn-name "f"}
                   [:AddInt [:Int 2] [:Int  2]]]
                  [:FunCall "f"]])

  (build-program
   [[:FunDef
     {:fn-name "HelloWorld"
      :args [{:id "x" :type :string}]
      :return-type :string}
     [:InteropCall {:class-name "java.lang.String"
                    :method-name "concat"}
      [:String "Hello "]
      [:VarRef
       {:var-id "x"}]]]
    [:FunDef
     {:args []
      :fn-name "Main"
      :return-type :void}
     [:FunCall "HelloWorld" [:String "Johnny"]]]
    [:FunCall "Main"]])


  )




(comment
  (require '[lasm.emit :as emit])


  ;; make-fn from AST via build-programs
  (-> (build-program
       [[:FunDef
         {:fn-name "Hello"
          :args [{:id "x" :type :string}]
          :return-type :string}
         [:InteropCall {:class-name "java.lang.String"
                        :method-name "concat"}
          [:String "Hello "]
          [:VarRef {:var-id "x"}]]]
        [:FunDef
         {:fn-name "Main112"
          :args []
          :return-type :string}
         [:FunCall "Hello" [:String "Johnny"]]]])
      emit/emit-and-run!)


  ;; make-fn from IR

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
