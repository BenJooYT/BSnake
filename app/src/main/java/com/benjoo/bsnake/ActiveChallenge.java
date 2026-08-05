package com.benjoo.bsnake;

// A single challenge objective selected for the current Arcade run. Holds the
// live progress and completion state; the definition is immutable.
class ActiveChallenge {

    final ChallengeDefinition def;

    int progress = 0;
    boolean completed = false;
    boolean failed = false;   // impossible to finish (e.g. timed out / rule broken)
    long settledAt = -1;      // wall-clock ms when the challenge completed/failed (-1 = still active)

    // Per-challenge runtime state
    int forbiddenDir = -1;        // DIRECTION_LOCK: 0=up 1=right 2=down 3=left
    long edgeWalkerMs = 0;        // EDGE_WALKER: accumulated seconds near the border
    int maxLength = 0;            // TINY_SNAKE: highest length reached this run
    boolean lostSegment = false;  // NO_MISTAKES: has the player lost a segment?

    ActiveChallenge(ChallengeDefinition def) {
        this.def = def;
    }
}
