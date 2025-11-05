# LASM Pong - TODO List

## Overview

This document outlines all tasks required to implement a fully functional Pong game with animations, keyboard input, and AI opponent. Tasks are organized by priority and dependencies.

**Current Status**: ✅ Parser fixed, KeyListener working, Full game mechanics implemented!

**Latest**: All core game mechanics complete (P0.1-P5.2). Game is playable with keyboard controls, physics, collision detection, and scoring.

---

## CRITICAL - Parser Grammar Fix (BLOCKER)

### Issue Summary

Parser fails when:
- Proxy has 3+ methods
- Multiple proxies in same function
- Even single proxy in long function bodies

This blocks KeyListener implementation (requires 3 methods) and any complex event-driven code.

### Task P0.1: Fix `body` Grammar Rule ✅ COMPLETE

**Priority**: P0 (Critical Blocker)
**Complexity**: Medium
**Time Taken**: ~2 hours
**Status**: ✅ IMPLEMENTED AND TESTED

**Problem**: `src/lasm/parser.clj:34`
```ebnf
body := <'{'>?  wc Expr ws (expr-delim ws Expr)* wc <'}'>?
```

Optional braces create ambiguity where `}` can close either:
- Method body (via optional `<'}'>?`)
- Proxy expression (via required `<'}'>`in ProxyExpr)

**Solution Options**:

**Option A: Require Braces in Proxy Context (RECOMMENDED)**
```ebnf
ProxyMethod := symbol ws <'('> ws params ws <')'> TypeAnnotation ws <'=>'> ws proxy-body
proxy-body := <'{'> wc Expr ws (expr-delim ws Expr)* wc <'}'>
```

**Pros**:
- Eliminates ambiguity
- Clear syntax
- Matches existing LASM style

**Cons**:
- Single-expression methods require braces
- Slight verbosity increase

**Option B: Use Delimiter Between Methods**
```ebnf
ProxyExpr := <'proxy'> ws fullyQualifiedType ws <'{'> wc ProxyMethod (delimiter ProxyMethod)* wc <'}'>
delimiter := <';'> | newline newline
```

**Pros**:
- Keeps optional braces
- Explicit method boundaries

**Cons**:
- Non-standard syntax
- More complex grammar

**Implementation Steps**:

1. **Backup Current Parser**
   ```bash
   cp src/lasm/parser.clj src/lasm/parser.clj.backup
   ```

2. **Create New Grammar Rule**
   ```clojure
   ; Add new rule for proxy method bodies
   proxy-body := <'{'> wc Expr ws (expr-delim ws Expr)* wc <'}'>

   ; Update ProxyMethod to use it
   ProxyMethod := symbol ws <'('> ws params ws <')'> TypeAnnotation ws <'=>'> ws proxy-body
   ```

3. **Update AST Transformation**
   - No changes needed (body structure stays same)
   - AST nodes should match existing format

4. **Run Existing Tests**
   ```bash
   clj -M:test -n lasm.parser-proxy-test
   ```

5. **Add Test Cases for 3+ Methods**
   ```clojure
   (deftest three-method-proxy-test
     (testing "KeyListener with 3 methods parses"
       (let [code "fn test(): int => {
         kl:java.awt.event.KeyListener = proxy java.awt.event.KeyListener {
           keyPressed(e:java.awt.event.KeyEvent): void => { printstr(\"p\") }
           keyReleased(e:java.awt.event.KeyEvent): void => { printstr(\"r\") }
           keyTyped(e:java.awt.event.KeyEvent): void => { printstr(\"t\") }
         }
         42
       }"]
         (is (not (insta/failure? (parser code)))))))
   ```

6. **Verify Multiple Proxies Work**
   ```bash
   # Test file in test_multi_proxy.lasm
   clj -M -m lasm.cli run test_multi_proxy.lasm
   ```

7. **Update Documentation**
   - Update syntax examples in README.md
   - Add note about required braces in proxy methods
   - Update examples to use braces

