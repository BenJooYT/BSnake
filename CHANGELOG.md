# Changelog

All notable changes to BSnake are documented here. See [README.md](README.md) for
the full feature history; this file tracks the most recent releases.

## 1.7.1 — The Mirror Patch

**New Boss: MIRROR**
- A new purple boss that hunts down fruit and converts whatever it eats into **purple mirror fruit** (capped at 6 on the board)
- Purple mirror fruit is a gamble: it grants **+3 score and +2 growth**, but flips your controls for **5 seconds** once eaten
- Mirror only inverts *future* inputs, and a mirrored turn can never force your snake to reverse into itself
- Clear feedback while you're debuffed: a purple edge vignette, your snake's body tints toward purple, and a reversal-glyph icon with a countdown banner

**Boss balance**
- Boss spawn gap now grows **quadratically** as you defeat bosses (`100 → 400` cap), so each successive boss takes more farming to reach instead of coming at a flat interval

**Developer mode**
- The dev boss selector now cycles through RANDOM / CHASER / WALL / HEALER / **MIRROR**

**Stability**
- Fixed a crash where the upgrade-card offer list could change on the render thread mid-draw, throwing `IndexOutOfBoundsException` on the upgrade screen
