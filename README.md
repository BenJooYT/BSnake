# BSnake

Simple Snake game for Android, AIDE-compatible.

## Features

- Java Android project (minSdk 16, portrait orientation)
- Fixed 32×32 toroidal grid with red boundary and grid lines
- Three camera modes: CLASSIC_ZOOM, FULL_PLAY_AREA, FIT_VERTICAL
- Swipe controls with 2-item input queue (no dropped inputs or 180° reversals)
- Procedural menu music (Markov chain, C major, 120 BPM) and synthesized SFX
- **Boss Fruit System** — 2×2 purple boss with 5 HP spawns every 125 score; drops golden trail cells on hit; grants +25 score and +5 growth on defeat
- **Multi-food scaling** — up to 6 food items active simultaneously
- **Score/size decoupling** — normal food (+1/+1 growth), trail fruit (+1/+0), boss hit (+5/−3 shrink), boss defeat (+25/+5)
- **Leaderboard** — top 20 entries with score, timestamp, and difficulty; sortable by score or date
- **Live color preview** — hex head/body color input updates swatch in real time
- **Developer mode** — triple-tap the SNAKE title to set a custom starting score (scores not saved)
- **In-app update checker** — fetches version.json from GitHub, prompts to download new APK
- No external assets or libraries — everything drawn with Canvas shapes and synthesized audio

## Changelog

### 1.5.0
- **BOSS SNAKE REWORK:** The boss is now a snake that moves toward food, eats it, and grows. Position your snake to make the boss run into you — head-on or body contact damages it. Each hit removes 2 boss segments; when none remain, it dies. Trail drops under its body when teleporting.

### 1.4.4
- **MANUAL INSTALL REQUIRED:** You must go into your Files app → Downloads to install; auto-install is being worked on
- New dedicated color picker with HSV sliders and live snake preview
- Snake color moved to Settings with a 3-segment preview under the button
- Boss hits now have their own unique damage sound
- Update download now automatically prompts installation when complete

### 1.4.3
- In-game sound effects (eating, damage, boss defeat) now play correctly
- Music no longer resets volume when switching apps
- Audio no longer cuts out after switching apps
- Update download now automatically prompts installation when complete

### 1.4.2
- Host screen shows live status updates instead of a static message
- Join screen lists available games to pick from
- Each host identified by device name
- Automatically enter the lobby after connecting
- Clean disconnect clears all connection data
- Fix: canceling multiplayer no longer carries fake P2 score into singleplayer

### 1.4.1
- Devices can now discover each other in multiplayer
- Both players see the game start at the same time
- Snakes face the right direction on the joiners screen
- Game over message only appears once per game
- Fixed various connectivity and timing issues

### 1.4.0
- Play against friends over WiFi (LAN multiplayer)
- Host runs the game, joiner sees everything in real-time
- Lobby with ready up and force start for the host
- Two snakes on the same board with proper collision rules
- "YOU" label follows your snake
- Both players see each other's chosen colors
- Rematch without reconnecting
- Pause disabled during multiplayer
- Boss defeat sound with echo effect
- Various stability fixes for multiplayer
- Code cleanup

### 1.3.7
- Procedural menu music while browsing menus
- Volume sliders for music and SFX
- Camera mode and volume preferences save between sessions
- Smoother audio during gameplay
- Uses system download manager for APK updates

### 1.3.5
- Checks for updates on GitHub and prompts to download

### 1.3.4
- In-app update checker (checks GitHub for new versions)

### 1.3.3
- Score and snake length are now independent
- Boss avoids snake tiles when moving
- Live color preview while typing hex codes
- Three camera modes to choose from

### 1.3.2
- Live color preview and multiple camera modes

### 1.3.1
- Boss avoids snake tiles when moving

### 1.3.0
- Boss Fruit system (2x2 purple boss with HP and trail cells)
- Developer mode (triple-tap title for custom starting score)

### 1.2.0
- Fixed 32x32 grid with zoomed camera and smooth following
- Food arrows point off-screen, wrap-around edges

### 1.1.0
- Settings screen with snake color customization
- Colors save between sessions

## AIDE Instructions

1. Open AIDE, choose **Import Project** → **Open existing project** and point to this repository.
2. Keep it as a standard Android project (Gradle conversion is optional).
3. Open `MainActivity.java` or run the project to build and install.