**Verification**:
- [x] `test_parser_fix.lasm` with 3 methods parses ✅
- [x] Multiple proxies in same function parse ✅
- [x] All existing tests still pass ✅
- [x] KeyListener example works (examples/05_keyboard_test.lasm) ✅

**Implementation**: Added `proxy-body` rule requiring braces, updated all examples.

---

## HIGH PRIORITY - Keyboard Input

### Task P1.1: Implement KeyListener Support ✅ COMPLETE

**Priority**: P1 (High)
**Complexity**: Low
**Depends On**: P0.1 ✅
**Time Taken**: 1 hour
**Status**: ✅ WORKING

**Goal**: Add keyboard event handling for paddle controls

**Implementation**:

1. **Create KeyListener Proxy** (`examples/05_keyboard_test.lasm`):
   ```lasm
   fn testKeyboard(): int => {
     frame:javax.swing.JFrame = new javax.swing.JFrame("Keyboard Test")
     label:javax.swing.JLabel = new javax.swing.JLabel("Press keys...")
     frame.add(label)

     keyListener:java.awt.event.KeyListener = proxy java.awt.event.KeyListener {
       keyPressed(e:java.awt.event.KeyEvent): void => {
         code:int = e.getKeyCode()
         label.setText("Key pressed: " + code)
       }
       keyReleased(e:java.awt.event.KeyEvent): void => {
         label.setText("Key released")
       }
       keyTyped(e:java.awt.event.KeyEvent): void => {
         label.setText("Key typed")
       }
     }

     frame.addKeyListener(keyListener)
     frame.setVisible(true)
     42
   }
   ```

2. **Test Key Codes**:
   - Up Arrow: 38
   - Down Arrow: 40
   - W: 87
   - S: 83

3. **Verify Events Trigger**:
   ```bash
   clj -M -m lasm.cli run examples/05_keyboard_test.lasm
   # Press keys and verify label updates
   ```

**Known Issues**:
- KeyListener requires focus - click window first
- Some keys may be captured by OS
- Multiple key presses may not register simultaneously

**Verification**:
- [x] KeyListener parses correctly ✅
- [x] keyPressed events trigger ✅
- [x] keyReleased events trigger ✅
- [x] Key codes are correct for arrow keys and W/S ✅

**Implementation**: examples/05_keyboard_test.lasm demonstrates all 3 KeyListener methods working.

### Task P1.2: Local Variables in Proxy Methods

**Priority**: P1 (High)
**Complexity**: Medium
**Depends On**: P0.1
**Estimated Time**: 3 hours

**Problem**: Cannot declare local variables in proxy method bodies:
```lasm
keyPressed(e:KeyEvent): void => {
  code:int = e.getKeyCode()  // NOT SUPPORTED
  printint(code)
}
```

**Current Workaround**: Use single-expression bodies only

**Solution**: Update grammar to support variable declarations in proxy bodies

**Grammar Change**:
```ebnf
proxy-body := <'{'> wc statement* wc <'}'>
statement := VarDefExpr | Expr
```

**Implementation Steps**:

1. **Define Statement Rule**:
   ```ebnf
   statement := VarDefExpr expr-delim | Expr expr-delim?
   ```

2. **Update proxy-body**:
   ```ebnf
   proxy-body := <'{'> wc statement+ <'}'>
   ```

3. **Update AST Transformation**:
   - Parse statements sequentially
   - Build environment with local variables
   - Type check each statement

4. **Update LocalGen**:
   - Allocate local variable slots for proxy method locals
   - Track which slots are used
   - Generate appropriate load/store instructions

5. **Test**:
   ```lasm
   keyPressed(e:KeyEvent): void => {
     code:int = e.getKeyCode()
     text:string = "Key: "
     label.setText(text + code)
   }
   ```

**Verification**:
- [ ] Local variables can be declared
- [ ] Variables scope correctly within method
- [ ] Type checking works
- [ ] Bytecode generation correct

---

## HIGH PRIORITY - Game State Management

### Task P2.1: Implement Mutable Game State ✅ COMPLETE

**Priority**: P1 (High)
**Complexity**: High
**Depends On**: None
**Time Taken**: 3 hours
**Status**: ✅ WORKING with Java arrays

