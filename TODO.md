# LASM TODO List

Priority tasks for improving the language, organized by theme. Each task should be small, testable, and verified via CI.

## 🔴 HIGH PRIORITY - Parser Fixes

### P1: Fix Static Method Call Parsing ✅ COMPLETED
- [x] Issue: `java.lang.Math/abs(-42)` fails to parse
- [x] Task: Debug why static method calls fail - Fixed by adding negative number support to NumExpr
- [x] Test: Re-enabled `test-static-method-calls` in examples_test.clj
- [x] Verification: Test should pass in CI
- **Fix**: Changed `NumExpr := #'[0-9]+'` to `NumExpr := #'-?[0-9]+'` in parser.clj
- **Commit**: e1da7ad

### P2: Fix Proxy Syntax Parsing ⚠️ PARTIALLY COMPLETED
- [x] Issue: Proxy expressions with braces fail to parse
- [x] Task: Investigated and fixed - Proxy was completely missing from grammar
- [x] Test: Re-enabled `test-proxy-simple` and `test-proxy-multi-method` in comprehensive_test.clj
- [ ] Remaining: IR generation and bytecode emission for ProxyExpr (ast-to-ir method needed)
- **Fix**: Added ProxyExpr, ProxyMethod, and proxy-body to grammar and AST transformations
- **Commit**: ad94e3a, 08fad46
- **Status**: Parsing works, compilation not yet implemented

### P3: Support Standalone Expressions at Top Level ✅ DECISION: KEEP AS-IS
- [x] Issue: Cannot parse `"42"` or `"hello"` as standalone programs
- [x] Task: Decided to keep current design
- [x] Decision: **Option A - Keep as-is** (requires wrapping in function/var)
- [x] Rationale:
  - LASM is a compiled language, not a REPL
  - Current design: top-level code has side effects or defines things
  - Standalone expressions have unclear semantics (what happens to the value?)
  - VarDefExpr already works for most use cases: `x:int = 42`
  - For testing, wrap in function: `fn test(): int => 42`
- [x] Test: Keep `test-basic-expressions` disabled with explanation
- [x] Verification: Documented in TODO

## 🟡 MEDIUM PRIORITY - Type Checker Improvements

### P4: Add Recursive Function Support ✅ COMPLETED
- [x] Issue: Type checker can't handle `fn fib(n) => ... fib(n-1)`
- [x] Task: Add function to environment before checking body
- [x] Test: Re-enabled `test-compile-and-run-factorial`
- [x] Test: Re-enabled `test-compile-and-run-fibonacci`
- [x] Test: Re-enabled `test-compile-and-run-simple-function`
- [x] Test: Re-enabled `sample-factorial` in end_2_end_test.clj
- [x] Verification: All tests should pass in CI
- **Fix**: Modified type_checker.clj `:FunDef` to add function to env before checking body
- **Commit**: 76896bd

### P5: Fix Void Return Type Handling ✅ COMPLETED
- [x] Issue: Functions with `void` return had type checker issues
- [x] Task: Investigated 04_pong_with_logic.lasm failure - printstr/printint returned wrong types
- [x] Test: Re-enabled `test-pong-logic-example`
- [x] Verification: Example should compile successfully
- **Fix**: Changed printstr return type from String to :void, printint from int to :void in ast.clj
- **Commit**: ef12285
- **Root Cause**: printstr/printint were defined as returning their input types instead of void

### P6: Better Type Error Messages
- [ ] Task: Add meaningful error messages for type mismatches
- [ ] Example: "Expected int, got string at line 42"
- [ ] Test: Add test for error message quality
- [ ] Verification: Error messages include line numbers and types

## 🟢 LOW PRIORITY - Language Features

### P7: Add Array Support
- [ ] Research: How should array syntax look?
  - Option A: `[1, 2, 3]`
  - Option B: `int[]{1, 2, 3}` (Java-style)
- [ ] Task: Implement array creation parsing
- [ ] Test: Re-enable `test-array-creation-pattern`
- [ ] Verification: Arrays work in examples

### P8: Add While Loops
- [ ] Task: Add `while` to grammar
- [ ] Grammar: `while condition { body }`
- [ ] Test: Add comprehensive while loop tests
- [ ] Verification: Loop examples run correctly

