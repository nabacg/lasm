(ns lasm.emit
  (:require [clojure.string :as string]
            [clojure.pprint :as pprint]
            [lasm.decompiler :as decomp])
  (:import [org.objectweb.asm Opcodes Type ClassWriter]
           [org.objectweb.asm.commons Method GeneratorAdapter]))


(set! *warn-on-reflection* true)

(def INIT (Method/getMethod "void <init>()"))

(defn resolve-type [expr-type]
  (cond
    (= Type (type expr-type))  ;;if expr is already of asm.Type
    expr-type
    (and (vector? expr-type)
         (= (first expr-type) :class))
    ;; This will most certainly not work long term, especially once we want to import between packages and stuff
    ;; but for now it allows recursive functions
    (Type/getType (str "L" (string/replace (second expr-type) "." "/") ";"))
    :else
    (case expr-type
      :string (Type/getType String)
      :object (Type/getType Object)
      :void Type/VOID_TYPE
      :boolean Type/BOOLEAN_TYPE
      :int Type/INT_TYPE
      :long Type/LONG_TYPE
      :bool Type/BOOLEAN_TYPE
      nil (throw (ex-info "[resolve-type] NIL expr-type!" {:expr-type nil}))
      (throw (ex-info "Unknown expr-type, resolve-type failed" {:expr-type expr-type})))))

(defn resolve-cmp-type [op]
  (case op
    :== GeneratorAdapter/EQ
    :!= GeneratorAdapter/NE
    :>  GeneratorAdapter/GT
    :>= GeneratorAdapter/GE
    :<  GeneratorAdapter/LT
    :<= GeneratorAdapter/LE))


(defn build-method [method-name return-type args]
  ;; Consider using method signature string and Method/getMethod like this
  ;; (Method/getMethod "java.lang.Object invoke (java.lang.Object)")
  (Method. method-name
           (resolve-type return-type)
           (into-array Type
                       (map (fn [arg] (resolve-type
                                       (if (map? arg)
                                         (:var-type arg)
                                         arg)))
                            args))))

(defn build-invoke-props [[fn-name args return-type]]
  (let [owner-class  (namespace fn-name)
        method-name  (name fn-name)]
    {:owner [:class owner-class]
     :method (build-method method-name return-type args)}))

(defn build-invoke-static [cmd]
  [:invoke-static (build-invoke-props cmd)])

(defn emit-print [^GeneratorAdapter ga {:keys [args] :or {args [:int]}}]
  (let [out-stream-type (Type/getType ^java.lang.Class
                                      (.getGenericType
                                       (.getDeclaredField
                                        (Class/forName "java.lang.System") "out")))]
    (.dup ga)
    (.getStatic ga
                (resolve-type [:class "java.lang.System"])
                "out"
                out-stream-type)
    (.swap ga)
    (.invokeVirtual ga  out-stream-type
                    (build-method "println" :void args))))