**Problem**: LASM variables are immutable (captured as final). Game needs mutable state for:
- Ball position (x, y)
- Ball velocity (dx, dy)
- Paddle positions (leftY, rightY)
- Scores (leftScore, rightScore)

**Solution Options**:

**Option A: Java Array Wrapper (RECOMMENDED)**
```lasm
fn createIntCell(val: int): int[] => {
  cell:int[] = java.lang.reflect.Array.newInstance(java.lang.Integer/TYPE, 1)
  cell[0] = val
  cell
}

fn main(): int => {
  ballX:int[] = createIntCell(400)
  ballY:int[] = createIntCell(300)

  listener:ActionListener = proxy ActionListener {
    actionPerformed(e:ActionEvent): void => {
      ballX[0] = ballX[0] + 5  // Mutate via array
      updateDisplay(ballX[0], ballY[0])
    }
  }
  ...
}
```

**Pros**:
- Works with current LASM
- No language changes needed
- Arrays are naturally mutable

**Cons**:
- Ugly syntax
- Boxing overhead for primitives
- Not type-safe

**Option B: Add Mutable Cell Type** (Future work)
```lasm
fn main(): int => {
  ballX:Cell[int] = cell(400)
  ballY:Cell[int] = cell(300)

  listener:ActionListener = proxy ActionListener {
    actionPerformed(e:ActionEvent): void => {
      ballX.set(ballX.get() + 5)
      ballY.set(ballY.get() + 2)
    }
  }
  ...
}
```

**Implementation for Option A**:

1. **Add Array Support to Parser**:
   ```ebnf
   TypeExpr := ... | TypeExpr <'['> <']'>
   ArrayAccess := VarExpr <'['> Expr <']'>
   ArrayAssign := ArrayAccess ws <'='> ws Expr
   ```

2. **Add Array Type Checking**:
   ```clojure
   (defmethod check :ArrayAccess [[_ arr-expr idx-expr] env]
     (let [arr-type (synth arr-expr env)]
       (assert (array-type? arr-type))
       (assert (check idx-expr :Int env))
       (array-element-type arr-type)))
   ```

3. **Add Array Bytecode Generation**:
   ```clojure
   (defmethod emit :ArrayAccess [[_ arr-expr idx-expr] cg]
     (emit arr-expr cg)
     (emit idx-expr cg)
     (.visitInsn (:method cg) Opcodes/IALOAD))  ; or AALOAD for objects
   ```

4. **Create Helper Functions**:
   ```lasm
   fn createIntArray(size: int): int[] => {
     java.lang.reflect.Array.newInstance(java.lang.Integer/TYPE, size)
   }

   fn intCell(val: int): int[] => {
     arr:int[] = createIntArray(1)
     arr[0] = val
     arr
   }
   ```

**Verification**:
- [x] Arrays can be created via java.lang.reflect.Array ✅
- [x] Array elements can be read with Array.getInt() ✅
- [x] Array elements can be written with Array.setInt() ✅
- [x] Captured arrays are mutable in proxies ✅
- [x] No null pointer exceptions ✅

**Implementation**: Using java.lang.reflect.Array for creating and accessing int arrays. Helper functions createIntArray, getInt, setInt work perfectly.

### Task P2.2: Create Game State Structure

**Priority**: P1
**Complexity**: Low
**Depends On**: P2.1
**Estimated Time**: 1 hour

**Create Mutable State Cells**:
```lasm
fn createGameState(): GameState => {
  ballX:int[] = intCell(400)
  ballY:int[] = intCell(300)
  ballDX:int[] = intCell(3)
  ballDY:int[] = intCell(2)
  leftPaddleY:int[] = intCell(250)
  rightPaddleY:int[] = intCell(250)
  leftScore:int[] = intCell(0)
  rightScore:int[] = intCell(0)
  // Return struct or map
}
```

**Note**: LASM doesn't have structs yet. Use multiple variables or implement struct support.

**Verification**:
- [ ] All state variables can be created
- [ ] State can be passed to proxies
- [ ] State persists across timer ticks

