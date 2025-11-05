# LASM TODO List

Priority tasks for improving the language, organized by theme. Each task should be small, testable, and verified via CI.

## 🔴 HIGH PRIORITY - Parser Fixes

### P1: Fix Static Method Call Parsing
- [ ] Issue: `java.lang.Math/abs(-42)` fails to parse
- [ ] Task: Debug why static method calls fail
- [ ] Test: Re-enable `test-static-method-calls` in examples_test.clj
- [ ] Verification: Test should pass in CI

### P2: Fix Proxy Syntax Parsing
- [ ] Issue: Proxy expressions with braces fail to parse
- [ ] Task: Investigate why `proxy Interface { method(...): type => { body } }` fails
- [ ] Test: Re-enable `test-proxy-simple` in comprehensive_test.clj
- [ ] Verification: Test should pass in CI

### P3: Support Standalone Expressions at Top Level
- [ ] Issue: Cannot parse `"42"` or `"hello"` as standalone programs
- [ ] Task: Decide if standalone expressions should be valid TopLevelExpr
- [ ] Options:
  - A) Keep as-is (requires wrapping in function/var)
  - B) Add standalone expr support to grammar
- [ ] Test: Update `test-basic-expressions` based on decision
- [ ] Verification: Document design decision

## 🟡 MEDIUM PRIORITY - Type Checker Improvements

### P4: Add Recursive Function Support
- [ ] Issue: Type checker can't handle `fn fib(n) => ... fib(n-1)`
- [ ] Task: Add function to environment before checking body
- [ ] Test: Re-enable `test-compile-and-run-factorial`
- [ ] Test: Re-enable `test-compile-and-run-fibonacci`
- [ ] Verification: Both tests pass in CI

### P5: Fix Void Return Type Handling
- [ ] Issue: Functions with `void` return may have type checker issues
- [ ] Task: Investigate 04_pong_with_logic.lasm failure
- [ ] Test: Re-enable `test-pong-logic-example`
- [ ] Verification: Example compiles successfully

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

## 📈 Current Stats

- ✅ **14 tests passing**
- ⚠️  **~35 tests disabled** (waiting on fixes)
- 🎯 **Target: 50+ tests passing**

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
