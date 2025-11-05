# How to Run the Lasm Examples

## Method 1: Using the Clojure REPL (Easiest)

Since you have the lasm project already set up, you can run examples directly from a Clojure REPL:

### Step 1: Start a REPL
```bash
cd /path/to/lasm
clj
```

### Step 2: Load and Run an Example
```clojure
(require '[lasm.parser :as parser]
         '[lasm.ast :as ast]
         '[lasm.emit :as emit])

;; Run the simple window example
(-> (slurp "examples/01_simple_window.lasm")
    parser/parser
    parser/parse-tree-to-ast
    ast/build-program
    emit/emit-and-run!)

;; Run the Pong game
(-> (slurp "examples/03_pong.lasm")
    parser/parser
    parser/parse-tree-to-ast
    ast/build-program
    emit/emit-and-run!)

;; Run the Pong logic demo (no GUI)
(-> (slurp "examples/04_pong_with_logic.lasm")
    parser/parser
    parser/parse-tree-to-ast
    ast/build-program
    emit/emit-and-run!)
```

### Step 3: Keep Window Open
For GUI examples, add this to prevent the window from closing:
```clojure
(Thread/sleep 60000)  ; Keep window open for 60 seconds
;; or
(Thread/sleep Long/MAX_VALUE)  ; Keep window open indefinitely
```

## Method 2: Using the Parser Comment Examples

The parser already has examples in comments! You can evaluate them directly:

```bash
clj
```

Then in the REPL:
```clojure
(require '[lasm.parser :as p]
         '[lasm.ast :as ast]
         '[lasm.emit :as emit])

;; This example is already in parser.clj comments (line 264-277)
(-> "fn MakeFrame(): int => {
       frame:javax.swing.JFrame = new javax.swing.JFrame(\"hello\")
       label:javax.swing.JLabel = new javax.swing.JLabel(\"Hello World\")
       container:java.awt.Container = frame.getContentPane()
       container.add(label)
       frame.pack()
       frame.setVisible(true)
       42
     }"
    p/parser
    p/parse-tree-to-ast
    ast/build-program
    emit/emit-and-run!)
```

## Method 3: Using a Helper Script

I've created `run_pong.clj` in the root directory:

```bash
clj run_pong.clj
```

Or for any example:
```bash
clj -e "(require '[lasm.parser :as p] '[lasm.ast :as ast] '[lasm.emit :as emit]) \
       (-> (slurp \"examples/03_pong.lasm\") \
           p/parser \
           p/parse-tree-to-ast \
           ast/build-program \
           emit/emit-and-run!)"
```

## Method 4: Running Tests

You can also verify the examples work by running the test suite:

```bash
./bin/run-tests
```

Or manually:
```bash
clj -M:tests
```

## Quick Test Commands

### Test Simple Window (no GUI in tests)
```bash
clj -e "(require '[lasm.parser :as p] '[lasm.ast :as ast]) \
       (-> (slurp \"examples/01_simple_window.lasm\") \
           p/parser \
           p/parse-tree-to-ast \
           ast/build-program \
           println)"
```

### Run Pong Logic (Console Output Only)
```bash
clj -e "(require '[lasm.parser :as p] '[lasm.ast :as ast] '[lasm.emit :as emit]) \
       (-> (slurp \"examples/04_pong_with_logic.lasm\") \
           p/parser \
           p/parse-tree-to-ast \
           ast/build-program \
           emit/emit-and-run!)"
```

## Troubleshooting

### "clj: command not found"
Install Clojure CLI tools: https://clojure.org/guides/install_clojure

### Window Opens and Immediately Closes
Add a sleep statement to keep the JVM running:
```clojure
(Thread/sleep 60000)  ; 60 seconds
```

### Parse Errors
Make sure you're using the latest version of the code - the examples require:
- Constructor syntax: `new ClassName(...)`
- Instance methods: `object.method(...)`
- Boolean literals: `true`, `false`

### Missing Dependencies
Run from the project root directory where `deps.edn` is located.

## Example Session

Here's a complete session running the Pong game:

```bash
$ cd lasm
$ clj
Clojure 1.11.1
user=> (require '[lasm.parser :as p] '[lasm.ast :as ast] '[lasm.emit :as emit])
nil
user=> (-> (slurp "examples/03_pong.lasm")
           p/parser
           p/parse-tree-to-ast
           ast/build-program
           emit/emit-and-run!)
=================================
  Lasm Pong Game Demo
=================================

Initializing game...
Pong game initialized!
Game window created!
This demo shows:
- Object creation with 'new' keyword
- Java interop (Swing components)
- Function definitions and calls
- Type annotations

Press Ctrl+C in terminal to exit.
nil
user=> ;; Window is now open! Press Ctrl+C to exit
```

## Notes

- The GUI examples will open Swing windows
- Use Ctrl+C to close windows and exit the REPL
- The examples are designed to showcase language features, not be full interactive games
- For a full Pong game, you'd need Java helper classes for game loops and input handling
