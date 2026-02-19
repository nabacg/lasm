# Building Conway's Game of Life in LASM

After building [Pong](tutorial-pong.md), Game of Life is a natural next step. It exercises different muscles: recursive grid traversal, text-based rendering, and more complex state management.

The final result is ~285 lines of LASM that compiles to a standalone JAR. It renders a 25x25 grid in a JTextArea with green-on-black terminal aesthetics.

## The Challenge

Game of Life needs:
- A 2D grid of cells (alive or dead)
- A function to count each cell's neighbors
- A step function that applies the rules to every cell
- A rendering function that converts the grid to text
- Keyboard controls for pause, reset, and patterns

LASM has no loops, no 2D arrays, and no mutable variables. We'll use the same patterns from Pong -- flat arrays for state, recursion for iteration -- but at a larger scale.

## Data Model: Flat Arrays

A 25x25 grid has 625 cells. We store it as a flat int array where cell `(row, col)` lives at index `row * cols + col`:

```lasm
fn createIntArray(size: int): java.lang.Object => {
  intType:java.lang.Class = java.lang.Integer/TYPE
  java.lang.reflect.Array/newInstance(intType, size)
}

current:java.lang.Object = createIntArray(625)
next:java.lang.Object = createIntArray(625)
```

Two arrays for double buffering: read from `current`, write to `next`, then copy `next` back to `current`.

## Bounds-Checked Cell Access

The neighbor-counting function needs to handle edge cells without going out of bounds. Instead of adding boundary checks everywhere, we write a `getCell` function that returns 0 for out-of-bounds coordinates:

```lasm
fn getCell(grid: java.lang.Object, row: int, col: int, rows: int, cols: int): int => {
  if row < 0
    0
  else
    if row >= rows
      0
    else
      if col < 0
        0
      else
        if col >= cols
          0
        else {
          idx:int = row * cols
          cellIdx:int = idx + col
          getArrayElement(grid, cellIdx)
        }
}
```

This is verbose, but the nested if/else pattern is idiomatic LASM. Each level peels off one boundary condition.

## Counting Neighbors

Each cell has 8 neighbors. We check all of them explicitly:

```lasm
fn countNeighbors(grid: java.lang.Object, row: int, col: int, rows: int, cols: int): int => {
  rm1:int = row - 1
  rp1:int = row + 1
  cm1:int = col - 1
  cp1:int = col + 1
  n1:int = getCell(grid, rm1, cm1, rows, cols)
  n2:int = getCell(grid, rm1, col, rows, cols)
  n3:int = getCell(grid, rm1, cp1, rows, cols)
  n4:int = getCell(grid, row, cm1, rows, cols)
  n5:int = getCell(grid, row, cp1, rows, cols)
  n6:int = getCell(grid, rp1, cm1, rows, cols)
  n7:int = getCell(grid, rp1, col, rows, cols)
  n8:int = getCell(grid, rp1, cp1, rows, cols)
  s1:int = n1 + n2
  s2:int = s1 + n3
  s3:int = s2 + n4
  s4:int = s3 + n5
  s5:int = s4 + n6
  s6:int = s5 + n7
  s6 + n8
}
```

Why the chain of additions instead of `n1 + n2 + n3 + ...`? LASM's parser treats `+` as a binary operator, and chaining more than two operands creates ambiguous parses. So we accumulate step by step.

## The Step Function

This is where the Game of Life rules live. For each cell, we apply:
- **Alive + 2 or 3 neighbors** = stays alive
- **Dead + exactly 3 neighbors** = becomes alive
- **Everything else** = dead

Since there are no loops, we iterate recursively over all 625 cells:

```lasm
fn stepCell(current: java.lang.Object, next: java.lang.Object,
            i: int, total: int, rows: int, cols: int): void => {
  if i < total {
    row:int = i / cols
    rc:int = row * cols
    col:int = i - rc
    neighbors:int = countNeighbors(current, row, col, rows, cols)
    alive:int = getArrayElement(current, i)

    if alive == 1 {
      if neighbors == 2 {
        setArrayElement(next, i, 1)
      } else {
        if neighbors == 3 {
          setArrayElement(next, i, 1)
        } else {
          setArrayElement(next, i, 0)
        }
      }
    } else {
      if neighbors == 3 {
        setArrayElement(next, i, 1)
      } else {
        setArrayElement(next, i, 0)
      }
    }

    nextI:int = i + 1
    stepCell(current, next, nextI, total, rows, cols)
  } else {
    setArrayElement(next, 0, getArrayElement(next, 0))
  }
}
```

