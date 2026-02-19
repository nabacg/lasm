# Getting Started with LASM

LASM is a small, statically-typed language that compiles to JVM bytecode. You write code that looks like a mix of Rust and TypeScript, and it produces standalone JAR files with zero runtime dependencies.

This tutorial walks through the basics: variables, functions, conditionals, Java interop, and a simple GUI.

## Setup

You need Clojure and Java installed. Clone the repo and you're ready to go:

```bash
git clone <repo-url>
cd lasm
```

Two ways to run lasm programs:

```bash
# Run directly
clj -M -m lasm.cli run myprogram.lasm

# Compile to standalone JAR
clj -M -m lasm.cli compile myprogram.lasm -o myprogram.jar
java -jar myprogram.jar
```

## Hello World

Create a file called `hello.lasm`:

```lasm
printstr("Hello, World!")
```

Run it:

```bash
clj -M -m lasm.cli run hello.lasm
```

That's it. `printstr` is a built-in that prints a string to stdout. There's also `printint` for integers.

## Variables

Variables require type annotations:

```lasm
x:int = 42
name:string = "Alice"
flag:bool = true
```

LASM has three primitive types (`int`, `string`, `bool`) plus full access to Java's type system via fully qualified class names:

```lasm
frame:javax.swing.JFrame = new javax.swing.JFrame("Hello")
```

## Functions

Functions use `fn`, with parameter types and a return type:

```lasm
fn add(x: int, y: int): int => x + y

printint(add(3, 5))
```

The `=>` separates the signature from the body. For multi-expression bodies, use braces:

```lasm
fn greet(name: string): string => {
  prefix:string = "Hello, "
  prefix.concat(name)
}

printstr(greet("World"))
```

The last expression in a block is the return value. There's no `return` keyword.

## Conditionals

If/else works as an expression (it returns a value):

```lasm
fn max(a: int, b: int): int =>
  if a > b
    a
  else
    b

printint(max(10, 20))
```

For multiple statements in a branch, use braces:

```lasm
fn clamp(x: int, lo: int, hi: int): int => {
  if x < lo {
    lo
  } else {
    if x > hi
      hi
    else
      x
  }
}
```

Both branches must have the same type. Comparison operators: `==`, `!=`, `<`, `>`, `<=`, `>=`.

## Recursion

LASM has no loops. Use recursion instead:

```lasm
fn factorial(n: int): int =>
  if n <= 1
    1
  else
    n * factorial(n - 1)

printint(factorial(10))
```

This is a deliberate design choice. If you need to iterate, write a recursive function:

```lasm
fn countdown(n: int): void => {
  if n > 0 {
    printint(n)
    countdown(n - 1)
  } else {
    printstr("done!")
  }
}

countdown(5)
```

## Java Interop

This is where LASM gets interesting. You have full access to any Java class.

**Create objects** with `new`:

```lasm
list:java.util.ArrayList = new java.util.ArrayList()
```

**Call instance methods** with dot notation:

```lasm
list.add("hello")
size:int = list.size()
```

**Call static methods** with `ClassName/methodName`:

```lasm
absVal:int = java.lang.Math/abs(0 - 42)
strVal:string = java.lang.String/valueOf(42)
```

**Access static fields**:

```lasm
intType:java.lang.Class = java.lang.Integer/TYPE
```

## A Simple GUI

Let's put it together with a Swing window:

```lasm
fn main(): int => {
  frame:javax.swing.JFrame = new javax.swing.JFrame("Hello LASM")
  label:javax.swing.JLabel = new javax.swing.JLabel("Hello, World!")

  bigFont:java.awt.Font = new java.awt.Font("SansSerif", 1, 32)
  label.setFont(bigFont)

  container:java.awt.Container = frame.getContentPane()
  container.add(label)

  frame.pack()
  frame.setDefaultCloseOperation(3)
  frame.setVisible(true)
  0
}

main()
```

Compile and run:

```bash
clj -M -m lasm.cli compile hello_gui.lasm -o hello_gui.jar
java -jar hello_gui.jar
```

You get a window with "Hello, World!" in large bold text. Every Java class is available -- Swing, AWT, `java.util`, `java.io`, reflection, everything.

## Event Handling with Proxies

To handle events, you implement Java interfaces using `proxy`:

```lasm
fn main(): int => {
  frame:javax.swing.JFrame = new javax.swing.JFrame("Click Demo")
  label:javax.swing.JLabel = new javax.swing.JLabel("Click me!")
  container:java.awt.Container = frame.getContentPane()
  container.add(label)

  listener:java.awt.event.ActionListener = proxy java.awt.event.ActionListener {
    actionPerformed(e:java.awt.event.ActionEvent): void => {
      label.setText("Clicked!")
    }
  }

  button:javax.swing.JButton = new javax.swing.JButton("Go")
  button.addActionListener(listener)
  container.add(button)

  frame.pack()
  frame.setDefaultCloseOperation(3)
  frame.setVisible(true)
  0
}

main()
```

The proxy captures `label` from the outer scope (closure capture). When the button is clicked, it updates the label text. This is the same mechanism used to build the Pong game and Game of Life demos.

## What's Next

You now know enough to read and understand the example programs in `examples/`:

- **Pong** (`examples/06_pong_full_game.lasm`) -- a full game with keyboard input, ball physics, and collision detection
- **Game of Life** (`examples/07_game_of_life.lasm`) -- Conway's simulation with recursive grid operations

See the [Pong tutorial](tutorial-pong.md) and [Game of Life tutorial](tutorial-game-of-life.md) for guided walkthroughs of building those programs.
