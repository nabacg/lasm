# Lasm Language Examples

This directory contains example programs written in the **lasm** language, demonstrating its capabilities from simple window creation to a Pong game demo.

## About Lasm

Lasm is a functional language that compiles to JVM bytecode. It features:

- **Strong type system** with type inference
- **Java interop** - seamless integration with Java libraries
- **Functional programming** - immutable variables, no loops (recursion instead)
- **Object creation** with `new` keyword
- **Boolean, integer, and string** primitive types
- **Custom types** via fully qualified Java class names

## Language Syntax

### Basic Types
```lasm
x: int = 42
name: string = "Hello"
flag: bool = true
```

### Functions
```lasm
fn add(x: int, y: int): int => {
    x + y
}

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

### Object Creation
```lasm
frame: javax.swing.JFrame = new javax.swing.JFrame("Title")
color: java.awt.Color = new java.awt.Color(255, 0, 0)
```

### Method Calls

**Instance methods:**
```lasm
result: string = text.toUpperCase()
frame.setVisible(true)
```

**Static methods:**
```lasm
absValue: int = java.lang.Math/abs(-42)
```

### Built-in Functions
```lasm
printstr("Hello World")  // Print string
printint(42)             // Print integer
```

## Examples

### 01_simple_window.lasm
The simplest possible Swing example - creates a window and displays it.

**Demonstrates:**
- Creating Java objects with `new`
- Method calls on objects
- Basic Java Swing interop

**To run:**
```bash
clojure -M -m run-example examples/01_simple_window.lasm
```

### 02_window_with_label.lasm
Creates a window with a text label inside.

**Demonstrates:**
- Multiple object creation
- Container management
- Component hierarchy

### 03_pong.lasm
A Pong game user interface with title and instructions.

**Demonstrates:**
- Function definitions and composition
- Complex object graphs
- Type annotations
- Panel and label creation
- Layout management

**Features:**
- Custom color creation (RGB values)
- Font customization
- Multiple components
- Proper initialization sequence

### 04_pong_with_logic.lasm
Game logic simulation showing ball physics and boundary detection.

**Demonstrates:**
- Conditional logic (if/else)
- Function composition
- Boolean operations
- Integer arithmetic
- Recursive-style computation
- Game state simulation

**Features:**
- Ball position calculations
- Velocity management
- Boundary collision detection
- Velocity reflection on collision

## Running the Examples

### Prerequisites
- Java 8 or higher
- Clojure CLI tools

### Setup
1. Install Clojure: https://clojure.org/guides/install_clojure
2. Clone the lasm repository
3. Navigate to the lasm directory

### Run an Example
```bash
# From the lasm root directory
clojure -M:run-example examples/01_simple_window.lasm
```

Or use the helper script:
```bash
cd examples
./run.sh 01_simple_window.lasm
```

## Language Features Showcased

### Type System
- Primitive types: `int`, `bool`, `string`, `void`
- Java class types: `javax.swing.JFrame`, `java.awt.Color`, etc.
- Type inference for local variables
- Return type annotations

### Functional Programming
- Pure functions (no side effects except I/O)
- Immutable variables
- Expression-based (everything returns a value)
- No loops - use recursion

### Java Interop
- **Constructor calls:** `new ClassName(args)`
- **Instance methods:** `object.method(args)`
- **Static methods:** `ClassName/method(args)`
- **All Java libraries available**

### Control Flow
- `if/else` expressions (not statements!)
- Pattern matching through conditionals
- Short-circuit evaluation

## Architecture Notes

### Why Functional?
Lasm embraces functional programming principles:
1. **Immutability** - Variables cannot be reassigned
2. **Expressions** - Everything returns a value
3. **Pure functions** - No hidden state
4. **Composition** - Build complex behavior from simple functions

### Limitations & Workarounds

**No loops:**
- Use recursion instead
- Use Java's iterator methods through interop

**No mutable state:**
- Java objects can have mutable state
- Pass state as function parameters

**No interface implementation:**
- Use Java helper classes for event listeners
- Or rely on Java 8+ functional interfaces

**No anonymous functions (yet):**
- Define named functions
- Use Java lambdas through interop if needed

## Building a Full Pong Game

To build a complete, playable Pong game, you would need:

1. **Game Loop** - Use Java Timer or recursion with Thread.sleep
2. **Custom Rendering** - Extend JPanel and override paintComponent
3. **Input Handling** - Implement KeyListener in Java, call lasm functions
4. **Game State** - Pass state through function parameters or use Java objects

### Recommended Architecture
```
Java Helper Classes:
- GamePanel extends JPanel (custom rendering)
- KeyHandler implements KeyListener (input)
- GameLoop (calls lasm functions each frame)

Lasm Functions:
- updateBallPosition(x, y, vx, vy): Position
- checkPaddleCollision(ball, paddle): bool
- calculateScore(state): int
- renderFrame(graphics, state): void
```

## Contributing

Feel free to add more examples! Some ideas:
- Calculator application
- Recursive algorithms (fibonacci, factorial)
- String manipulation utilities
- Data structure demonstrations
- More complex Swing UIs

## Resources

- [Lasm Source Code](https://github.com/yourusername/lasm)
- [Java Swing Tutorial](https://docs.oracle.com/javase/tutorial/uiswing/)
- [Functional Programming Principles](https://www.manning.com/books/functional-programming-in-java)

## License

See the main project LICENSE file.
