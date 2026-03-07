# LASM Examples

A progression from "open a window" to fully playable games.

## Examples

| # | File | Description | Key Features |
|---|------|-------------|--------------|
| 01 | `01_simple_window.lasm` | Empty JFrame window | Constructor, method calls |
| 02 | `02_window_with_label.lasm` | Window with text label | Multiple objects, layout |
| 03 | `03_pong.lasm` | Static Pong display | Functions, GUI composition |
| 04 | `04_animated_pong.lasm` | Animated label with timer | ActionListener proxy, closure capture |
| 05 | `05_keyboard_test.lasm` | Keyboard event handler | 3-method KeyListener proxy |
| 06 | `06_pong_full_game.lasm` | Playable Pong game | Null layout, setBounds, physics, scoring |
| 07 | `07_game_of_life.lasm` | Conway's Game of Life | Recursive iteration, text rendering, double buffering |
| 08 | `08_snake.lasm` | Snake game | Circular buffer, O(1) collision, BufferedImage rendering |

## Running

### Compile to standalone JAR (recommended)
```bash
clj -M -m lasm.cli compile examples/08_snake.lasm -o snake.jar
java -jar snake.jar
```

### Run directly (no JAR)
```bash
clj -M -m lasm.cli run examples/04_animated_pong.lasm
```

## Pong Game (06)

The Pong game demonstrates most of lasm's features in ~200 lines:
- **Mutable state** via int arrays (no mutable locals, so arrays act as cells)
- **Null layout** with `panel.setLayout(java.lang.Object/null)` for absolute positioning
- **setBounds** to move ball and paddles each frame
- **KeyListener proxy** (3 methods) with closure capture for keyboard input
- **ActionListener proxy** for 60fps game loop timer
- **Collision detection** with wall and paddle bouncing
- **Score tracking** with `String.valueOf` for int-to-string conversion

### Controls
- **W / S** - Move left paddle up/down
- **Up / Down arrows** - Move right paddle up/down
- Ball bounces off walls and paddles
- Score updates when ball passes a paddle

## Game of Life (07)

Conway's Game of Life on a 25x25 grid, rendered as text in a JTextArea:
- **Recursive iteration** - no loops, all iteration via tail-recursive functions
- **Double buffering** - current and next grid arrays, copy after each step
- **Text rendering** - builds display string recursively, `##` for alive cells
- **Multiple controls** via KeyListener proxy

### Controls
- **SPACE** - Pause/resume
- **R** - Randomize grid
- **C** - Clear grid
- **G** - Add a glider pattern