---

## MEDIUM PRIORITY - Ball Movement Physics

### Task P3.1: Implement Ball Movement ✅ COMPLETE

**Priority**: P2 (Medium)
**Complexity**: Low
**Depends On**: P2.1 ✅
**Time Taken**: 1 hour
**Status**: ✅ WORKING

**Physics**:
- Ball position: (x, y)
- Ball velocity: (dx, dy)
- Each frame: x += dx, y += dy
- Screen bounds: 0 <= x <= 800, 0 <= y <= 600

**Implementation**:
```lasm
fn updateBall(ballX: int[], ballY: int[], ballDX: int[], ballDY: int[]): void => {
  newX:int = ballX[0] + ballDX[0]
  newY:int = ballY[0] + ballDY[0]
  ballX[0] = newX
  ballY[0] = newY
}

fn main(): int => {
  ballX:int[] = intCell(400)
  ballY:int[] = intCell(300)
  ballDX:int[] = intCell(3)
  ballDY:int[] = intCell(2)

  timer:javax.swing.Timer = proxy ActionListener {
    actionPerformed(e:ActionEvent): void => {
      updateBall(ballX, ballY, ballDX, ballDY)
      updateDisplay(ballX[0], ballY[0])
    }
  }
  timer.start()
  42
}
```

**Verification**:
- [x] Ball moves across screen ✅
- [x] Movement is smooth at 50-60 FPS ✅
- [x] Frame rate is consistent ✅

**Implementation**: Timer-based update loop with velocity vectors (dx, dy). All working in examples 06-09.

### Task P3.2: Wall Collision Detection ✅ COMPLETE

**Priority**: P2
**Complexity**: Low
**Depends On**: P3.1 ✅
**Time Taken**: 30 min
**Status**: ✅ WORKING

**Bounce Logic**:
- Top wall: y <= 0 → dy = -dy
- Bottom wall: y >= 600 → dy = -dy
- Keep ball in bounds

**Implementation**:
```lasm
fn checkWallCollision(ballY: int[], ballDY: int[]): void => {
  y:int = ballY[0]
  dy:int = ballDY[0]

  if y <= 0 {
    ballDY[0] = -dy  // Bounce off top
  } else {
    if y >= 600 {
      ballDY[0] = -dy  // Bounce off bottom
    } else {
      // No collision
    }
  }
}
```

**Note**: LASM's if-else requires expressions, not statements. May need to refactor as ternary or function call.

**Verification**:
- [x] Ball bounces off top wall ✅
- [x] Ball bounces off bottom wall ✅
- [x] Bounce angle is correct (velocity reversal) ✅
- [x] No ball escape bugs ✅

**Implementation**: Simple boundary checks with velocity reversal (dy = 0 - dy).

---

## MEDIUM PRIORITY - Paddle Controls

### Task P4.1: Implement Paddle Movement ✅ COMPLETE

**Priority**: P2
**Complexity**: Low
**Depends On**: P1.1 ✅, P2.1 ✅
**Time Taken**: 1 hour
**Status**: ✅ WORKING

**Controls**:
- Left paddle: W (up), S (down)
- Right paddle: Up Arrow, Down Arrow
- Movement speed: 10 pixels per keypress
- Bounds: 0 <= y <= 500 (paddle height = 100)

**Implementation**:
```lasm
fn main(): int => {
  leftPaddleY:int[] = intCell(250)
  rightPaddleY:int[] = intCell(250)

  keyListener:KeyListener = proxy KeyListener {
    keyPressed(e:KeyEvent): void => {
      code:int = e.getKeyCode()

      // Left paddle
      if code == 87 {  // W key
        leftPaddleY[0] = max(0, leftPaddleY[0] - 10)
      } else {}

      if code == 83 {  // S key
        leftPaddleY[0] = min(500, leftPaddleY[0] + 10)
      } else {}

      // Right paddle
      if code == 38 {  // Up arrow
        rightPaddleY[0] = max(0, rightPaddleY[0] - 10)
      } else {}

      if code == 40 {  // Down arrow
        rightPaddleY[0] = min(500, rightPaddleY[0] + 10)
      } else {}

      updateDisplay(leftPaddleY[0], rightPaddleY[0])
    }

    keyReleased(e:KeyEvent): void => {}
    keyTyped(e:KeyEvent): void => {}
  }

  frame.addKeyListener(keyListener)
  42
}
```

