# lasm

A small functional language that compiles to JVM bytecode

## Overview

Lasm is a statically-typed functional programming language that compiles directly to JVM bytecode. It features seamless Java interoperability, strong typing, and a clean, expression-based syntax.

## Features

- 🔧 **JVM Bytecode Compilation** - Compiles directly to bytecode using ASM
- ☕ **Java Interop** - Call any Java library, create objects, invoke methods
- 🎯 **Strong Typing** - Static type checking with type inference
- 🔄 **Functional** - Immutable variables, expression-based, pure functions
- 📦 **Simple Syntax** - Clean and readable syntax inspired by modern functional languages

## Quick Examples

### Hello World
```lasm
printstr("Hello World!")
```

### Functions
```lasm
fn factorial(n: int): int => {
    if n <= 1
        1
    else
        n * factorial(n - 1)
}

printint(factorial(5))
```

### Java Swing GUI
```lasm
frame:javax.swing.JFrame = new javax.swing.JFrame("Hello Lasm")
label:javax.swing.JLabel = new javax.swing.JLabel("Hello World!")
container:java.awt.Container = frame.getContentPane()
container.add(label)
frame.pack()
frame.setVisible(true)
```

## Examples

Check out the [`examples/`](examples/) directory for complete examples including:
- Simple window creation
- GUI applications with Swing
- **Pong game demo** - A complete game showcasing the language
- Game logic and physics simulation

See [examples/README.md](examples/README.md) for detailed documentation.

## Language Syntax

### Variables
```lasm
x: int = 42
name: string = "Alice"
flag: bool = true
```

### Functions
```lasm
fn add(x: int, y: int): int => x + y

fn greet(name: string): string => {
    "Hello, ".concat(name)
}
```

### Conditionals
```lasm
fn max(a: int, b: int): int => {
    if a > b
        a
    else
        b
}
```

### Java Interop

**Create objects:**
```lasm
frame: javax.swing.JFrame = new javax.swing.JFrame("Title")
```

**Instance methods:**
```lasm
result: string = text.toUpperCase()
```

**Static methods:**
```lasm
absValue: int = java.lang.Math/abs(-42)
```

## Project Structure

```
lasm/
├── src/lasm/
│   ├── parser.clj      # Parser using Instaparse
│   ├── ast.clj         # AST transformations and IR generation
│   ├── emit.clj        # JVM bytecode emission using ASM
│   ├── type-checker.clj # Type checking and inference
│   └── decompiler.clj  # Debug utilities
├── examples/           # Example lasm programs
│   ├── 01_simple_window.lasm
│   ├── 02_window_with_label.lasm
│   ├── 03_pong.lasm
│   ├── 04_pong_with_logic.lasm
│   └── README.md
└── README.md
```

## How It Works

1. **Parse** - Source code is parsed into a parse tree using Instaparse
2. **AST** - Parse tree is transformed into an Abstract Syntax Tree
3. **Type Check** - Types are inferred and checked for correctness
4. **IR** - AST is lowered to an intermediate representation
5. **Emit** - IR is compiled to JVM bytecode using the ASM library
6. **Load** - Bytecode is loaded into the JVM and executed

## Development

### Running Examples
```bash
# Using Clojure REPL
clojure -M -m lasm.parser

# Then in the REPL, run examples from parser.clj comments
```

### Running from Source
```clojure
(require '[lasm.parser :as parser]
         '[lasm.ast :as ast]
         '[lasm.emit :as emitter])

(-> "fn add(x: int, y: int): int => x + y
     printint(add(5, 3))"
    parser/parser
    parser/parse-tree-to-ast
    ast/build-program
    emitter/emit-and-run!)
```

## Technical Details

### Type System
- Primitives: `int`, `bool`, `string`, `void`
- Java classes: Full Java type system available
- Type inference for local variables
- No implicit conversions

### Compilation Strategy
- Functions compile to static methods on Java classes
- Each function gets its own class
- Main entry point is generated automatically
- No garbage collection overhead (uses JVM GC)

### Bytecode Generation
- Uses ASM library for bytecode generation
- Generates Java 8 compatible bytecode
- Supports tail call optimization (where possible)
- Stack-based VM instructions

## Contributing

Contributions are welcome! Areas for improvement:
- More language features (arrays, structs, pattern matching)
- Better error messages
- Standard library
- Package system
- More examples

## License

See [LICENSE](LICENSE) file.
