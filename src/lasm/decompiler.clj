(ns lasm.decompiler
  (:import [com.strobel.assembler.metadata ArrayTypeLoader]
           [com.strobel.decompiler.languages Languages]
           [com.strobel.decompiler.languages Language]
           [com.strobel.assembler.metadata DeobfuscationUtilities MetadataSystem TypeReference]
           [com.strobel.decompiler DecompilationOptions
            DecompilerSettings
            PlainTextOutput]
           [org.objectweb.asm ClassWriter]))

(def bytecode-decompiler (Languages/bytecode))
(def java-decompiler (Languages/java))

(defn decompile [decompiler byte-array class-name]
  (let [output (PlainTextOutput.)
        decomp-options (doto (DecompilationOptions.)
                         (.setSettings (doto (DecompilerSettings.)
                                         (.setSimplifyMemberReferences true))))]
    (.decompileType ^Language decompiler
                    (doto (.resolve (.lookupType (MetadataSystem. (ArrayTypeLoader. byte-array)) class-name))
                      (DeobfuscationUtilities/processType))
                    output
                    decomp-options)
    (println (str output))))

(defn to-java [byte-array class-name]
  (decompile java-decompiler byte-array class-name))

(defn to-bytecode [byte-array class-name]
   (decompile bytecode-decompiler byte-array class-name))



#_(defn print-and-load-bytecode [writer class-name]
  (let [byteArray (.toByteArray ^ClassWriter writer)]
    (lasm.decompile/to-bytecode byteArray class-name)
    (.defineClass ^clojure.lang.DynamicClassLoader
                  (clojure.lang.DynamicClassLoader.)
                  (.replace ^String class-name \/ \.)
                  byteArray
                  nil)))
#_
(defn print-and-load-java [writer class-name]
  (let [byteArray (.toByteArray ^ClassWriter writer)]
    (lasm.decompile/to-java byteArray class-name)
    (.defineClass ^clojure.lang.DynamicClassLoader
                  (clojure.lang.DynamicClassLoader.)
                  (.replace ^String class-name \/ \.)
                  byteArray
                  nil)))