**Verification**:
- [x] Left paddle moves with W/S ✅
- [x] Right paddle moves with arrows ✅
- [x] Paddles don't go off screen (min/max bounds) ✅
- [x] Movement is responsive ✅

**Implementation**: KeyListener with key code checks (87=W, 83=S, 38=Up, 40=Down). Paddle Y positions updated with bounds checking.

### Task P4.2: Paddle Collision Detection ✅ COMPLETE

**Priority**: P2
**Complexity**: Medium
**Depends On**: P3.1 ✅, P4.1 ✅
**Time Taken**: 2 hours
**Status**: ✅ WORKING

**Collision Logic**:
- Left paddle: x = 20, y = leftPaddleY, width = 10, height = 100
- Right paddle: x = 770, y = rightPaddleY, width = 10, height = 100
- Ball: x = ballX, y = ballY, radius = 10

**Collision Detection**:
```lasm
fn checkPaddleCollision(ballX: int[], ballY: int[], ballDX: int[],
                        leftY: int[], rightY: int[]): void => {
  x:int = ballX[0]
  y:int = ballY[0]
  dx:int = ballDX[0]

  // Left paddle collision
  if x <= 30 {  // Near left paddle
    if y >= leftY[0] {
      if y <= leftY[0] + 100 {
        ballDX[0] = -dx  // Bounce right
      } else {}
    } else {}
  } else {}

  // Right paddle collision
  if x >= 760 {  // Near right paddle
    if y >= rightY[0] {
      if y <= rightY[0] + 100 {
        ballDX[0] = -dx  // Bounce left
      } else {}
    } else {}
  } else {}
}
```

**Verification**:
- [x] Ball bounces off left paddle ✅
- [x] Ball bounces off right paddle ✅
- [x] No double-bounce bugs ✅
- [x] Collision detection working ✅

**Implementation**: Nested if checks for X proximity and Y range overlap. Velocity reversal on hit (dx = 0 - dx).

---

## MEDIUM PRIORITY - Scoring System

### Task P5.1: Detect Goals ✅ COMPLETE

**Priority**: P2
**Complexity**: Low
**Depends On**: P3.1 ✅
**Time Taken**: 30 min
**Status**: ✅ WORKING

**Goal Detection**:
- Left scores: ballX >= 800
- Right scores: ballX <= 0
- Reset ball to center after goal

**Implementation**:
```lasm
fn checkGoals(ballX: int[], ballY: int[], leftScore: int[], rightScore: int[]): void => {
  x:int = ballX[0]

  if x <= 0 {
    rightScore[0] = rightScore[0] + 1
    resetBall(ballX, ballY)
  } else {}

  if x >= 800 {
    leftScore[0] = leftScore[0] + 1
    resetBall(ballX, ballY)
  } else {}
}

fn resetBall(ballX: int[], ballY: int[]): void => {
  ballX[0] = 400
  ballY[0] = 300
}
```

**Verification**:
- [x] Left player scores when ball exits right ✅
- [x] Right player scores when ball exits left ✅
- [x] Ball resets to center after goal ✅
- [x] Scores increment correctly ✅

**Implementation**: X boundary checks (x <= 0, x >= width). Score arrays updated, ball position reset.

### Task P5.2: Display Scores ✅ COMPLETE

**Priority**: P2
**Complexity**: Low
**Depends On**: P5.1 ✅
**Time Taken**: 30 min
**Status**: ✅ WORKING

**Display Requirements**:
- Show scores at top of screen
- Format: "3 : 2"
- Update on every goal
- Large font (36pt)

**Implementation**:
```lasm
fn updateScoreDisplay(scoreLabel: JLabel, leftScore: int[], rightScore: int[]): void => {
  text:string = leftScore[0] + " : " + rightScore[0]
  scoreLabel.setText(text)
}
```

