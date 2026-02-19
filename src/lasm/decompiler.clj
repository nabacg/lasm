(ns lasm.decompiler
  (:import [org.benf.cfr.reader.api CfrDriver$Builder
            ClassFileSource OutputSinkFactory
            OutputSinkFactory$Sink
            OutputSinkFactory$SinkType OutputSinkFactory$SinkClass
            SinkReturns$Decompiled]
           [org.benf.cfr.reader.bytecode.analysis.parse.utils Pair]
           [org.objectweb.asm ClassWriter]))

(defn- make-class-source
  "Create a ClassFileSource that serves a single class from a byte array."
  [^bytes byte-array ^String class-name]
  (let [dot-name (.replace class-name \/ \.)]
    (reify ClassFileSource
      (addJar [_ _jar-path])
      (getPossiblyRenamedPath [_ path] path)
      (getClassFileContent [_ path]
        (if (= path (str dot-name ".class"))
          (Pair. byte-array dot-name)
          ;; For JDK classes, load from system classloader
          (let [cls-path (-> ^String path
                             (.replace "." "/")
                             (str ".class")
                             ;; fix double .class
                             (.replace ".class.class" ".class"))
                stream (.getResourceAsStream (ClassLoader/getSystemClassLoader) cls-path)]
            (when stream
              (Pair. (.readAllBytes stream) path)))))
      (informAnalysisRelativePathDetail [_ _a _b]))))

(defn- make-output-sink
  "Create an OutputSinkFactory that captures decompiled output into an atom."
  [result-atom]
  (reify OutputSinkFactory
    (getSupportedSinks [_ _sink-type _available]
      java.util.Collections/EMPTY_LIST)
    (getSink [_ sink-type sink-class]
      (if (and (= sink-type OutputSinkFactory$SinkType/JAVA)
               (= sink-class OutputSinkFactory$SinkClass/DECOMPILED))
        (reify OutputSinkFactory$Sink
          (write [_ x]
            (let [^SinkReturns$Decompiled d x]
              (reset! result-atom (.getJava d)))))
        (reify OutputSinkFactory$Sink
          (write [_ _x]))))))

(defn decompile-to-java
  "Decompile a byte array to Java source. Returns a string."
  [^bytes byte-array ^String class-name]
  (let [result (atom nil)
        dot-name (.replace class-name \/ \.)
        driver (-> (CfrDriver$Builder.)
                   (.withClassFileSource (make-class-source byte-array class-name))
                   (.withOutputSink (make-output-sink result))
                   (.build))]
    (.analyse driver (java.util.Collections/singletonList (str dot-name ".class")))
    @result))

(defn decompile-to-bytecode
  "Decompile a byte array to bytecode listing. Returns a string."
  [^bytes byte-array ^String class-name]
  (let [result (atom nil)
        dot-name (.replace class-name \/ \.)
        driver (-> (CfrDriver$Builder.)
                   (.withClassFileSource (make-class-source byte-array class-name))
                   (.withOutputSink (make-output-sink result))
                   (.withOptions {"bytecodeMonitor" "true"})
                   (.build))]
    (.analyse driver (java.util.Collections/singletonList (str dot-name ".class")))
    @result))

;; Keep backward-compatible API matching the old decompiler
(defn to-java [byte-array class-name]
  (when-let [result (decompile-to-java byte-array class-name)]
    (println result)))

(defn to-bytecode [byte-array class-name]
  (when-let [result (decompile-to-java byte-array class-name)]
    (println result)))

(defn print-and-load-bytecode [writer class-name]
  (let [byteArray (.toByteArray ^ClassWriter writer)]
    (to-bytecode byteArray class-name)
    (.defineClass ^clojure.lang.DynamicClassLoader
                  (clojure.lang.DynamicClassLoader.)
                  (.replace ^String class-name \/ \.)
                  byteArray
                  nil)))

(defn print-and-load-java [writer class-name]
  (let [byteArray (.toByteArray ^ClassWriter writer)]
    (to-java byteArray class-name)
    (.defineClass ^clojure.lang.DynamicClassLoader
                  (clojure.lang.DynamicClassLoader.)
                  (.replace ^String class-name \/ \.)
                  byteArray
                  nil)))
