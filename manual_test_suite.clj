#!/usr/bin/env clojure

;;; Manual Test Suite for LASM
;;; This tests LASM functionality without requiring external test frameworks

(require '[lasm.parser :as p]
         '[lasm.ast :as ast]
         '[lasm.emit :as emit]
         '[instaparse.core :as insta])

(def test-results (atom {:pass 0 :fail 0 :total 0}))

(defn test-assert [name condition & [message]]
  (swap! test-results update :total inc)
  (if condition
    (do
      (swap! test-results update :pass inc)
      (println (str "✓ " name)))
    (do
      (swap! test-results update :fail inc)
      (println (str "✗ " name))
      (when message
        (println (str "  Error: " message))))))

(defn test-example-file [name filepath expected-result]
  (try
    (let [code (slurp filepath)
          parsed (p/parser code)]
      (if (insta/failure? parsed)
        (if (= expected-result :should-fail)
          (test-assert (str name " - correctly fails to parse") true)
          (test-assert (str name " - parse") false
                      (str "Parse failed: " (pr-str parsed))))
        (if (= expected-result :should-fail)
          (test-assert (str name " - should have failed but didn't") false)
          (try
            (let [ast-tree (p/parse-tree-to-ast parsed)
                  program (ast/build-program ast-tree)]
              (test-assert (str name " - parse & build AST") true))
            (catch Exception e
              (test-assert (str name " - build AST") false
                          (.getMessage e)))))))
    (catch Exception e
      (test-assert (str name " - file read") false (.getMessage e)))))

(defn test-code-snippet [name code expected-result]
  (try
    (let [parsed (p/parser code)]
      (if (insta/failure? parsed)
        (if (= expected-result :should-fail)
          (test-assert name true)
          (test-assert name false (str "Parse failed")))
        (if (= expected-result :should-fail)
          (test-assert name false "Should have failed but didn't")
          (test-assert name true))))
    (catch Exception e
      (test-assert name false (.getMessage e)))))

(defn test-compile-and-run [name code expected-value]
  (try
    (let [result (-> (p/parser code)
                    p/parse-tree-to-ast
                    ast/build-program
                    emit/emit-and-run!)]
      (if (= result expected-value)
        (test-assert (str name " (expected " expected-value ")") true)
        (test-assert (str name " (expected " expected-value ")") false
                    (str "Got " result " instead"))))
    (catch Exception e
      (test-assert name false (.getMessage e)))))

(println "\n╔════════════════════════════════════════════════════════════╗")
(println "║           LASM COMPREHENSIVE TEST SUITE                  ║")
(println "╚════════════════════════════════════════════════════════════╝\n")

;;; ==================================================================
;;; BASIC PARSER TESTS
;;; ==================================================================

(println "─── Basic Expression Parsing ───\n")

(test-code-snippet "Parse integer literal" "42" :should-pass)
(test-code-snippet "Parse string literal" "\"hello\"" :should-pass)
(test-code-snippet "Parse boolean true" "true" :should-pass)
(test-code-snippet "Parse boolean false" "false" :should-pass)
(test-code-snippet "Parse variable" "x" :should-pass)
(test-code-snippet "Parse variable with type" "x:int = 42" :should-pass)

(println "\n─── Function Definition Parsing ───\n")

(test-code-snippet "Parse simple function"
  "fn test(): int => 42"
  :should-pass)

(test-code-snippet "Parse function with parameters"
  "fn add(x:int, y:int): int => x + y"
  :should-pass)

(test-code-snippet "Parse recursive function"
  "fn fib(n:int): int => if n < 2\n  1\nelse\n  fib(n-1) + fib(n-2)"
  :should-pass)

(println "\n─── If-Expression Parsing ───\n")

(test-code-snippet "Parse simple if-else"
  "if x < 5\n  1\nelse\n  2"
  :should-pass)

(test-code-snippet "Parse nested if-else"
  "if x < 5\n  if y > 10\n    1\n  else\n    2\nelse\n  3"
  :should-pass)

(test-code-snippet "If-block syntax should fail"
  "if x > 3 {\n  y:int = 10\n  y\n} else {\n  z:int = 20\n  z\n}"
  :should-fail)

(println "\n─── Java Interop Parsing ───\n")

(test-code-snippet "Parse constructor call"
  "frame:javax.swing.JFrame = new javax.swing.JFrame(\"Test\")"
  :should-pass)

(test-code-snippet "Parse instance method call"
  "result:string = text.toUpperCase()"
  :should-pass)

(test-code-snippet "Parse static method call"
  "result:int = java.lang.Math/abs(-42)"
  :should-pass)

(println "\n─── Proxy Expression Parsing ───\n")

(test-code-snippet "Parse single-method proxy"
  "listener:java.awt.event.ActionListener = proxy java.awt.event.ActionListener {\n  actionPerformed(e:java.awt.event.ActionEvent): void => { printstr(\"clicked\") }\n}"
  :should-pass)

(test-code-snippet "Parse 3-method proxy (KeyListener)"
  "listener:java.awt.event.KeyListener = proxy java.awt.event.KeyListener {\n  keyPressed(e:java.awt.event.KeyEvent): void => { printstr(\"pressed\") }\n  keyReleased(e:java.awt.event.KeyEvent): void => { printstr(\"released\") }\n  keyTyped(e:java.awt.event.KeyEvent): void => { printstr(\"typed\") }\n}"
  :should-pass)

;;; ==================================================================
;;; EXAMPLE FILE TESTS
;;; ==================================================================

(println "\n─── Example Files ───\n")

(test-example-file "Example 01: Simple Window"
  "examples/01_simple_window.lasm"
  :should-pass)

(test-example-file "Example 02: Window with Label"
  "examples/02_window_with_label.lasm"
  :should-pass)

(test-example-file "Example 03: Basic Pong"
  "examples/03_pong.lasm"
  :should-pass)

(test-example-file "Example 04: Animated Pong"
  "examples/04_animated_pong.lasm"
  :should-pass)

(test-example-file "Example 05: Keyboard Test (3-method proxy)"
  "examples/05_keyboard_test.lasm"
  :should-pass)

(test-example-file "Example 06: Full Game (invalid syntax)"
  "examples/06_pong_full_game.lasm"
  :should-fail)

(test-example-file "Example 07: Text Mode (invalid syntax)"
  "examples/07_pong_text_mode.lasm"
  :should-fail)

(test-example-file "Example 08: Working Version (invalid syntax)"
  "examples/08_pong_working.lasm"
  :should-fail)

;;; ==================================================================
;;; END-TO-END COMPILATION TESTS
;;; ==================================================================

(println "\n─── End-to-End Compilation & Execution ───\n")

(test-compile-and-run "Simple addition"
  "fn add(x:int, y:int): int => x + y\nadd(2, 3)"
  5)

(test-compile-and-run "Factorial"
  "fn fact(x:int): int => if x <= 1\n  1\nelse\n  x * fact(x - 1)\nfact(5)"
  120)

(test-compile-and-run "Fibonacci"
  "fn fib(x:int): int => if x < 2\n  1\nelse\n  fib(x - 1) + fib(x - 2)\nfib(10)"
  89)

(test-compile-and-run "String operations"
  "fn test(): string => {\n  s:string = \"hello\"\n  s.toUpperCase()\n}\ntest()"
  "HELLO")

(test-compile-and-run "Math static method"
  "java.lang.Math/abs(-42)"
  42)

;;; ==================================================================
;;; KNOWN LIMITATIONS TESTS
;;; ==================================================================

(println "\n─── Known Limitations (should fail) ───\n")

(test-code-snippet "While loops not supported"
  "while i < 10 { i = i + 1 }"
  :should-fail)

(test-code-snippet "For loops not supported"
  "for i in range(10) { printint(i) }"
  :should-fail)

(test-code-snippet "Variable reassignment not supported"
  "x:int = 5\nx = 10"
  :should-fail)

;;; ==================================================================
;;; RESULTS SUMMARY
;;; ==================================================================

(println "\n╔════════════════════════════════════════════════════════════╗")
(println "║                    TEST RESULTS                           ║")
(println "╚════════════════════════════════════════════════════════════╝\n")

(let [{:keys [pass fail total]} @test-results
      pass-rate (if (pos? total) (int (* 100 (/ pass total))) 0)]
  (println (format "Total Tests:  %d" total))
  (println (format "Passed:       %d (%.0f%%)" pass pass-rate))
  (println (format "Failed:       %d" fail))
  (println "")

  (if (zero? fail)
    (do
      (println "╔════════════════════════════════════════════════════════════╗")
      (println "║              ✓✓✓ ALL TESTS PASSED! ✓✓✓                  ║")
      (println "╚════════════════════════════════════════════════════════════╝")
      (System/exit 0))
    (do
      (println "╔════════════════════════════════════════════════════════════╗")
      (println "║               ✗✗✗ SOME TESTS FAILED ✗✗✗                 ║")
      (println "╚════════════════════════════════════════════════════════════╝")
      (System/exit 1))))