**Verification**:
- [x] Scores display correctly ✅
- [x] Scores update immediately ✅
- [x] Format is readable (using StringBuilder) ✅

**Implementation**: StringBuilder to concatenate score values. Updated on every goal.

---

## LOW PRIORITY - Visual Improvements

### Task P6.1: Improve Ball Rendering

**Priority**: P3 (Low)
**Complexity**: Medium
**Estimated Time**: 3 hours

**Current**: Using JLabel with "O" character
**Goal**: Use actual circle graphics

**Options**:

**Option A: ASCII Art** (Simple)
```lasm
fn renderBall(x: int, y: int): string => {
  "   OOO   " +
  "  OOOOO  " +
  " OOOOOOO " +
  "OOOOOOOOO"
}
```

**Option B: Custom JPanel with Graphics2D** (Better)
```lasm
fn createBallPanel(x: int[], y: int[]): JPanel => {
  panel:JPanel = proxy JPanel {
    paintComponent(g:Graphics): void => {
      g.setColor(Color.WHITE)
      g.fillOval(x[0], y[0], 20, 20)
    }
  }
  panel
}
```

**Implementation for Option B**:

1. **Add paintComponent Support**:
   - Override `paintComponent` method
   - Access Graphics object
   - Draw shapes with Graphics2D

2. **Create Custom Panel**:
   ```lasm
   gamePanel:JPanel = proxy JPanel {
     paintComponent(g:Graphics): void => {
       super.paintComponent(g)
       drawBall(g, ballX[0], ballY[0])
       drawPaddle(g, 20, leftPaddleY[0])
       drawPaddle(g, 770, rightPaddleY[0])
     }
   }
   ```

3. **Repaint on Timer**:
   ```lasm
   timer:Timer = proxy ActionListener {
     actionPerformed(e:ActionEvent): void => {
       updatePhysics()
       gamePanel.repaint()
     }
   }
   ```

**Verification**:
- [ ] Ball renders as circle
- [ ] Paddles render as rectangles
- [ ] Graphics are smooth
- [ ] No flickering

### Task P6.2: Add Colors and Styling

**Priority**: P3
**Complexity**: Low
**Depends On**: P6.1
**Estimated Time**: 1 hour

**Enhancements**:
- Black background
- White ball
- Different colors for paddles
- Score display in color
- Center line

**Verification**:
- [ ] Colors display correctly
- [ ] UI looks polished

---

## LOW PRIORITY - Game Loop Optimization

### Task P7.1: Implement Fixed Time Step

**Priority**: P3
**Complexity**: Medium
**Estimated Time**: 3 hours

**Problem**: Timer fires at intervals but physics should be framerate-independent

**Solution**: Track delta time
```lasm
lastTime:long[] = longCell(System.currentTimeMillis())

timer:Timer = proxy ActionListener {
  actionPerformed(e:ActionEvent): void => {
    now:long = System.currentTimeMillis()
    dt:long = now - lastTime[0]
    lastTime[0] = now

    updatePhysics(dt)  // Scale movement by dt
  }
}
```

**Verification**:
- [ ] Physics consistent across frame rates
- [ ] No speed-up/slow-down issues

### Task P7.2: Add Pause/Resume Functionality

**Priority**: P3
**Complexity**: Low
**Estimated Time**: 1 hour

**Implementation**:
- Press SPACE to pause
- Press SPACE again to resume
- Display "PAUSED" message

**Verification**:
- [ ] Game pauses correctly
- [ ] Game resumes correctly
- [ ] Pause message displays

---

## BONUS - Computer AI Player

### Task P8.1: Basic AI (Follow Ball)

**Priority**: P4 (Bonus)
**Complexity**: Low
**Estimated Time**: 2 hours

**Algorithm**: Move paddle toward ball Y position
```lasm
fn updateAI(ballY: int[], paddleY: int[]): void => {
  targetY:int = ballY[0] - 50  // Center paddle on ball
  currentY:int = paddleY[0]

  if targetY > currentY {
    paddleY[0] = min(500, currentY + 5)
  } else {
    if targetY < currentY {
      paddleY[0] = max(0, currentY - 5)
    } else {}
  }
}
```

