(ns lasm.emit
  (:require [clojure.string :as string]
            [clojure.pprint :as pprint])
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
    (Type/getType (Class/forName (second expr-type)))
    :else
    (case expr-type
      :string (Type/getType String)
      :object (Type/getType Object)
      :void Type/VOID_TYPE
      :int Type/INT_TYPE
      :long Type/LONG_TYPE
      :bool Type/BOOLEAN_TYPE
      nil (throw (ex-info "[resolve-type] NIL expr-type!"))
      (throw (ex-info "Unknown expr-type, resolve-type failed" expr-type)))))

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

(defn build-invoke-static [[ fn-name args return-type]]
  (let [owner-class  (namespace fn-name)
        method-name  (name fn-name)]
    [:invoke-static {:owner [:class owner-class]
                     :method (build-method method-name return-type args)}]))

(defn emit-print-int [^GeneratorAdapter ga]
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
                      (build-method "println" :void [:int]))))

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
    (.newInstance ga (:owner cmd))
    :store-local
    (.storeLocal ga (:local-ref cmd) (resolve-type (:local-type cmd)))
    :load-local
    (.loadLocal ga  (:local-ref cmd) (resolve-type (:local-type cmd)))
    :int
    (.push ga (int (:value cmd)))
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
    (emit-print-int ga)))

(defn emit-with-env [^GeneratorAdapter ga env [cmd-type cmd :as c]]
  (case cmd-type
    :def-local (let [{:keys [value var-type var-id]} cmd
                     var-handle (.newLocal ga (resolve-type var-type))]
                 (.storeLocal ga var-handle)
                 (assoc env var-id var-handle))
    :ref-local (let [var-handle (env (:var-id cmd))]
                 (.loadLocal ga var-handle)
                 env)
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


(defn make-fn [{:keys [class-name body] :as fn-definition}]
  (let [writer (ClassWriter. ClassWriter/COMPUTE_FRAMES)
      ;;  body   (linearize body)
        body   (if (= :return (first (last body))) body (conj body [:return]))]

    (println "make-fn=")
    (pprint/pprint body)
    (initialize-class writer class-name)

    (generate-default-ctor writer)
    (make-static-method writer "invoke" (assoc fn-definition :body body))
    (.visitEnd writer)
    (.defineClass ^clojure.lang.DynamicClassLoader
                  (clojure.lang.DynamicClassLoader.)
                  (.replace ^String class-name \/ \.)
                  (.toByteArray ^ClassWriter writer) nil)
    class-name))


(defn make-entry-point [class-name code]
  (make-fn {:class-name class-name
            :args []
            :return-type :int
            :body code}))


;; bril like syntax ?
;; https://capra.cs.cornell.edu/bril/lang/syntax.html
{:fns  {"Main" {:args []
                    :return-type :int
                    :body [[:int {:var-type :int, :value 23}]
                           [:def-local {:var-id "x" :var-type :int}]
                           [:int {:var-type :int, :value 119}]
                           [:int {:var-type :int :value 19}]
                           [:ref-local {:var-id "x"}]
                           [:add-int]]}
        "Inc" {:args [:int]
               :return-type :int
               :body [[:int {:var-type :int :value 1}]
                      [:arg { :value 0}]
                      [:add-int]]}
        "CallMethod" {:args [:int]
                      :return-type :int
                      :body [[:arg { :value 0}]
                             [:invoke-static {:owner (resolve-type [:class "Inc"])
                                              :method (build-method "invoke" :int [:int])}]]}}
 :entry-point "Main"}



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

  (Inc/invoke 89)


  (make-fn {:class-name "CallMethod"
            :args [:int]
            :return-type :int
            :body [[:arg { :value 0}]
                   [:print]
                   [:arg { :value 0}]
                   #_
                   [:invoke-static
                    {:owner  [:class  "Inc"]
                     :method (build-method "invoke" :int [:int])}]
                   [:call-fn [:Inc/invoke [:int] :int]]
                   ]})



  (CallMethod/invoke 21)


  )