(defn emit-instr! [^GeneratorAdapter ga [cmd-type cmd]]
  (case cmd-type
    :do nil
    :add-int
    (recur ga [:math {:op GeneratorAdapter/ADD
                      :op-type Type/INT_TYPE}])
    :sub-int
    (recur ga [:math {:op GeneratorAdapter/SUB
                      :op-type Type/INT_TYPE}])
    :mul-int
    (recur ga [:math {:op GeneratorAdapter/MUL
                      :op-type Type/INT_TYPE}])
    :div-int
    (recur ga [:math {:op GeneratorAdapter/DIV
                      :op-type Type/INT_TYPE}])
    :call-fn
    (recur ga (build-invoke-static cmd))
    :interop-call
    (recur ga [:invoke-virtual (build-invoke-props cmd)])
    :static-interop-call
    (recur ga [:invoke-static  (build-invoke-props cmd)])
    :arg
    (.loadArg ga (:value cmd))
    :math
    (.math ga (:op cmd) (:op-type cmd))
    :get-static-field
    (.getStatic ga (resolve-type (:owner cmd)) (:name cmd) (resolve-type  (:result-type cmd)))
    :invoke-static
    (.invokeStatic ga (resolve-type (:owner cmd)) (:method cmd))
    :invoke-virtual
    (.invokeVirtual ga (resolve-type (:owner cmd)) (:method cmd))
    :invoke-interface
    (.invokeInterface ga (resolve-type (:owner cmd))  (:method cmd))
    :invoke-constructor
    (.invokeConstructor ga (resolve-type (:owner cmd)) (:method cmd))
    :new
    (do
      ;; ctor call that will happen immediately after will consume it's argument, leaving empty stack and leading to null pointer exception
      (.newInstance ga (:owner cmd))
      (.dup ga))
    :store-local
    (.storeLocal ga (:local-ref cmd) (resolve-type (:local-type cmd)))
    :load-local
    (.loadLocal ga  (:local-ref cmd) (resolve-type (:local-type cmd)))
    :bool
    (.push ga (boolean (:value cmd)))
    :int
    (.push ga (int (:value cmd)))
    :string
    (.push ga ^String (:value cmd))
    :put-field
    (.putField ga (:owner cmd) (:name cmd) (resolve-type (:field-type cmd)))
    :dup
    (.dup ga)
    :pop
    (.pop ga)
    :box
    (.box ga (resolve-type  (:var-type cmd)))
    :unbox
    (.unbox ga  (resolve-type (:var-type cmd)))
    :return
    (.returnValue ga)
    :print
    (emit-print ga cmd)
    :print-str
    (emit-print ga {:args [:string]})))

(defn emit-with-env [^GeneratorAdapter ga env [cmd-type cmd :as c]]
  (case cmd-type
    :def-local (let [{:keys [value var-type var-id]} cmd
                     var-handle (.newLocal ga (resolve-type var-type))]
                 (.storeLocal ga var-handle)
                 (assoc env var-id var-handle))
    :ref-local (let [var-handle (env (:var-id cmd))]
                 (.loadLocal ga var-handle)
                 env)
    :label     (if-let [label (get env (:value cmd))]
                 (do
                   (.mark ga label)
                   env)
                 (let [label (.newLabel ga)]
                   (.mark ga label)
                   (assoc env (:value cmd) label)))
    :jump-cmp (if-let [label (get env cmd)]
                (do
                  (.ifCmp ga (resolve-type (:compare-type cmd)) (resolve-cmp-type (:compare-op cmd)) label))
                (let [label (.newLabel ga)]
                  (.ifCmp ga (resolve-type (:compare-type cmd)) (resolve-cmp-type (:compare-op cmd)) label)
                  (assoc env (:value cmd) label)))

    :jump     (if-let [label (get env (:value cmd))]
                (do
                  (.goTo ga label)
                  env)
                (let [label (.newLabel ga)]
                  (.goTo ga label)
                  (assoc env (:value cmd) label)))
    (do
      (emit-instr! ga c)
      env)))

(defn generate-default-ctor
  ([^ClassWriter writer] (generate-default-ctor writer (Type/getType  Object)))
  ([^ClassWriter writer ^Type superclass-type]
   (let [ga (GeneratorAdapter. Opcodes/ACC_PUBLIC INIT nil nil writer)]
     (.loadThis ga)
     (.invokeConstructor ga superclass-type INIT)
     (.returnValue ga)
     (.endMethod ga))))


(defn initialize-class [^ClassWriter writer class-name]
  (.visit writer Opcodes/V1_8 Opcodes/ACC_PUBLIC class-name nil "java/lang/Object" nil))


(defn make-static-method [^ClassWriter writer method-name {:keys [args return-type body] :as method-description}]
  (let [method (Method. method-name (resolve-type return-type)
                        (into-array Type (map (comp resolve-type) args)))
        ga     (GeneratorAdapter. (int (+ Opcodes/ACC_PUBLIC Opcodes/ACC_STATIC)) method nil nil writer)]
    (reduce (fn [env line] (emit-with-env  ga env line)) {} body)
    (.endMethod ^GeneratorAdapter ga)))

