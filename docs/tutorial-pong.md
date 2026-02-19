# Building Pong in LASM

This is the story of building a fully playable Pong game in ~240 lines of a language that has no loops, no mutable variables, and no standard library. The output is a standalone JAR file with zero runtime dependencies.

If you haven't read the [basics tutorial](tutorial-hello-world.md), start there.

## The Problem

LASM is statically typed, compiles to JVM bytecode, and has direct Java interop. But it has some constraints that make game development interesting:

1. **No loops.** All iteration is recursion.
2. **No mutable locals.** Once a variable is bound, it can't change.
3. **No standard library.** Everything comes from Java interop.

So how do you build a game that needs mutable state (ball position, paddle position, scores) and a game loop (60fps updates)?

## The Architecture

The trick is two Java features:

- **`javax.swing.Timer`** fires an `ActionListener` every N milliseconds. This is our game loop.
- **Int arrays** act as mutable cells. You can't mutate a variable, but you can mutate array contents.

The structure:

```
1. Create mutable state (int arrays for ball, paddles, score)
2. Create GUI components (JPanel for ball/paddles, JLabel for score)
3. Create KeyListener proxy (reads keyboard, updates state arrays)
4. Create Timer with ActionListener proxy (reads state, updates physics, moves components)
5. Start timer, show window
```

## Step 1: Mutable State via Arrays

Since we can't mutate variables, we use single-element int arrays as mutable cells:

```lasm
fn createIntArray(size: int): java.lang.Object => {
  intType:java.lang.Class = java.lang.Integer/TYPE
  java.lang.reflect.Array/newInstance(intType, size)
}

fn getArrayElement(arr: java.lang.Object, index: int): int => {
  java.lang.reflect.Array/getInt(arr, index)
}

fn setArrayElement(arr: java.lang.Object, index: int, value: int): void => {
  java.lang.reflect.Array/setInt(arr, index, value)
}
```

We use `java.lang.reflect.Array` because LASM doesn't have native array syntax. Each piece of game state is a 1-element array:

```lasm
ballX:java.lang.Object = createIntArray(1)
ballY:java.lang.Object = createIntArray(1)
ballDX:java.lang.Object = createIntArray(1)
ballDY:java.lang.Object = createIntArray(1)
leftPaddleY:java.lang.Object = createIntArray(1)
rightPaddleY:java.lang.Object = createIntArray(1)
leftScore:java.lang.Object = createIntArray(1)
rightScore:java.lang.Object = createIntArray(1)
```

Initialize them:

```lasm
setArrayElement(ballX, 0, 392)     // center of 800px window
setArrayElement(ballY, 0, 280)     // center of 600px window
setArrayElement(ballDX, 0, 3)      // horizontal velocity
setArrayElement(ballDY, 0, 2)      // vertical velocity
setArrayElement(leftPaddleY, 0, 250)
setArrayElement(rightPaddleY, 0, 250)
```

## Step 2: GUI with Null Layout

For a game, we need pixel-precise positioning. Swing's layout managers won't work. Instead, we use **null layout** and `setBounds`:

```lasm
panel:javax.swing.JPanel = new javax.swing.JPanel()
panel.setLayout(java.lang.Object/null)    // disable layout manager
black:java.awt.Color = new java.awt.Color(0, 0, 0)
panel.setBackground(black)
```

The `java.lang.Object/null` syntax is how LASM expresses `null` -- it's accessed as a static field.

Now create the visual components. The ball and paddles are just JPanels with a white background:

```lasm
white:java.awt.Color = new java.awt.Color(255, 255, 255)

ball:javax.swing.JPanel = new javax.swing.JPanel()
ball.setBackground(white)
ball.setBounds(392, 280, 15, 15)           // x, y, width, height

leftPaddle:javax.swing.JPanel = new javax.swing.JPanel()
leftPaddle.setBackground(white)
leftPaddle.setBounds(20, 250, 10, 80)

rightPaddle:javax.swing.JPanel = new javax.swing.JPanel()
rightPaddle.setBackground(white)
rightPaddle.setBounds(760, 250, 10, 80)
```

The score display is a JLabel:

```lasm
scoreLabel:javax.swing.JLabel = new javax.swing.JLabel("0 : 0")
scoreLabel.setForeground(white)
scoreFont:java.awt.Font = new java.awt.Font("Monospaced", 1, 28)
scoreLabel.setFont(scoreFont)
scoreLabel.setBounds(340, 10, 120, 35)
```

## Step 3: Keyboard Input

LASM implements Java interfaces using `proxy`. The `KeyListener` interface has three methods, all of which must be implemented:

```lasm
keyListener:java.awt.event.KeyListener = proxy java.awt.event.KeyListener {
  keyPressed(e:java.awt.event.KeyEvent): void => {
    code:int = e.getKeyCode()
    currentLeft:int = getArrayElement(leftPaddleY, 0)
    currentRight:int = getArrayElement(rightPaddleY, 0)

    if code == 87 {                  // W key
      newLeft:int = max(0, currentLeft - 20)
      setArrayElement(leftPaddleY, 0, newLeft)
    } else {
      if code == 83 {                // S key
        newLeft:int = min(440, currentLeft + 20)
        setArrayElement(leftPaddleY, 0, newLeft)
      } else {
        if code == 38 {              // Up arrow
          newRight:int = max(0, currentRight - 20)
          setArrayElement(rightPaddleY, 0, newRight)
        } else {
          if code == 40 {            // Down arrow
            newRight:int = min(440, currentRight + 20)
            setArrayElement(rightPaddleY, 0, newRight)
          } else {
            setArrayElement(leftPaddleY, 0, currentLeft)
          }
        }
      }
    }
  }
  keyReleased(e:java.awt.event.KeyEvent): void => {
    printstr("")
  }
  keyTyped(e:java.awt.event.KeyEvent): void => {
    printstr("")
  }
}
```

A few things to notice:

- **All three methods are required.** `keyReleased` and `keyTyped` do nothing, but the proxy must implement every interface method.
- **No switch/case.** We use nested `if/else` chains. It's verbose but it works.
- **Closure capture.** The proxy body references `leftPaddleY`, `rightPaddleY`, and the `getArrayElement`/`setArrayElement` functions. These are captured from the outer scope and stored as fields on the generated proxy class.
- **Key codes are integers.** `87` = W, `83` = S, `38` = Up, `40` = Down.

## Step 4: The Game Loop

The game loop is a `javax.swing.Timer` that fires every 16ms (~60fps). Each tick:

1. Reads ball position and velocity from arrays
2. Computes new position
3. Checks for wall collisions (bounce off top/bottom)
4. Checks for paddle collisions (bounce off paddles)
5. Checks for scoring (ball passes left/right edge)
6. Updates state arrays and moves GUI components

```lasm
timerListener:java.awt.event.ActionListener = proxy java.awt.event.ActionListener {
  actionPerformed(e:java.awt.event.ActionEvent): void => {
    x:int = getArrayElement(ballX, 0)
    y:int = getArrayElement(ballY, 0)
    dx:int = getArrayElement(ballDX, 0)
    dy:int = getArrayElement(ballDY, 0)

    newX:int = x + dx
    newY:int = y + dy
    // ... collision detection ...

    setArrayElement(ballX, 0, newX)
    setArrayElement(ballY, 0, newY)
    ball.setBounds(newX, newY, 15, 15)
    leftPaddle.setBounds(20, leftY, 10, 80)
    rightPaddle.setBounds(760, rightY, 10, 80)
  }
}

timerObj:javax.swing.Timer = new javax.swing.Timer(16, timerListener)
timerObj.start()
```

The `setBounds` calls are what actually move things on screen. Each frame, we reposition every component based on the current state.

### Collision Detection

Wall bounces flip the vertical velocity:

```lasm
if newY <= 0 {
  newDY = abs(dy)        // bounce down
  newY = 0
} else {
  if newY >= 545 {
    newDY = 0 - abs(dy)  // bounce up
    newY = 545
  } else {
    newDY = dy
  }
}
```

Paddle bounces check if the ball overlaps the paddle's Y range:

```lasm
ballBottom:int = newY + 15
leftPdlBottom:int = leftY + 80

if newX <= 30 {
  if ballBottom > leftY {
    if newY < leftPdlBottom {
      newDX = abs(dx)    // bounce right
      newX = 30
    } else {
      newDX = dx
    }
  } else {
    newDX = dx
  }
}
```

### Scoring

When the ball passes the edge, we update the score and reset:

```lasm
if newX < 0 {
  rs:int = getArrayElement(rightScore, 0)
  setArrayElement(rightScore, 0, rs + 1)
  setArrayElement(ballX, 0, 392)
  setArrayElement(ballY, 0, 280)

  lsStr:string = java.lang.String/valueOf(getArrayElement(leftScore, 0))
  newRs:int = rs + 1
  rsStr:string = java.lang.String/valueOf(newRs)
  tmp:string = lsStr.concat(" : ")
  scoreText:string = tmp.concat(rsStr)
  scoreLabel.setText(scoreText)
}
```

Note: `java.lang.String/valueOf(int)` converts an integer to a string. LASM doesn't have string interpolation, so we build the score string by concatenating pieces.

## Step 5: Launch

```lasm
frame.setVisible(true)
frame.requestFocus()
42
```

The `requestFocus()` is important -- without it, the frame won't receive keyboard events.

## Running It

```bash
clj -M -m lasm.cli compile examples/06_pong_full_game.lasm -o pong.jar
java -jar pong.jar
```

You get a black window with a bouncing white ball, two paddles, and a score counter. W/S controls the left paddle, Up/Down controls the right.

## Lessons Learned

**Arrays as mutable cells** is the key pattern. In a language without mutation, you can still model mutable state by putting values in containers (arrays) and swapping the contents. This is essentially what Clojure's atoms do, minus the concurrency guarantees.

**Null layout + setBounds** is the simplest way to do pixel-precise Swing. No fighting with layout managers.

**Proxy closure capture** makes event-driven programming possible. The proxy class stores references to outer variables as instance fields, so the callback can read and write shared state.

**No loops means no game loop abstraction.** The `Timer` + `ActionListener` pattern replaces the traditional `while(running) { update(); render(); }` loop. Each timer tick is a fresh function call with the latest state read from arrays.

The full source is 240 lines. Not bad for a language with no standard library.
