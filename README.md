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

### 1.5.6
- Dev mode: force boss type selection (RANDOM / CHASER / WALL), show boss pathfinding toggle
- Boss AI: player body avoidance via scoring penalties (not hard block) — boss prefers to avoid but can still be baited into body contact
- Boss adjacent-to-player scoring penalty to prevent cornering
- Multiplayer: TCP_NODELAY on both sockets — fixes client input not reaching host
- Multiplayer: volatile writer in GameServer/GameClient — fixes ready messages silently dropping
- Color sync: hello message now sends bodyColor, stored as clientBodyColor, used in resetGame()
- Lobby UI redesigned: player names on left, snake previews with actual colors beside them, ready status on right

### 1.5.5
- Menu restructure: Main menu now has PLAY button leading to PLAY_MENU (SINGLEPLAYER / MULTIPLAYER / BACK)
- Singleplayer opens MODE_SELECT screen with ARCADE mode (the original game)
- Added PLAY_MENU and MODE_SELECT states

### 1.5.4
- Boss AI overhaul: evasion with danger radius (7 cells), 40/60 evade/task blend, turn speed limits, hesitation (10%), and imperfect moves (12%)
- Fixed boss circling food: alignment bonus (40) now outweighs turn penalty (10)
- Boss always moves on tick: brute-force fallback when all scored candidates are blocked
- Boss trail now spawns at correct length (before segments are removed on damage)
- Wall placement range increased 3→6; walls avoid map border (-20 score penalty)
- Food thresholds changed: 50, 175, 375, 550, 825 (was exponential 50/100/200/400/800)
- App switching no longer resets to main menu — preserves PLAYING, PAUSED, and GAME_OVER states

### 1.5.3
- New **Wall Builder** boss type — orange body, bright blue head, places destructible red walls
- Walls flash a preview tile 0.5–1s before placement, grow in from the ground, and crumble into particles when the boss is defeated
- Wall placement within 6 cells of boss body, **3 walls at a time** (difficulty-scaled interval and cap)
- Boss AI targets the closest player or food, scores wall positions for tactical trapping (ahead of player direction, near borders/walls, avoids trapping)
- Wall collision kills the player; walls persist until boss defeat
- Each boss has 40% chance of being Wall Builder, 60% chaser
- Boss spawn interval reduced from 125 → 100 score
- Boss trail fruit no longer increases snake size (still gives +1 score)
- Full multiplayer sync — walls, preview state, and boss type serialized over network

### 1.5.2
- Fixed multiplayer lobby player labels (host = Player 1, client = Player 2)
- Fixed host game loop so snakes actually move in multiplayer
- Fixed game auto-start when client readies after host

### 1.5.1
- Multiplayer discovery now uses direct UDP multicast (more reliable than NSD)
- Score display shows YOU / PARTNER / SUM in multiplayer
- Boss spawn and food scaling use combined player score
- Boss body now kills the player on contact (only head-on damages the boss)
- Boss damage sound fixed
- Boss moves faster and avoids its own body

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
