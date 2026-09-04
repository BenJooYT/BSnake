# Changelog

All notable changes to BSnake are documented here. See [README.md](README.md) for
the full feature history; this file tracks the most recent releases.

## 1.8.0 — Phantom & Summoner

**New Boss: PHANTOM**
- A cyan boss that cycles between **tangible** and **intangible** phases
- While intangible it passes through everything and cannot be damaged; a phase-shift flash and sound telegraph the change

**New Boss: SUMMONER**
- An orange boss that periodically spawns up to **5 chasing minion snakes** which attack the player and shrink them on contact
- All minions die with the boss and drop a score trail to collect
- Minions can be defeated **individually** — strike one with your head to kill it and earn score
- Minion AI no longer reverses 180 degrees; they keep moving forward and turn around obstacles instead of flipping into their own body

**Stability**
- Fixed the boss not spawning when the target score is reached — a single-player regression from the multiplayer world-authority refactor
- Multiplayer client's own snake no longer freezes from a transient host alive flag
- Multiplayer client falls back to the game-over panel if the host's game-over message is delayed or lost
- Dev boss selector now cycles PHANTOM and SUMMONER without crashing

**Multiplayer**
- Host + client run the identical simulation; world objects (boss, walls, food, minions) stay host-authoritative while each side owns its own snake (peer prediction)

## 1.7.2 — Cards

**New Boss: MIRROR**
- A new purple boss that hunts down fruit and converts whatever it eats into **purple mirror fruit** (capped at 6 on the board)
- Purple mirror fruit is a gamble: it grants **+3 score and +2 growth**, but flips your controls for **5 seconds** once eaten
- Mirror only inverts *future* inputs, and a mirrored turn can never force your snake to reverse into itself
- Clear feedback while you're debuffed: a purple edge vignette, your snake's body tints toward purple, and a reversal-glyph icon with a countdown banner

**Boss balance**
- Boss spawn gap now grows **quadratically** as you defeat bosses (`100 → 400` cap), so each successive boss takes more farming to reach instead of coming at a flat interval

**Developer mode**
- The dev boss selector now cycles through RANDOM / CHASER / WALL / HEALER / **MIRROR**

**Combat & presentation**
- Added a cinematic boss-death sequence: hit stop, camera lunge and zoom, a shockwave ring, chromatic aberration, rotating debris, and glowing embers - with a smooth pan from your snake to the boss (synced in multiplayer)
- Added off-screen boss and food arrows so you can always tell what's coming from afar; the boss arrow is split into head/body halves and the old head glow is removed
- Added a reward-style post-boss **upgrade card** selection screen for Arcade runs, with auto-fit text so card flavor never overflows
- Score popups now show the actual points gained (+1 / +5 / +2) instead of a rounded total

**Stability**
- Audio playback moved off the game thread to avoid stutter
- Full tail-loop into your own tail is legal unless the snake would grow that tick
- The boss-death cinematic uses the classic zoom regardless of camera mode
- Fixed a crash where the upgrade-card offer list could change mid-draw on the render thread

## 1.7.1 — Cards

**New Boss: MIRROR**
- A new purple boss that hunts down fruit and converts whatever it eats into **purple mirror fruit** (capped at 6 on the board)
- Purple mirror fruit is a gamble: it grants **+3 score and +2 growth**, but flips your controls for **5 seconds** once eaten
- Mirror only inverts *future* inputs, and a mirrored turn can never force your snake to reverse into itself
- Clear feedback while you're debuffed: a purple edge vignette, your snake's body tints toward purple, and a reversal-glyph icon with a countdown banner

**Boss balance**
- Boss spawn gap now grows **quadratically** as you defeat bosses (`100 → 400` cap), so each successive boss takes more farming to reach instead of coming at a flat interval

**Developer mode**
- The dev boss selector now cycles through RANDOM / CHASER / WALL / HEALER / **MIRROR**