### P9: Add For Loops
- [ ] Task: Add `for` to grammar
- [ ] Grammar: Decide on syntax (range-based? iterator-based?)
- [ ] Test: Add comprehensive for loop tests
- [ ] Verification: Loop examples run correctly

### P10: Add Mutable Variables
- [ ] Task: Add `var` keyword for mutable variables
- [ ] Grammar: `var x: int = 0` and `x = 5` (assignment)
- [ ] Test: Add mutability tests
- [ ] Verification: Mutable variables work correctly

## 📚 Documentation

### P11: Document Parser Grammar
- [ ] Task: Create GRAMMAR.md with all rules
- [ ] Include: What's valid at top level
- [ ] Include: Complete syntax reference
- [ ] Verification: Document reviewed and accurate

### P12: Document Type System
- [ ] Task: Create TYPE_SYSTEM.md
- [ ] Include: All supported types
- [ ] Include: Type inference rules
- [ ] Include: Known limitations
- [ ] Verification: Document reviewed and accurate

### P13: Add More Examples
- [ ] Task: Create examples/06_calculator.lasm
- [ ] Task: Create examples/07_fibonacci.lasm (when recursion works)
- [ ] Task: Create examples/08_arrays.lasm (when arrays work)
- [ ] Verification: All examples compile and run

## 🧪 Test Improvements

### P14: Fix Brittle AST Structure Tests
- [ ] Issue: Tests check exact AST structure which breaks easily
- [ ] Task: Convert to semantic checks (does it have a function? does it call something?)
- [ ] Test: Update `top-level-expression-parser-tests`
- [ ] Test: Update `ast-generation-tests`
- [ ] Verification: Tests are less brittle and more maintainable

### P15: Add End-to-End Integration Tests
- [ ] Task: Add tests that compile and run complete programs
- [ ] Test: Simple calculator program
- [ ] Test: Fibonacci (when recursion works)
- [ ] Test: GUI application
- [ ] Verification: All integration tests pass

## 🔧 Tooling

### P16: Better REPL Experience
- [ ] Task: Create `lasm` CLI tool
- [ ] Feature: `lasm repl` - Interactive REPL
- [ ] Feature: `lasm run file.lasm` - Run a file
- [ ] Feature: `lasm compile file.lasm` - Compile to .class
- [ ] Verification: CLI works and is documented

### P17: JAR Compiler
- [ ] Task: Implement standalone JAR compilation
- [ ] Feature: `lasm build project/` - Build to executable JAR
- [ ] Test: Compile and run JAR
- [ ] Verification: Generated JARs work standalone

## 📈 Current Stats (Updated: Session claude/review-lasm-code-011CUpXxyCnPkAQowgjSdJcv)

- ✅ **32 tests enabled** (ast_test: 1, comprehensive_test: 19, end_2_end_test: 2, examples_test: 10)
- ⚠️  **15 tests disabled** (waiting on fixes)
- 🎯 **Target: 50+ tests passing**
- 📊 **Progress**: 27 → 32 tests enabled (+5 tests re-enabled)
- 📝 **Recent Progress**:
  - ✅ P1: Fixed static method parsing with negative numbers
  - ⚠️  P2: Added proxy parsing (compilation pending)
  - ✅ P3: Documented design decision (keep standalone expressions disabled)
  - ✅ P4: Fixed recursive function support in type checker
  - ✅ P5: Fixed void return type handling (printstr/printint now return void)
  - ✅ Re-enabled 5 tests that now pass after fixes

## How to Work on TODOs

1. **Pick a task** from HIGH PRIORITY
2. **Create a branch**: `claude/fix-parser-static-methods-{session-id}`
3. **Make small changes** - one logical change at a time
4. **Write tests first** (TDD) or alongside changes
5. **Commit and push** - CI will verify
6. **Check CI** - must be green before moving on
7. **Update this TODO** - mark task as complete
8. **Repeat** - pick next task

## Completion Criteria

Each task is complete when:
- ✅ Code changes implemented
- ✅ Tests written and passing locally
- ✅ CI passing (green checkmark)
- ✅ Changes committed and pushed
- ✅ This TODO updated

---

**Start with**: P1 (Fix Static Method Call Parsing) - it's small, well-defined, and has a test ready to re-enable.