**Verification**:
- [ ] AI tracks ball vertically
- [ ] AI moves smoothly
- [ ] AI doesn't go off screen

### Task P8.2: Improved AI (Prediction)

**Priority**: P4
**Complexity**: Medium
**Estimated Time**: 3 hours

**Algorithm**: Predict where ball will be when it reaches paddle

```lasm
fn predictBallY(ballX: int, ballY: int, ballDX: int, ballDY: int,
                paddleX: int): int => {
  // Calculate time to reach paddle
  dx:int = paddleX - ballX
  dt:int = dx / ballDX

  // Predict Y position
  predictedY:int = ballY + (ballDY * dt)

  // Account for wall bounces
  if predictedY < 0 {
    predictedY = -predictedY
  } else {}
  if predictedY > 600 {
    predictedY = 1200 - predictedY
  } else {}

  predictedY
}

fn updateSmartAI(ballX: int[], ballY: int[], ballDX: int[], ballDY: int[],
                 paddleY: int[]): void => {
  targetY:int = predictBallY(ballX[0], ballY[0], ballDX[0], ballDY[0], 770)
  // Move toward predicted position
  updateAIPaddle(targetY, paddleY)
}
```

**Verification**:
- [ ] AI predicts ball trajectory
- [ ] AI reaches ball most of the time
- [ ] AI is beatable (not perfect)

### Task P8.3: Difficulty Levels

**Priority**: P4
**Complexity**: Low
**Estimated Time**: 1 hour

**Implementation**: Vary AI speed and accuracy
```lasm
fn updateAIWithDifficulty(difficulty: int, ...): void => {
  if difficulty == 0 {  // Easy
    speed = 3
    accuracy = 0.7
  } else {
    if difficulty == 1 {  // Medium
      speed = 5
      accuracy = 0.85
    } else {  // Hard
      speed = 7
      accuracy = 0.95
    }
  }
  // Use speed and accuracy in movement
}
```

**Verification**:
- [ ] Easy AI is beatable
- [ ] Medium AI is challenging
- [ ] Hard AI is very difficult but not impossible

---

## Known Bugs and Issues

### B1: Parser Grammar Ambiguity

**Status**: CRITICAL
**Affects**: All keyboard input, multi-method interfaces
**Workaround**: Use 1-2 method interfaces only
**Fix**: P0.1

### B2: No Local Variables in Proxy Methods

**Status**: High Impact
**Affects**: Code organization, readability
**Workaround**: Use single-expression bodies
**Fix**: P1.2

### B3: Thread.sleep Type Ambiguity

**Status**: Medium Impact
**Affects**: Delay operations
**Workaround**: Use Timer instead
**Fix**: Improve overload resolution in type checker

### B4: No Struct/Record Types

**Status**: Medium Impact
**Affects**: Game state organization
**Workaround**: Use multiple variables or Java objects
**Fix**: Add struct support to LASM

### B5: No Mutable Variables

**Status**: High Impact
**Affects**: All stateful game logic
**Workaround**: Use arrays as mutable cells
**Fix**: P2.1 or add mutable cell type

### B6: If-Else Requires Expressions

**Status**: Low Impact
**Affects**: Control flow readability
**Workaround**: Use nested if-else or functions
**Fix**: Add statement support to if-else

### B7: No String Concatenation Operator

**Status**: Low Impact
**Affects**: Display formatting
**Workaround**: Use StringBuilder or string.concat()
**Fix**: Add `+` operator for strings

### B8: No Increment/Decrement Operators

**Status**: Low Impact
**Affects**: Verbose arithmetic
**Workaround**: Use `x = x + 1`
**Fix**: Add `++` and `--` operators

---

## Testing Strategy

### Unit Tests
- [ ] Parser tests for all grammar changes
- [ ] Type checker tests for new features
- [ ] Bytecode generation tests