**Combat & presentation**
- Added a cinematic boss-death sequence: hit stop, camera lunge and zoom, a shockwave ring, chromatic aberration, rotating debris, and glowing embers — with a smooth pan from your snake to the boss (synced in multiplayer)
- Added off-screen boss and food arrows so you can always tell what's coming from afar; the boss arrow is split into head/body halves and the old head glow is removed
- Added a reward-style post-boss **upgrade card** selection screen for Arcade runs, with auto-fit text so card flavor never overflows
- Score popups now show the actual points gained (+1 / +5 / +2) instead of a rounded total

**Stability**
- Audio is now played off the game thread to avoid stutter and dropped game-loop ticks
- Made a full tail-loop into your own tail legal (it only kills you if the snake would grow that tick)
- The boss-death cinematic uses the classic zoom regardless of camera mode
- Fixed a crash where the upgrade-card offer list could change on the render thread mid-draw, throwing `IndexOutOfBoundsException` on the upgrade screen

### 1.7.0 — The Big Beautiful August Update

**Challenges**
- **Arcade challenges:** every run starts with 3 randomly selected objectives drawn from a pool of 20 — score goals, length goals, food collection, boss fights, and wall capture
- **Challenge HUD:** redesigned into a collapsible status-dot strip (top-left) — one colored dot per objective (red untouched, amber in progress, green done, grey failed); tap to expand the full list with live progress and point rewards, tap again to collapse
- **Auto-rotate:** completed or failed objectives linger briefly, then are replaced by a fresh objective not yet used this run (no repeats)
- **Feedback:** completing an objective flashes the screen green, pops the score badge, and plays a chime plus a floating "+X"; failing one flashes red with a dedicated fail sound
- **Direction Lock:** objectives now name the forbidden direction in the description (e.g. "without moving up")
- **Boss Rush:** requires 5 consecutive boss defeats — dying resets the streak
- **Extensible:** all challenge definitions live in a single data file (`ChallengeDefinitions.java`), so new objectives can be added with zero gameplay code changes

**Visuals**
- **Death dissolve:** the snake fades out cell-by-cell with a flashing white/red head for ~1.4s before the game-over panel; the camera freezes at the death site instead of snapping to center
- **Food effects:** fruits scale in with a pulse and soft radial glow, then burst into arcing dots plus an expanding ring when eaten (green for healing fruit, red for normal)
- **Boss spawn telegraph:** a much more transparent red vignette, a pulsing "BOSS INCOMING" banner, and a warning siren for 1 second before a boss appears
- **Boss presence:** bosses spawn with a shockwave ring, white flash, and screen shake; their whole body pulses with a colored aura and their head glows
- **Boss combat:** bosses flash white and burst into particles when hit, and explode into a big double-ring burst with screen shake plus a "BOSS DEFEATED +N" banner when defeated
- **Boss health bar:** a top-center bar tracks the boss's remaining segments against its maximum length
- **Transitions:** full-screen fades between screens; red/green flashes on challenge results
- **Camera:** FIT VERTICAL mode now really works — it fills the screen height exactly and scrolls horizontally

**HUD & Score**
- **Coin meter:** the plain score text is replaced by a coin in your snake's head color (mid-left of the screen) that pops on every point and floats a "+1" when you eat food; multiplayer shows both players' coins plus a gold SUM; DEV mode shows as a small red tag
- **Bigger touch targets:** the pause icon and the collapsed challenge strip each have a larger invisible tap area

**Audio**
- **Full sound redesign:** 7 new synthesized sounds — heal chime, boss warning siren, boss spawn growl, challenge-fail buzz, segment-lost snip, game-over descent, and a pause/resume blip — plus reworked eat (popping crunch), boss hit (punchy sub-thud), boss defeat (cascading explosion), wall shatter, challenge-complete arpeggio, and UI click
- **Louder game-over sound**

