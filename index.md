# BSnake Feature Index

A complete overview of how every feature in BSnake works.

---

# 🎮 Game Modes

## Arcade Mode

The main BSnake experience.

Features:

- Fixed 32×32 grid
- Boss battles
- Food scaling
- Score progression
- Camera movement
- Special mechanics

This is the mode where the game gets harder over time and introduces new challenges.

---

## Classic Mode

A pure Snake experience.

Features:

- Full screen board
- Static camera
- No bosses
- No walls
- No special mechanics

Basically normal Snake, but using the entire screen instead of the original 32×32 grid.

---

# 📷 Camera System

BSnake has three camera modes.

## CLASSIC_ZOOM

The original camera mode.

- Follows the snake
- Zooms into the 32×32 board
- Keeps the classic BSnake feeling

---

## FULL_PLAY_AREA

Shows the entire playable area.

- No camera movement
- Entire board visible at once

Useful for players who prefer seeing everything.

---

## FIT_VERTICAL

A balanced camera mode.

- Fits the board vertically
- Shows more than CLASSIC_ZOOM
- Still keeps the game readable

---

# 🐍 Controls

BSnake uses swipe controls.

Features:

- 2-item input queue
- Prevents dropped inputs
- Prevents 180° direction reversals

Example:

Swipe Right → Swipe Up → Swipe Left

The game remembers the inputs instead of ignoring fast swipes.

---

# 🍎 Food System

## Normal Food

The standard food item.

Rewards:

- +1 score
- +1 growth

Score and snake size are separate systems.

---

## Trail Fruit

Dropped by bosses.

Rewards:

- +1 score
- +0 growth

Allows score progression without making the snake too large.

---

## Multi-Food Scaling

Food count increases as the player progresses.

Features:

- Multiple food items can exist at once
- Maximum of 6 active food items
- Makes higher-score gameplay faster

---

# 👑 Boss System

Bosses are special enemy snakes that appear during Arcade mode.

Bosses can:

- Move around the map
- Interact with food
- Damage the player
- Have unique abilities

Boss spawning:

Every 100 score

---

# 🟣 Boss Fruit

The original boss type.

## Appearance

- Purple snake

## Mechanics

- 5 segments; each hit removes 2
- Head-on collisions damage the boss
- Body collisions damage the player

## Rewards

Defeat:

+25 score +5 growth

Normal hit:

+5 score -3 player segments

---

# 🟧 Wall Builder Boss

A boss focused on creating obstacles.

## Appearance

- Orange body
- Bright blue head

## Ability

Creates red destructible walls.

Walls:

- Show a preview before spawning
- Grow from the ground
- Break into particles when destroyed

## AI

The boss:

- Predicts player movement
- Places tactical walls
- Avoids impossible traps

---

# 🟢 HEALER Boss

A support-focused boss.

## Appearance

- Green gradient snake
- Healing-themed design

## Mechanics

The HEALER stores normal food — and still grows +1 segment while storing it.

Flow:

Eat normal food ↓ Store it + grow ↓ Take damage ↓ Release healing fruit

## Healing Fruit

Can be eaten by:

- Players
- Bosses

Effects:

+2 growth

Does not count toward the normal food limit.

Spawns at least 16 cells away from the boss head after it teleports on damage.

---

# 🤖 Boss AI

Bosses use scoring-based AI.

They consider:

- Player position
- Food locations
- Player body danger zones
- Walls
- Their own movement

Boss behavior includes:

- Chasing
- Avoiding danger
- Tactical movement
- Imperfect decisions

The goal is to feel intelligent without becoming impossible.

---

# 🧱 Wall System

Walls are created by the Wall Builder boss.

Features:

- Red world-style walls
- Placement preview
- Collision detection
- Destruction effects
- **Wall capture** — a player snake that completely surrounds a connected wall group with a closed loop destroys it (8-directional connectivity, toroidal edges supported)
- Captured walls stop blocking movement, boss pathing, and food spawning immediately

Walls otherwise remain until the boss is defeated.

---

# 🌐 Multiplayer

BSnake supports LAN multiplayer.

## Features

- Two players on the same board
- Player colors synced
- Ready system
- Rematches
- Multiplayer bosses
- Multiplayer leaderboards

---

# 🔌 Multiplayer Networking

BSnake uses host authority.

## Host controls:

- Boss AI
- Food spawning
- Collisions
- World state

## Client sends:

- Snake movement
- Player state
- Boss hits

This keeps both players synchronized.

---

# 🏆 Leaderboards

Stores:

- Top 20 scores
- Timestamp
- Difficulty
- Game mode

Sorting options:

- Score
- Date

Arcade and Classic have separate leaderboards.

---

# 🎨 Customization

## Snake Colors

Players can customize their snake.

Features:

- Head color
- Body color
- HSV sliders
- Hex input
- Live preview

Colors are saved between sessions.

---

# 🛠 Developer Mode

Hidden testing mode.

## Activation

Triple-tap the SNAKE title.

## Features

Allows:

- Custom starting score
- Forcing a specific boss type (Random / Chaser / Wall Builder / Healer)
- Toggling boss pathfinding visualization

Useful for testing:

- Boss spawning
- Progression
- Scaling

Developer mode scores are not saved.

---

# 🔄 Update Checker

BSnake can update itself through GitHub.

Process:

Check version.json ↓ Compare versions ↓ Download APK ↓ Install update

No external update server required.

---

# 🔊 Audio System

BSnake uses fully generated audio.

No audio files are included.

Features:

- Procedural menu music
- Eating sounds
- Damage sounds
- Boss defeat sounds

## Music

Generated using:

- Markov chains
- C major scale
- 120 BPM

---

# 📱 Technical Details

## Project

- Java Android project
- minSdk 16
- Portrait orientation
- Canvas rendering

## Dependencies

None.

Everything is created using:

- Android Canvas
- Custom rendering
- Synthesized audio

---

# 📜 Version Overview

## 1.6.x

Major updates:

- Wall capture — destroy Wall Builder walls by fully surrounding them
- HEALER boss
- Multiplayer improvements
- Better boss synchronization
- Classic mode

---

## 1.5.x

Major updates:

- Wall Builder boss
- LAN multiplayer
- Boss AI improvements

---

## 1.4.x

Major updates:

- Multiplayer foundation
- Audio system
- Color customization
- Update system

---

## 1.3.x

Major updates:

- Boss Fruit system
- Developer mode
- Camera modes
- GitHub updater

---

## 1.2.x

Major updates:

- Fixed 32×32 grid
- Zoomed camera
- Food indicators

---

## 1.1.x

Major updates:

- Settings system
- Snake customization
- Saved colors