### Integration Tests
- [ ] Full Pong game runs without crashes
- [ ] All controls work correctly
- [ ] Scoring works correctly
- [ ] AI plays correctly

### Performance Tests
- [ ] Game runs at 60 FPS
- [ ] No memory leaks
- [ ] CPU usage reasonable

### Manual Tests
- [ ] Play full game as left player
- [ ] Play full game as right player
- [ ] Play against AI
- [ ] Test edge cases (score to 10, very long rallies)

---

## Documentation Tasks

### D1: Update README with Pong Example

**Priority**: P2
**Time**: 1 hour

Add Pong to examples section with:
- How to run
- Controls
- Features demonstrated

### D2: Create Tutorial: Building Pong

**Priority**: P3
**Time**: 3 hours

Step-by-step guide:
1. Set up window
2. Add ball
3. Add movement
4. Add paddles
5. Add controls
6. Add scoring

### D3: Document Parser Grammar

**Priority**: P2
**Time**: 2 hours

Full EBNF grammar with:
- All rules documented
- Examples for each construct
- Known limitations

### D4: Create Video Demo

**Priority**: P4
**Time**: 2 hours

Screen recording showing:
- Pong gameplay
- Code walkthrough
- Compilation process

---

## Timeline Estimate

### Phase 1: Critical Fixes (1-2 days)
- P0.1: Parser grammar fix
- P1.1: KeyListener support
- Verification tests

### Phase 2: Core Game (3-4 days)
- P1.2: Local variables
- P2.1: Mutable state
- P2.2: Game state structure
- P3.1: Ball movement
- P3.2: Wall collision
- P4.1: Paddle controls
- P4.2: Paddle collision
- P5.1: Goal detection
- P5.2: Score display

### Phase 3: Polish (1-2 days)
- P6.1: Graphics improvements
- P6.2: Colors and styling
- P7.1: Fixed time step
- P7.2: Pause functionality
- Documentation updates

### Phase 4: AI (Optional, 1-2 days)
- P8.1: Basic AI
- P8.2: Predictive AI
- P8.3: Difficulty levels

**Total Estimate**: 6-10 days of development

---

## Success Criteria

A fully functional Pong game includes:

**Must Have**:
- [ ] Two paddles controlled by keyboard
- [ ] Ball that moves and bounces
- [ ] Collision detection (walls and paddles)
- [ ] Scoring system
- [ ] Visual display of game state
- [ ] Playable by two humans

**Should Have**:
- [ ] Smooth animation (30+ FPS)
- [ ] Polished graphics
- [ ] Score display
- [ ] Game reset functionality
- [ ] Computer AI opponent

**Nice to Have**:
- [ ] Multiple difficulty levels
- [ ] Sound effects
- [ ] Pause/resume
- [ ] Win condition (first to 10)
- [ ] Menu system

---

## Resources and References

### LASM Documentation
- `Claude.md` - Technical deep dive
- `examples/` - Working examples
- `test/` - Test suite

### Java APIs Used
- `javax.swing.JFrame` - Window
- `javax.swing.JPanel` - Drawing surface
- `javax.swing.JLabel` - Text display
- `javax.swing.Timer` - Animation loop
- `java.awt.event.KeyListener` - Keyboard input
- `java.awt.Graphics2D` - Graphics rendering
- `java.awt.Color` - Colors

### External References
- Pong game logic: https://en.wikipedia.org/wiki/Pong
- Swing tutorial: https://docs.oracle.com/javase/tutorial/uiswing/
- Game loop patterns: https://gameprogrammingpatterns.com/game-loop.html

---

## Maintenance Notes

### After Each Task
- [ ] Run all tests
- [ ] Update documentation
- [ ] Commit changes
- [ ] Tag stable versions

### Code Review Checklist
- [ ] Parser grammar changes don't break existing code
- [ ] Type checker validates all new constructs
- [ ] Bytecode generation is correct
- [ ] No memory leaks
- [ ] Performance is acceptable
- [ ] Code is documented

### Future Considerations
- Port to other platforms (web, mobile)
- Add networking for multiplayer
- Create level editor
- Add more game modes
- Optimize for performance
