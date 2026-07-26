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

### 1.4.3
- In-game sound effects (eating, damage, boss defeat) now play correctly
- Music no longer resets volume when switching apps
- Audio no longer cuts out after switching apps
- Host screen now shows live connection status
- Join screen lists available games to pick from
- Each host is identified by their device name
- Automatically transition to the lobby after connecting
- Clean disconnect clears all connection data
- Devices can now discover each other in multiplayer
- Both players see the game start at the same time
- Players see correct snake direction on their screen
- Game over notification only appears once per game
- Fixed various connectivity and timing issues

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
- LAN Local Multiplayer over WiFi (host/client via TCP + NSD discovery)
- Host game simulation is authoritative — client renders received STATE snapshots
- Lobby system with ready/un-ready and force-start for host
- Multi-snake support: 2 snakes on the same board with head-on/body collision rules
- "YOU" label on local player's snake, tweens smoothly with the head
- Color sharing: both players see each other's chosen snake colors
- Rematch button reuses existing socket connection
- Pause functionality disabled during multiplayer matches
- Fix: host sends gameOver message to client at match end
- Fix: client reads snake alive flags from state messages
- Fix: client resets game state on rematch (no leftover data)
- Fix: asymmetric snake-vs-snake body collision (dead bodies still block)
- Fix: GameClient.running flag not set after connection
- Fix: client interpolation t cycles 0–1 every tick for smooth rendering
- Fix: drawColorField uses uiCellSize instead of cellSize
- Fix: AudioTrack lifecycle safety (regeneration after surface destroy)
- Fix: menu music rest-index crash and Markov chain corruption from rests
- Fix: menu music beatPos initialized correctly for accurate chord/bar detection
- Boss defeat sound: 7 echo repeats at 400ms with exponential decay
- Code size reduction via dead-code elimination and expression simplification

### 1.3.7
- Procedural menu music (Markov chain, C major, 120 BPM) and synthesized SFX
- Music and SFX volume sliders in settings with drag-to-adjust
- Persistent camera mode and volume preferences
- Default music volume 25%, SFX volume 50% on first-ever startup
- Fix: pre-allocate AudioTrack objects to eliminate stutter during gameplay
- Fix: use DownloadManager instead of browser ACTION_VIEW for APK downloads

### 1.3.5
- Added in-app update prompt that checks GitHub for new versions

### 1.3.4
- Bump to v1.3.4; add in-app update prompt with GitHub version check

### 1.3.3
- Score/size decoupling: score and snake length are now independent
- Boss AI: movement avoids snake tiles; teleport avoids snake only
- Live color preview: swatch updates immediately as player types hex
- Three camera modes: CLASSIC_ZOOM, FULL_PLAY_AREA, FIT_VERTICAL

### 1.3.2
- Live color preview and multiple camera modes

### 1.3.1
- Boss AI improvement: movement avoids snake tiles

### 1.3.0
- Boss Fruit System (2×2 purple boss, 5 HP, trail cells)
- Developer Mode (triple-tap title, custom starting score)

### 1.2.0
- Fixed 32×32 world, grid lines, zoomed camera with smooth following
- Off-screen food direction arrows, toroidal teleportation

### 1.1.0
- Settings screen for snake color customization with live hex preview
- Persistent color preferences via SharedPreferences

## AIDE Instructions

1. Open AIDE, choose **Import Project** → **Open existing project** and point to this repository.
2. Keep it as a standard Android project (Gradle conversion is optional).
3. Open `MainActivity.java` or run the project to build and install.