**Input & Controls**
- **On-screen D-pad:** an optional 4-way direction pad (bottom-center) driven by taps, with a new persisted DIRECTION BUTTONS toggle in Settings; the button for the illegal 180° turn is hidden
- **Forgiving taps:** D-pad presses are hit-tested at the press-down point with a wide 90px tolerance, so a small finger wobble no longer cancels them; swipes that start on the pad are ignored
- **Challenge tab:** tap the collapsed strip to open the list, tap the open panel to close it — swipes still pass through as movement
- **Pause feedback:** pausing and resuming play a dedicated blip

**Stability**
- Multiplayer start, restart, and rematch are deferred to the game thread (no crashes from racing the game loop)
- Game over waits for the death dissolve animation to finish before saving scores
- Stale boss, death, and particle effects are cleared between runs
- Bottom UI stays above the system navigation bar / gesture pill on edge-to-edge devices

### 1.6.2 — Smash Through the Walls
- **Wall Builder counterplay:** a player snake that completely surrounds a connected wall group with a closed loop destroys the whole group
- **Bosses:** wall groups are 8-directionally connected, including across toroidal map edges — every tile must be enclosed before the group is removed (touching, partial surround, and incomplete loops never trigger)
- **Bosses:** destroyed walls trigger the existing wall crumble animation plus a new wall-shatter sound, and stop blocking movement, boss pathing, and food spawning immediately
- **Audio:** verified all boss types (CHASER, WALL_BUILDER, HEALER) consistently play the default boss damage and boss defeat sounds

### 1.6.1 — The Best Patch So Far
- **Multiplayer:** client camera now stays in spectator full view on game over — no snap back to the chosen camera mode
- **Multiplayer:** fixed half-cell grid shift when a snake is dead (dead-snake view camera aligned with the grid)
- **Multiplayer:** game-over screen now reliably appears when both snakes die — host detects client death via empty-body clientState instead of keeping a ghost snake alive
- **Multiplayer:** client prediction no longer masks its own death as an alive empty-body ghost
- **Bosses:** client now sends a `bossHit` packet when it lands a boss head-on; host applies the damage authoritatively and credits the client
- **Bosses:** new HEALER boss — a green gradient snake that stores the normal fruit it eats and releases it as green healing fruit when damaged
- **Bosses:** green healing fruit is edible by players and bosses, grows the snake +2, and doesn't count toward the normal food cap
- **Bosses:** boss-defeat score (+25) and growth now credit the snake that landed the killing blow, not always player 1
- **Bosses:** boss teleport avoids player danger zones (head and body); falls back to a random teleport that avoids the player body
- **Bosses:** hitting a boss head shrinks the player by 3 segments again (skipped on the killing blow — defeat only rewards +25 score / +5 growth)
- **Multiplayer:** ready system fixed — host starts the match only when both players are ready; stale opponent readiness is cleared when a client disconnects

### 1.6.0
- **Classic game mode** — dynamic screen-filling board with static camera, no bosses or walls
- Game mode selection screen redesigned — select Arcade or Classic, see a description, then press PLAY
- Separate Arcade and Classic leaderboards with mode selector tabs
- Last selected game mode is remembered across sessions
- Board dimensions computed dynamically in Classic mode based on device screen size

### 1.5.7
- Multiplayer network ownership refactor: client sends full clientState (body/dir/score/alive) before each tick; host uses body as-is, no local movement of remote snake
- State message streamlined: single snake key (host only), client keeps its own body
- sendSwipe() is now local-only enqueue — no per-swipe network message
- Client camera init in applyState() and start handlers — game starts even if "start" message dropped
- Colors redundantly synced via state() message every tick
- Dead snake body cleared at every death point — no phantom hitbox after visual disappearance
- Remote snake jitter fixed: host no longer moves remote snake independently
- Client prediction no longer runs boss AI, boss spawn, or food refill (host is authoritative for all game state)
- Host removes food at remote snake's current head — food eaten by client properly disappears
- Client game loop ticks independently of host state arrival — no more lag pauses
- isHost set AFTER server/client reference to ensure volatile happens-before ordering
- toggleReady() null-safe guard
- Empty snake body guards in engine update and clientState handler
- Color picker sliders 2x taller with doubled spacing

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