;(require '[lasm.decompiler :as decomp])

(defn make-fn [{:keys [class-name body] :as fn-definition}]
  (let [writer (ClassWriter. ClassWriter/COMPUTE_FRAMES)
        body   (if (= :return (first (last body))) body (conj body [:return]))]

    (println "make-fn name=" class-name " body=")
    (pprint/pprint body)
    (initialize-class writer class-name)

    (generate-default-ctor writer)
    (make-static-method writer "invoke" (assoc fn-definition :body body))
    (.visitEnd writer)
    (.defineClass ^clojure.lang.DynamicClassLoader
                  (clojure.lang.DynamicClassLoader.)
                  (.replace ^String class-name \/ \.)
                  (.toByteArray ^ClassWriter writer) nil)
    (decomp/to-bytecode (.toByteArray ^ClassWriter  writer)
                       class-name)

    #_(decomp/to-java (.toByteArray ^ClassWriter  writer)
                    class-name)
    class-name))


(defn make-entry-point [class-name code]
  (make-fn {:class-name class-name
            :args []
            :return-type :int
            :body code}))

(defn emit! [{:keys [fns]}]
  (run! make-fn fns))

(defn emit-and-run! [{:keys [ entry-point] :as exprs}]
  (emit! exprs)

  (.invoke ^java.lang.reflect.Method
           (.getMethod ^java.lang.Class (Class/forName entry-point)
                       "invoke" (into-array Class []))
           nil
           ;; maybe pass cmd line args into `emit-and-run`?
           (into-array Object [])))


(comment
  [:FunDef "Hello"
   {:args [{:id "x" :type :string}]
    :return-type :string}
   [:If [:>  [:VarRef "x"] 119] 42 -1]]

  (emit! {:fns [{:args [:int],
                 :return-type :int,
                 :body
                 [[:arg {:value 0}]
                  [:int {:value 119}]
                  [:jump-cmp {:value "truthy" :compare-op GeneratorAdapter/GT :compare-type Type/INT_TYPE}]
                  [:int {:value -1}]
                  [:jump {:value "exit"}]
                  [:label {:value "truthy"}]
                  [:int {:value 42}]
                  [:label {:value "exit"}]],
                 :class-name "Cond12"}]
          :entry-point "Cond12"})


  (emit! {:fns [{:class-name "Hello",
                 :args [:int],
                 :return-type :int,
                 :body
                 [[:arg {:value 0}]
                  [:def-local {:var-id "x", :var-type :int}]
                  [:ref-local {:var-id "x"}]
                  [:int {:value 119}]
                  [:jump-cmp
                   {:value "truthy_lbl_9887", :compare-op :>, :compare-type :int}]
                  [:int {:value -1}]
                  [:jump {:value "exit_lbl9888"}]
                  [:label {:value "truthy_lbl_9887"}]
                  [:int {:value 42}]
                  [:label {:value "exit_lbl9888"}]]}]
          :entry-point "Hello"})

  (Hello/invoke 20)







  ;; bril like syntax ?
  ;; https://capra.cs.cornell.edu/bril/lang/syntax.html
  (emit-and-run! {:fns [{:args [:int],
                        :return-type :int,
                        :body
                        [[:int {:var-type :int, :value 1}] [:arg {:value 0}] [:add-int]],
                        :class-name "Inc"}
                       {:args [:int],
                        :return-type :int,
                        :body [[:arg {:value 0}] [:call-fn [:Inc/invoke [:int] :int]]],
                        :class-name "CallMethod"}
                       {:args [:string],
                        :return-type :string,
                        :body
                        [[:string {:value "Hello "}]
                         [:arg {:value 0}]
                         [:interop-call [:java.lang.String/concat [:string] :string]]],
                        :class-name "HelloWorld"}
                        {:args [:int],
                         :return-type :int,
                         :body
                         [[:int {:value 119}]
                          [:arg {:value 0}]
                          [:def-local {:var-id "x", :var-type :int}]
                          [:ref-local {:var-id "x"}]
                          [:int {:var-type :int, :value 13}]
                          [:mul-int]
                          [:sub-int]
                          [:int {:value 2}]
                          [:ref-local {:var-id "x"}]
                          [:ref-local {:var-id "x"}]
                          [:mul-int]
                          [:mul-int]
                          [:add-int]],
                         :class-name "DoMath"}
                       {:args [],
                        :return-type :void,
                        :body
                        [[:string {:value "Inc/invoke 41"}]
                         [:print {:args [:string]}]
                         [:int {:value 41}]
                         [:call-fn [:Inc/invoke [:int] :int]]
                         [:print]
                         [:string {:value "HelloWorld/invoke MyNameIsJohnny"}]
                         [:print {:args [:string]}]
                         [:string {:value "MyNameIsJohnny"}]
                         [:call-fn [:HelloWorld/invoke [:string] :string]]
                         [:print {:args [:string]}]
                         [:string {:value "(DoMath/invoke (Inc/invoke (Inc/invoke 100)))"}]
                         [:print {:args [:string]}]
                         [:int {:value 100}]
                         [:call-fn [:Inc/invoke [:int] :int]]
                         [:call-fn [:Inc/invoke [:int] :int]]
                         [:call-fn [:DoMath/invoke [:int] :int]]
                         [:print]],
                        :class-name "Main3"}]
                  :entry-point "Main3"}))




