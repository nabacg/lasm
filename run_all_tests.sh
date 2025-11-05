#!/bin/bash
# Local test runner - runs the same tests as CI

set -e

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║           LASM Test Suite (Local Runner)                 ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to run a test step
run_step() {
    local step_name="$1"
    local step_num="$2"
    echo ""
    echo "─────────────────────────────────────────────────────────────"
    echo "Step $step_num: $step_name"
    echo "─────────────────────────────────────────────────────────────"
}

# Check for clojure
if ! command -v clojure &> /dev/null; then
    echo -e "${RED}✗ Clojure CLI not found!${NC}"
    echo "Please install Clojure from: https://clojure.org/guides/install_clojure"
    exit 1
fi

# Step 1: Download dependencies
run_step "Download Dependencies" "1/6"
clojure -P -M:tests
echo -e "${GREEN}✓ Dependencies downloaded${NC}"

# Step 2: Run unit tests
run_step "Run Unit Tests (clojure.test)" "2/6"
if clojure -M:tests; then
    echo -e "${GREEN}✓ Unit tests passed${NC}"
else
    echo -e "${RED}✗ Unit tests failed${NC}"
    exit 1
fi

# Step 3: Run manual test suite
run_step "Run Comprehensive Manual Test Suite" "3/6"
chmod +x manual_test_suite.clj
if clojure manual_test_suite.clj; then
    echo -e "${GREEN}✓ Manual test suite passed${NC}"
else
    echo -e "${RED}✗ Manual test suite failed${NC}"
    exit 1
fi

# Step 4: Test working examples
run_step "Test Working Examples (01-05)" "4/6"
clojure -M -e "
(require '[lasm.parser :as p]
         '[lasm.ast :as ast]
         '[instaparse.core :as insta])

(def working-examples [\"01_simple_window.lasm\"
                       \"02_window_with_label.lasm\"
                       \"03_pong.lasm\"
                       \"04_animated_pong.lasm\"
                       \"05_keyboard_test.lasm\"])

(doseq [filename working-examples]
  (let [file (clojure.java.io/file \"examples\" filename)
        code (slurp file)
        parsed (p/parser code)]
    (if (insta/failure? parsed)
      (do
        (println \"❌ FAILED:\" filename)
        (System/exit 1))
      (ast/build-program (p/parse-tree-to-ast parsed)))))

(println \"✓ All working examples passed\")
"
echo -e "${GREEN}✓ Working examples validated${NC}"

# Step 5: Test invalid examples
run_step "Test Invalid Examples (06-09)" "5/6"
clojure -M -e "
(require '[lasm.parser :as p]
         '[instaparse.core :as insta])

(def invalid-examples [\"06_pong_full_game.lasm\"
                       \"07_pong_text_mode.lasm\"
                       \"08_pong_working.lasm\"
                       \"09_pong_simple.lasm\"])

(doseq [filename invalid-examples]
  (let [file (clojure.java.io/file \"examples\" filename)]
    (when (.exists file)
      (let [code (slurp file)
            parsed (p/parser code)]
        (if-not (insta/failure? parsed)
          (do
            (println \"❌ Should have failed but didn't:\" filename)
            (System/exit 1)))))))

(println \"✓ Invalid examples correctly rejected\")
"
echo -e "${GREEN}✓ Invalid examples validated${NC}"

# Step 6: Test compilation
run_step "Test End-to-End Compilation" "6/6"
clojure -M -e "
(require '[lasm.parser :as p]
         '[lasm.ast :as ast]
         '[lasm.emit :as emit])

;; Test factorial
(let [result (-> \"fn fact(x:int): int =>
                    if x <= 1
                      1
                    else
                      x * fact(x - 1)
                  fact(5)\"
                 p/parser
                 p/parse-tree-to-ast
                 ast/build-program
                 emit/emit-and-run!)]
  (when (not= result 120)
    (println \"❌ Factorial failed, expected 120, got\" result)
    (System/exit 1)))

;; Test fibonacci
(let [result (-> \"fn fib(x:int): int =>
                    if x < 2
                      1
                    else
                      fib(x - 1) + fib(x - 2)
                  fib(10)\"
                 p/parser
                 p/parse-tree-to-ast
                 ast/build-program
                 emit/emit-and-run!)]
  (when (not= result 89)
    (println \"❌ Fibonacci failed, expected 89, got\" result)
    (System/exit 1)))

(println \"✓ End-to-end compilation passed\")
"
echo -e "${GREEN}✓ Compilation tests passed${NC}"

# Summary
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║              ✓✓✓ ALL TESTS PASSED! ✓✓✓                  ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo -e "${GREEN}✓ Unit tests${NC}"
echo -e "${GREEN}✓ Manual test suite${NC}"
echo -e "${GREEN}✓ Working examples (01-05)${NC}"
echo -e "${GREEN}✓ Invalid examples (06-09)${NC}"
echo -e "${GREEN}✓ Compilation tests${NC}"
echo ""
