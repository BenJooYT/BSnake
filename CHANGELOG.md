# Changelog

All notable changes to BSnake are documented here. See [README.md](README.md) for
the full feature history; this file tracks the most recent releases.

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