The `else` branch does a no-op read/write. LASM requires both branches of an if/else to exist and have the same type, so for `void` functions we perform a harmless operation.

## Recursive Grid Operations

Every operation that touches every cell follows the same pattern: a function that processes index `i`, then calls itself with `i + 1`:

```lasm
fn copyGrid(src: java.lang.Object, dst: java.lang.Object, i: int, total: int): void => {
  if i < total {
    val:int = getArrayElement(src, i)
    setArrayElement(dst, i, val)
    nextI:int = i + 1
    copyGrid(src, dst, nextI, total)
  } else {
    setArrayElement(dst, 0, getArrayElement(dst, 0))
  }
}

fn clearGrid(grid: java.lang.Object, i: int, total: int): void => {
  if i < total {
    setArrayElement(grid, i, 0)
    nextI:int = i + 1
    clearGrid(grid, nextI, total)
  } else {
    setArrayElement(grid, 0, 0)
  }
}
```

`randomizeGrid` uses `java.util.Random` -- about 25% of cells start alive:

```lasm
fn randomizeGrid(grid: java.lang.Object, rng: java.util.Random,
                 i: int, total: int): void => {
  if i < total {
    val:int = rng.nextInt(4)
    if val == 0 {
      setArrayElement(grid, i, 1)
    } else {
      setArrayElement(grid, i, 0)
    }
    nextI:int = i + 1
    randomizeGrid(grid, rng, nextI, total)
  } else {
    setArrayElement(grid, 0, getArrayElement(grid, 0))
  }
}
```

## Text-Based Rendering

Instead of Pong's null-layout approach, Game of Life renders the entire grid as a string and displays it in a `JTextArea`. Alive cells are `##`, dead cells are two spaces:

```lasm
fn renderRow(grid: java.lang.Object, rowStart: int, col: int,
             cols: int, acc: string): string => {
  if col < cols {
    idx:int = rowStart + col
    cell:int = getArrayElement(grid, idx)
    nextCol:int = col + 1
    if cell == 1 {
      updatedAcc:string = acc.concat("##")
      renderRow(grid, rowStart, nextCol, cols, updatedAcc)
    } else {
      updatedAcc:string = acc.concat("  ")
      renderRow(grid, rowStart, nextCol, cols, updatedAcc)
    }
  } else
    acc
}

fn renderGrid(grid: java.lang.Object, row: int, rows: int,
              cols: int, acc: string): string => {
  if row < rows {
    rowStart:int = row * cols
    rowStr:string = renderRow(grid, rowStart, 0, cols, "")
    accRow:string = acc.concat(rowStr)
    withNewline:string = accRow.concat("\n")
    nextRow:int = row + 1
    renderGrid(grid, nextRow, rows, cols, withNewline)
  } else
    acc
}
```

This builds the output string by recursive accumulation. `renderRow` builds one row left-to-right, `renderGrid` builds all rows top-to-bottom. Each function takes an `acc` (accumulator) string and appends to it.

## Seeding Patterns: The Glider

The glider is the classic Game of Life pattern -- a 3x3 shape that moves diagonally:

```
  #
    #
# # #
```

```lasm
fn addGlider(grid: java.lang.Object, startRow: int, startCol: int, cols: int): void => {
  sc1:int = startCol + 1
  sc2:int = startCol + 2
  row1s:int = startRow * cols
  i1:int = row1s + sc1
  setArrayElement(grid, i1, 1)

  sr1:int = startRow + 1
  row2s:int = sr1 * cols
  i2:int = row2s + sc2
  setArrayElement(grid, i2, 1)

  sr2:int = startRow + 2
  row3s:int = sr2 * cols
  i3:int = row3s + startCol
  setArrayElement(grid, i3, 1)
  i4:int = row3s + sc1
  setArrayElement(grid, i4, 1)
  i5:int = row3s + sc2
  setArrayElement(grid, i5, 1)
}
```

Lots of index math, but it's straightforward: compute `row * cols + col` for each cell and set it to 1.

## Putting It Together

The `main` function wires everything up:

```lasm
fn main(): int => {
  rows:int = 25
  cols:int = 25
  total:int = 625

  current:java.lang.Object = createIntArray(total)
  next:java.lang.Object = createIntArray(total)
  pauseFlag:java.lang.Object = createIntArray(1)
  genCount:java.lang.Object = createIntArray(1)

  rng:java.util.Random = new java.util.Random()
  randomizeGrid(current, rng, 0, total)
```

The display uses a JTextArea with monospace font, green on black:

```lasm
  textArea:javax.swing.JTextArea = new javax.swing.JTextArea(25, 50)
  monoFont:java.awt.Font = new java.awt.Font("Monospaced", 1, 14)
  textArea.setFont(monoFont)
  textArea.setEditable(false)
  textArea.setBackground(black)
  textArea.setForeground(green)
```

### Keyboard Controls

The KeyListener handles four keys:

```lasm
keyListener:java.awt.event.KeyListener = proxy java.awt.event.KeyListener {
  keyPressed(e:java.awt.event.KeyEvent): void => {
    code:int = e.getKeyCode()

    if code == 32 {          // SPACE: toggle pause
      p:int = getArrayElement(pauseFlag, 0)
      if p == 0 {
        setArrayElement(pauseFlag, 0, 1)
      } else {
        setArrayElement(pauseFlag, 0, 0)
      }
    } else {
      if code == 82 {        // R: randomize
        randomizeGrid(current, rng, 0, total)
        setArrayElement(genCount, 0, 0)
      } else {
        if code == 67 {      // C: clear
          clearGrid(current, 0, total)
          setArrayElement(genCount, 0, 0)
        } else {
          if code == 71 {    // G: add glider
            addGlider(current, 2, 2, cols)
          } else {
            setArrayElement(pauseFlag, 0, getArrayElement(pauseFlag, 0))
          }
        }
      }
    }
  }
  keyReleased(e:java.awt.event.KeyEvent): void => { printstr("") }
  keyTyped(e:java.awt.event.KeyEvent): void => { printstr("") }
}
```

### The Timer

The ActionListener runs every 150ms. When not paused, it steps the simulation, copies the result back, and updates the generation counter. Every tick (paused or not), it re-renders and updates the title:

```lasm
timerListener:java.awt.event.ActionListener = proxy java.awt.event.ActionListener {
  actionPerformed(e:java.awt.event.ActionEvent): void => {
    paused:int = getArrayElement(pauseFlag, 0)

    if paused == 0 {
      stepCell(current, next, 0, total, rows, cols)
      copyGrid(next, current, 0, total)
      gen:int = getArrayElement(genCount, 0)
      setArrayElement(genCount, 0, gen + 1)
    } else {
      setArrayElement(pauseFlag, 0, getArrayElement(pauseFlag, 0))
    }

    gridStr:string = renderGrid(current, 0, rows, cols, "")
    textArea.setText(gridStr)

    g:int = getArrayElement(genCount, 0)
    genStr:string = java.lang.String/valueOf(g)
    titleBase:string = "Game of Life  Gen: "
    titleStr:string = titleBase.concat(genStr)
    frame.setTitle(titleStr)
  }
}

timerObj:javax.swing.Timer = new javax.swing.Timer(150, timerListener)
timerObj.start()
```

## Running It

```bash
clj -M -m lasm.cli compile examples/07_game_of_life.lasm -o game_of_life.jar
java -jar game_of_life.jar
```

You'll see a grid of green `##` symbols evolving on a black background. The title bar shows the generation count. Press Space to pause, R to randomize, C to clear, G to drop a glider.

## Interesting Observations

**String concatenation as rendering.** Every frame, we build the entire display string from scratch by recursively walking 625 cells and concatenating `##` or spaces. This creates ~700 intermediate string objects per frame. On a modern JVM, this is fine -- the GC handles it without visible latency at 150ms per frame.

**Recursion depth.** `stepCell` recurses 625 times per frame, `renderRow` recurses 25 times per row (625 total via `renderGrid`), and `copyGrid` recurses 625 times. That's ~2000 recursive calls per frame. The JVM's default stack size handles this comfortably. For larger grids, you might hit stack overflow -- but 25x25 is well within limits.

**No tail call optimization.** The JVM doesn't optimize tail calls, so each recursive call does consume a stack frame. A future LASM optimization could detect tail-recursive functions and compile them to loops.

**Double buffering matters.** Without separate `current` and `next` arrays, updating a cell would affect its neighbors' calculations in the same generation. The copy-back step (`copyGrid`) ensures each generation is computed from a consistent snapshot.