(comment

  (do
    (make-entry-point
     "HelloWorld"
     [[:int {:var-type :int, :value 23}]
      [:def-local {:var-id "x" :var-type :int}]
      [:int {:var-type :int, :value 119}]
      [:int {:var-type :int :value 19}]
      [:ref-local {:var-id "x"}]
      [:add-int ]])

    (HelloWorld/invoke))


  (make-fn {:class-name "Inc"
            :args [:int]
            :return-type :int
            :body [[:int {:var-type :int :value 1}]
                   [:arg { :value 0}]
                   [:add-int]]})

  (Inc/invoke 1)

  (make-fn {:class-name "StrConcat"
            :args [:string]
            :return-type :string
            :body [[:string {:value "Hello "}]
                   [:arg {:value 0}]
                   [:interop-call [:java.lang.String/concat [ :string] :string]]]})

  (make-fn {:class-name "StrConcat2"
            :args [:string]
            :return-type :string
            :body [[:string {:value "Hello "}]
                   [:arg {:value 0}]
                   [:interop-call [:java.lang.String/concat [[:class "java.lang.String"]] :string]]]})

  (StrConcat/invoke "Johnny")


  (make-fn {:class-name "CallMethod"
            :args [:int]
            :return-type :int
            :body [[:string {:value "World!"}]
                   [:call-fn [:SayHi/invoke [:string] :string]]
                   [:print {:args [:string]}]
                   [:arg {:value 0}]
                   [:print]
                   [:arg { :value 0}]
                   [:call-fn [:Inc/invoke [:int] :int]]]})



  (CallMethod/invoke 121)

  (make-fn {:args [:int]
            :return-type :int
            ;; f(x) = 2*x^2 -13 * x + 119
            :body [[:int { :value 119}] ;; 119
                   [:arg {:value 0}]
                   [:def-local {:var-id "x" :var-type :int}]
                   [:ref-local {:var-id "x"}]
                   [:int {:var-type :int :value 13}]
                   [:mul-int] ;; 13*x
                   [:sub-int] ;; -13*x + 119
                   [:int { :value 2}]
                   [:ref-local {:var-id "x"}]
                   [:ref-local {:var-id "x"}]
                   [:mul-int] ;; x^2
                   [:mul-int] ;; 2 * x^2
                   [:add-int] ;;2*x^2 -13*x +119
                   ]
            :class-name "DoMath"})

  (DoMath/invoke 102))
