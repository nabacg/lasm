# How to Run the LASM Test Suite

## Quick Summary

**On GitHub (Automatic)**: Tests run automatically on every push
**Locally**: Use the provided script

---

## Method 1: View GitHub Actions CI (Easiest)

The CI runs automatically whenever you push to GitHub.

### View Results:

1. **Go to the Actions page**:
   ```
   https://github.com/nabacg/lasm/actions
   ```

2. **Find your branch**:
   - Look for workflow runs on `claude/wip-pong-demo-011CUpXxyCnPkAQowgjSdJcv`
   - Click on the most recent run

3. **View detailed logs**:
   - Click on "Run Lasm Tests"
   - Expand each step to see output
   - Green ✓ = pass, Red ✗ = fail

### Trigger CI Manually:

```bash
# Create an empty commit to trigger CI
git commit --allow-empty -m "Trigger CI"
git push
```

Then check GitHub Actions page after a few seconds.

---

## Method 2: Run All Tests Locally

If you have Clojure installed locally and want to run tests without waiting for CI:

### Prerequisites:

1. **Install Clojure CLI**:
   - macOS: `brew install clojure/tools/clojure`
   - Linux: https://clojure.org/guides/install_clojure
   - Windows: https://clojure.org/guides/install_clojure

2. **Install Java 11+**:
   ```bash
   java -version  # Should show 11 or higher
   ```

### Run All Tests:

```bash
# Simple - run everything
./run_all_tests.sh
```

This runs the same tests as CI:
- ✓ Unit tests (clojure.test)
- ✓ Manual test suite (40+ tests)
- ✓ Working examples (01-05)
- ✓ Invalid examples (06-09)
- ✓ End-to-end compilation

### Run Individual Test Suites:

```bash
# Just unit tests
clojure -M:tests

# Just manual test suite
clojure manual_test_suite.clj

# Just check examples parse
clojure -M -e "
(require '[lasm.parser :as p]
         '[lasm.ast :as ast]
         '[instaparse.core :as insta])

(doseq [file [\"examples/01_simple_window.lasm\"
              \"examples/05_keyboard_test.lasm\"]]
  (let [code (slurp file)
        parsed (p/parser code)]
    (if (insta/failure? parsed)
      (println \"FAILED:\" file)
      (do
        (ast/build-program (p/parse-tree-to-ast parsed))
        (println \"OK:\" file)))))
"
```

---

## Method 3: Quick Smoke Test

Just want to verify examples parse?

```bash
./smoke_test.clj
```

This quickly tests all example files (01-09) and reports which ones parse.

---

## Understanding Test Results

### Expected Results:

**Should Pass** ✅:
- Examples 01-05 (valid LASM syntax)
- Parser tests (all language features)
- Compilation tests (factorial, fibonacci, strings)

**Should Fail** ❌ (expected):
- Examples 06-09 (use invalid `if { statements }` syntax)
- Known limitation tests (loops, reassignment)

### What to Look For:

1. **All unit tests pass** - Parser, AST, examples work
2. **Examples 01-05 compile** - Valid syntax examples
3. **Examples 06-09 fail to parse** - Invalid syntax correctly rejected
4. **3-method proxy works** - Parser fix verified
5. **E2E compilation works** - Factorial=120, Fib=89, "HELLO"

---

## Troubleshooting

### "Clojure command not found"

Install Clojure CLI tools:
- https://clojure.org/guides/install_clojure

### "Could not locate instaparse..."

Download dependencies first:
```bash
clojure -P -M:tests
```

### Tests fail locally but pass in CI

Network proxy issue - the CI has full internet access. Either:
1. Fix your network proxy settings
2. Just use GitHub Actions (recommended)

### Want to see verbose output?

Add `println` statements or use the manual test suite which shows detailed output.

---

## CI Configuration Files

- `.github/workflows/test.yml` - GitHub Actions workflow
- `deps.edn` - Clojure dependencies
- `manual_test_suite.clj` - Standalone test runner
- `test/lasm/comprehensive_test.clj` - Full test suite

---

## Next Steps After Tests Pass

Once all tests are green:
1. ✅ Parser fix verified working
2. ✅ Examples validated
3. 🎯 Ready to continue development
4. 🎯 Can work on TODO.md tasks with confidence

See `TODO.md` for next features to implement.
