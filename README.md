# BSnake

Simple Snake game for Android, AIDE-compatible.

Features
- Java Android project (minSdk 16)
- Portrait orientation
- Fixed 32x32 gameplay grid with a red boundary and dark gray grid lines
- Zoomed camera showing approximately 10 cells horizontally, centered on the snake, with off-screen food direction arrows
- Added a red boundary around the 32x32 play area; crossing it teleports the snake to the opposite side
- Simple swipe controls (improved: 2-item input queue to avoid dropped inputs and jerky turns)
- Snake and food drawn with shapes (no external assets)
- Local Leaderboard system:
  - LEADERBOARD button on the main menu
  - Persistent score tracking via SharedPreferences
  - Leaderboard overlay with sorting by High Score or Recent Date (toggle) and back navigation
  - Leaderboard entries include the difficulty used for each game
  - Settings menu for customizing head and body colors with hex codes
- Score-based food scaling and multi-food support:
  - Food scaling thresholds: 50score * Math.pow(2, foodCount - 1)
  - Up to 6 active food items on the grid
- Improved input & collision handling:
  - 2-item input queue for responsive controls
  - Prevents instant 180-degree self-collisions when multiple rapid turns occur before a game tick

Changelog
---------

### 1.2.0
- Added a fixed 32x32 gameplay world.
- Added dark gray grid lines with a red boundary around the play space.
- Added a zoomed camera showing approximately 10 cells horizontally.
- Added smooth camera following centered on the snake.
- Added red directional arrows for food outside the visible area.
- Added world-edge teleportation from one side of the play space to the other.
- Clipped the grid and game objects so nothing renders outside the red boundary.

### 1.1.0
- Added a fully in-game settings screen for snake color customization.
- Added head and body hex color inputs with live color previews.
- Added persistent color preferences.
- Replaced the native color dialog with the game-styled drawn UI.
- Fixed Android keyboard integration for color input.
- Preserved leaderboard difficulty display and backward compatibility with older scores.

AIDE instructions
1. In AIDE, choose "Import Project" -> "Open existing project" and point to this repository folder.
2. If AIDE asks to convert to a Gradle project, you can keep it as a standard Android project for quick editing and compiling.
3. Open MainActivity.java or run the project to build and install on your device.
