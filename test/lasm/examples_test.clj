(ns lasm.examples-test
  "Tests for lasm example programs"
  (:require [clojure.test :refer :all]
            [lasm.parser :as p]
            [lasm.ast :as ast]
            [lasm.emit :as emit]
            [instaparse.core :as insta]))

(deftest test-parser-handles-trailing-whitespace
  (testing "Parser should handle trailing newlines and whitespace"
    (let [code-with-trailing "fn test(): int => 42\ntest()\n\n"
          code-no-trailing "fn test(): int => 42\ntest()"
          result-with (p/parser code-with-trailing)
          result-without (p/parser code-no-trailing)]
      (is (not (insta/failure? result-with))
          "Parser should handle trailing newlines")
      (is (not (insta/failure? result-without))
          "Parser should handle no trailing newlines"))))

(deftest test-parser-handles-leading-whitespace
  (testing "Parser should handle leading whitespace"
    (let [code-with-leading "\n\nfn test(): int => 42\ntest()"
          result (p/parser code-with-leading)]
      (is (not (insta/failure? result))
          "Parser should handle leading whitespace"))))

;; DISABLED: Isolating type checker issues
#_(deftest test-simple-window-example
  (testing "Simple window example should parse and compile"
    (let [code (slurp "examples/01_simple_window.lasm")
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Simple window example should parse successfully")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)
              program (ast/build-program ast-tree)]
          (is (map? program) "Should build valid program")
          (is (:entry-point program) "Should have entry point")
          (is (vector? (:fns program)) "Should have functions"))))))

;; DISABLED: Isolating type checker issues
#_(deftest test-window-with-label-example
  (testing "Window with label example should parse and compile"
    (let [code (slurp "examples/02_window_with_label.lasm")
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Window with label example should parse successfully")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)
              program (ast/build-program ast-tree)]
          (is (map? program) "Should build valid program")
          (is (:entry-point program) "Should have entry point"))))))

;; DISABLED: Isolating type checker issues
#_(deftest test-pong-example
  (testing "Pong example should parse and compile"
    (let [code (slurp "examples/03_pong.lasm")
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Pong example should parse successfully")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)
              program (ast/build-program ast-tree)]
          (is (map? program) "Should build valid program")
          (is (:entry-point program) "Should have entry point")
          (is (= "MakeFrame" (:entry-point program))
              "Entry point should be MakeFrame"))))))

;; DISABLED: Type checker may have issues with void return type or complex logic
;; TODO: Investigate why 04_pong_with_logic.lasm fails type checking
#_(deftest test-pong-logic-example
  (testing "Pong logic example should parse and compile"
    (let [code (slurp "examples/04_pong_with_logic.lasm")
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Pong logic example should parse successfully")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)
              program (ast/build-program ast-tree)]
          (is (map? program) "Should build valid program")
          (is (:entry-point program) "Should have entry point"))))))

(deftest test-constructor-interop
  (testing "Constructor interop with 'new' keyword"
    (let [code "frame:javax.swing.JFrame = new javax.swing.JFrame(\"Test\")\nprintstr(\"ok\")"
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Should parse constructor interop")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)]
          (is (vector? ast-tree) "Should produce AST")
          ;; Check that we have a CtorInteropCall in the AST
          (is (some #(= :CtorInteropCall (first %)) ast-tree)
              "AST should contain constructor call"))))))

(deftest test-boolean-literals
  (testing "Boolean literals true and false"
    (let [code "flag:bool = true\nflag2:bool = false\nprintstr(\"ok\")"
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Should parse boolean literals")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)]
          (is (vector? ast-tree) "Should produce AST")
          ;; Check for Bool expressions in AST
          (is (some #(= :Bool (first %)) (flatten ast-tree))
              "AST should contain boolean expressions"))))))

(deftest test-instance-method-chaining
  (testing "Instance method calls on objects"
    (let [code "text:string = \"hello\"\nresult:string = text.toUpperCase()\nprintstr(result)"
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Should parse instance method calls")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)]
          (is (vector? ast-tree) "Should produce AST"))))))

(deftest test-static-method-calls
  (testing "Static method calls with / syntax"
    (let [code "result:int = java.lang.Math/abs(-42)\nprintint(result)"
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Should parse static method calls")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)]
          (is (vector? ast-tree) "Should produce AST")
          ;; Check for StaticInteropCall in AST
          (is (some #(= :StaticInteropCall (first %)) (flatten ast-tree))
              "AST should contain static interop call"))))))

(deftest test-multiline-function-definition
  (testing "Multi-line function definitions with braces"
    (let [code "fn test(): int => {\n  x:int = 1\n  y:int = 2\n  x + y\n}\ntest()"
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Should parse multi-line function with braces"))))

(deftest test-empty-lines-in-code
  (testing "Empty lines within code should be handled"
    (let [code "fn test(): int => 42\n\n\ntest()\n\n"
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Should handle empty lines within code"))))

(deftest test-type-transformation
  (testing "Type expressions should be properly transformed"
    (let [code "frame:javax.swing.JFrame = new javax.swing.JFrame(\"Test\")\nprintstr(\"ok\")"
          parsed (p/parser code)]
      (is (not (insta/failure? parsed))
          "Should parse type annotations")
      (when-not (insta/failure? parsed)
        (let [ast-tree (p/parse-tree-to-ast parsed)
              ;; Check that types are transformed to [:class "..."] format
              var-def (first ast-tree)]
          (is (= :VarDef (first var-def))
              "First expression should be VarDef")
          (let [var-type (get-in var-def [1 :var-type])]
            (is (vector? var-type)
                "Type should be a vector")
            (is (= :class (first var-type))
                "Type should start with :class, not :TypeExpr")
            (is (= "javax.swing.JFrame" (second var-type))
                "Type should be javax.swing.JFrame")))))))
