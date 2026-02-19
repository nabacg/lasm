# Building Conway's Game of Life in LASM

After building [Pong](tutorial-pong.md), Game of Life is a natural next step. It exercises different muscles: recursive grid traversal, pixel rendering with `BufferedImage`, and user input via dialog boxes.

The final result is ~270 lines of LASM that compiles to a standalone JAR. At startup it asks you for a grid size, shows you the controls, then launches a pixel-rendered simulation with green tiles on a dark grid.

## The Challenge

Game of Life needs:
- A 2D grid of cells (alive or dead)
- A function to count each cell's neighbors
- A step function that applies the rules to every cell
- A rendering function that draws colored tiles
- Keyboard controls for pause, reset, and patterns

LASM has no loops, no 2D arrays, and no mutable variables. We'll use the same patterns from Pong -- flat arrays for state, recursion for iteration -- but at a larger scale.

## Data Model: Flat Arrays

We store the grid as a flat int array where cell `(row, col)` lives at index `row * cols + col`. The grid size is chosen by the user at startup:

```lasm
sizeStr:string = javax.swing.JOptionPane/showInputDialog("Enter grid size (10-50):")
rows:int = java.lang.Integer/parseInt(sizeStr)
cols:int = rows
total:int = rows * cols

current:java.lang.Object = createIntArray(total)
next:java.lang.Object = createIntArray(total)
```

Two arrays for double buffering: read from `current`, write to `next`, then copy `next` back to `current`. The `JOptionPane.showInputDialog` pops up a dialog asking for the grid size, and `Integer.parseInt` converts the string response to an int.

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

Since there are no loops, we iterate recursively over all cells:

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

## Pixel Rendering with BufferedImage

Instead of Pong's component-based approach, Game of Life renders directly to a `BufferedImage` using Java2D. Each cell becomes a colored square tile on a dark grid:

```lasm
cellSize:int = 16
imgW:int = cols * cellSize
imgH:int = rows * cellSize
img:java.awt.image.BufferedImage = new java.awt.image.BufferedImage(imgW, imgH, 1)
gfx:java.awt.Graphics2D = img.createGraphics()

aliveColor:java.awt.Color = new java.awt.Color(0, 200, 0)
deadColor:java.awt.Color = new java.awt.Color(40, 40, 40)
```

The `1` in the `BufferedImage` constructor is `TYPE_INT_RGB`. Each cell is drawn as a 15x15 filled rectangle on a 16x16 grid, leaving 1-pixel black gaps that form the grid lines.

The rendering function walks every cell recursively, setting the color and drawing a filled rectangle:

```lasm
fn renderCell(gfx: java.awt.Graphics2D, grid: java.lang.Object, i: int, total: int,
              cols: int, cellSize: int, aliveColor: java.awt.Color,
              deadColor: java.awt.Color): void => {
  if i < total {
    row:int = i / cols
    rc:int = row * cols
    col:int = i - rc
    alive:int = getArrayElement(grid, i)
    x:int = col * cellSize
    y:int = row * cellSize
    cs:int = cellSize - 1

    if alive == 1 {
      gfx.setColor(aliveColor)
    } else {
      gfx.setColor(deadColor)
    }
    gfx.fillRect(x, y, cs, cs)

    nextI:int = i + 1
    renderCell(gfx, grid, nextI, total, cols, cellSize, aliveColor, deadColor)
  } else {
    setArrayElement(grid, 0, getArrayElement(grid, 0))
  }
}
```

The image is displayed via a `JLabel` with an `ImageIcon`:

```lasm
icon:javax.swing.ImageIcon = new javax.swing.ImageIcon(img)
label:javax.swing.JLabel = new javax.swing.JLabel(icon)
frame.add(label)
frame.pack()
```

Each frame, after rendering to the `BufferedImage`, we just call `label.repaint()` to update the display. The `ImageIcon` wraps the same image buffer, so the new pixels appear immediately.

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

## User Input and Controls

At startup, the program shows two dialog boxes using `JOptionPane`:

```lasm
sizeStr:string = javax.swing.JOptionPane/showInputDialog("Enter grid size (10-50):")
rows:int = java.lang.Integer/parseInt(sizeStr)

controlsMsg:string = "Controls:\n\n  SPACE = Pause / Resume\n  R = Randomize grid\n  C = Clear grid\n  G = Add glider pattern\n\nPress OK to start."
javax.swing.JOptionPane/showMessageDialog(java.lang.Object/null, controlsMsg)
```

The `showInputDialog` returns a string, which we convert to an int with `Integer.parseInt`. The `showMessageDialog` takes `null` as the parent component (`java.lang.Object/null` is how LASM expresses null) and a message string.

The KeyListener handles four keys with nested if/else:

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

The ActionListener runs every 100ms. When not paused, it steps the simulation, copies the result back, and updates the generation counter. Every tick, it re-renders the grid and updates the window title:

```lasm
timerListener:java.awt.event.ActionListener = proxy java.awt.event.ActionListener {
  actionPerformed(e:java.awt.event.ActionEvent): void => {
    paused:int = getArrayElement(pauseFlag, 0)

    if paused == 0 {
      stepCell(current, next, 0, total, rows, cols)
      copyGrid(next, current, 0, total)
      gen:int = getArrayElement(genCount, 0)
      genPlus:int = gen + 1
      setArrayElement(genCount, 0, genPlus)
    } else {
      setArrayElement(pauseFlag, 0, getArrayElement(pauseFlag, 0))
    }

    renderCell(gfx, current, 0, total, cols, cellSize, aliveColor, deadColor)
    label.repaint()

    g:int = getArrayElement(genCount, 0)
    genStr:string = java.lang.String/valueOf(g)
    titleBase:string = "Game of Life  Gen: "
    titleStr:string = titleBase.concat(genStr)
    frame.setTitle(titleStr)
  }
}

timerObj:javax.swing.Timer = new javax.swing.Timer(100, timerListener)
timerObj.start()
```

## Running It

```bash
clj -M -m lasm.cli compile examples/07_game_of_life.lasm -o game_of_life.jar
java -jar game_of_life.jar
```

A dialog asks for the grid size, another shows the controls, then the simulation launches. Green tiles pulse and evolve on a dark grid. The title bar tracks the generation count.

## Interesting Observations

**BufferedImage as a frame buffer.** We create a single `BufferedImage` and `Graphics2D` once, then re-render to the same buffer every frame. The `label.repaint()` call tells Swing to redraw from the same image. This avoids creating thousands of string objects per frame (the text rendering approach) and produces a much cleaner visual.

**Recursion depth.** `stepCell` recurses once per cell per frame, `renderCell` also recurses once per cell, and `copyGrid` does the same. For a 25x25 grid, that's ~1900 recursive calls per frame. The JVM's default stack handles this fine. For a 50x50 grid (2500 cells), it's ~7500 calls -- still within limits.

**No tail call optimization.** The JVM doesn't optimize tail calls, so each recursive call consumes a stack frame. A future LASM optimization could detect tail-recursive functions and compile them to loops.

**Double buffering matters.** Without separate `current` and `next` arrays, updating a cell would affect its neighbors' calculations in the same generation. The copy-back step (`copyGrid`) ensures each generation is computed from a consistent snapshot.

**Java interop unlocks everything.** The entire graphics pipeline -- `BufferedImage`, `Graphics2D`, `ImageIcon`, `JOptionPane` -- comes from Java's standard library. LASM just calls it. No bindings, no FFI, no wrappers. Every Java class is available directly.
