package com.benjoo.bsnake;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Random;

// Procedural menu music using a 2nd-order Markov chain with full music theory.
// Key: C Major, 120 BPM, 8-bar phrases, I-V-vi-IV chord progression.
// Melody uses C4-C5 (1 octave), with a bass drone on chord roots.
public class MenuMusic {

    private static final int SAMPLE_RATE = 22050;

    // ----- C Major scale (1 octave: C4–C5) -----
    private static final double[] FREQUENCIES = {
            261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25
    };
    private static final int NUM_NOTES = 8;

    // Semitone distance of each scale degree from C4
    private static final int[] SEMITONES = {0, 2, 4, 5, 7, 9, 11, 12};

    // ----- Chord progression I-V-vi-IV (2 bars each = 8 bars) -----
    // Each chord is an array of scale indices for root, third, fifth
    private static final int[][] CHORDS = {
            {0, 2, 4},  // C major (I):  C, E, G
            {4, 6, 1},  // G major (V):  G, B, D
            {5, 0, 2},  // A minor (vi): A, C, E
            {3, 5, 0}   // F major (IV): F, A, C
    };
    private static final int CHORDS_IN_PROGRESSION = 4;

    // ----- Rhythm / timing -----
    private static final int BPM = 120;
    private static final int BEAT_MS = 60000 / BPM;             // 500ms
    private static final int BEATS_PER_BAR = 4;
    private static final int BARS = 8;
    private static final int TOTAL_BEATS = BARS * BEATS_PER_BAR; // 32

    // Duration values in beats: eighth, quarter, half, dotted-quarter
    private static final double[] DURATION_BEATS = {0.5, 1.0, 2.0, 1.5};
    private static final int NUM_DURATIONS = 4;

    // Rhythm Markov chain: [previous_duration_index][next_duration_index] = weight
    // Rows: eighth, quarter, half, dotted-quarter
    private static final double[][] RHYTHM_WEIGHTS = {
            {0.10, 0.50, 0.10, 0.30},  // after eighth
            {0.30, 0.20, 0.30, 0.20},  // after quarter
            {0.40, 0.40, 0.10, 0.10},  // after half
            {0.25, 0.35, 0.20, 0.20},  // after dotted-quarter
    };

    // ----- built-in leap limit (max semitones) -----
    private static final int MAX_LEAP_SEMITONES = 7;

    private AudioTrack audioTrack;
    private boolean playing = false;

    // 2nd-order Markov chain weights: [prev1][prev2][next]
    private double[][][] melodyWeights;

    public MenuMusic() {
        try {
            buildMelodyWeights();
            generate();
        } catch (Exception e) {
            Log.e("MenuMusic", "Failed to generate music", e);
        }
    }

    // Build the 2nd-order Markov transition weights using music-theory rules.
    // The weights are computed once, then biased per-chord at generation time.
    private void buildMelodyWeights() {
        melodyWeights = new double[NUM_NOTES][NUM_NOTES][NUM_NOTES];
        int maxScaleSteps = 4; // prefer steps ≤ 4 scale degrees

        for (int p1 = 0; p1 < NUM_NOTES; p1++) {
            for (int p2 = 0; p2 < NUM_NOTES; p2++) {
                double[] weights = melodyWeights[p1][p2];
                double total = 0;

                for (int next = 0; next < NUM_NOTES; next++) {
                    int steps = Math.abs(next - p2);
                    int semis = Math.abs(SEMITONES[next] - SEMITONES[p2]);

                    double w;

                    // Leaps larger than MAX_LEAP_SEMITONES are heavily discouraged
                    if (semis > MAX_LEAP_SEMITONES) {
                        w = 0.01;
                    } else if (steps == 0) {
                        // Repeat previous note — moderately common
                        w = 1.5;
                    } else if (steps == 1) {
                        // Stepwise motion — most common
                        w = 4.0;
                    } else if (steps == 2) {
                        // Small skip — common
                        w = 2.5;
                    } else if (steps <= maxScaleSteps) {
                        // Larger skip — less common
                        w = 1.0;
                    } else {
                        // Very large skip — rare
                        w = 0.15;
                    }

                    // Slight bias toward tonic and dominant
                    if (next == 0) w *= 1.3;
                    else if (next == 4) w *= 1.15;

                    weights[next] = w;
                    total += w;
                }

                // Normalise so each row sums to 1
                for (int next = 0; next < NUM_NOTES; next++) {
                    weights[next] /= total;
                }
            }
        }
    }

    // Generate the PCM buffer and create the looping AudioTrack.
    @SuppressWarnings("deprecation")
    private void generate() {
        Random rand = new Random(42); // fixed seed = same music every time (deterministic)

        // ----- duration sequence via rhythm Markov chain -----
        double[] totalBeats = {0};
        int[] durIndices = generateDurations(rand, totalBeats);
        int noteCount = durIndices.length;

        // ----- melody via 2nd-order Markov chain with chord bias -----
        int[] melody = generateMelody(rand, noteCount, durIndices);

        // ----- bass: one root per chord (2 bars each) -----
        int bassChanges = CHORDS_IN_PROGRESSION;
        int[] bassRoots = new int[bassChanges];
        for (int c = 0; c < bassChanges; c++) {
            bassRoots[c] = CHORDS[c][0]; // scale-index of chord root
        }
        double beatsPerBass = TOTAL_BEATS / (double) bassChanges; // 8 beats each

        // ----- totals for PCM buffer -----
        int totalDurationMs = (int) (totalBeats[0] * BEAT_MS);
        int totalSamples = (int) ((long) totalDurationMs * SAMPLE_RATE / 1000);
        short[] buffer = new short[totalSamples];

        // Write melody
        double beatPos = 0;
        for (int i = 0; i < noteCount; i++) {
            double durBeats = DURATION_BEATS[durIndices[i]];
            int samples = (int) ((long) (durBeats * BEAT_MS) * SAMPLE_RATE / 1000);
            int offset = (int) ((long) (beatPos * BEAT_MS) * SAMPLE_RATE / 1000);
            if (melody[i] >= 0 && offset + samples <= totalSamples) {
                generateTone(buffer, offset, samples,
                        FREQUENCIES[melody[i]], 0.45);
            }
            beatPos += durBeats;
        }

        // Write bass (chord roots, quiet, mixed under melody)
        for (int c = 0; c < bassChanges; c++) {
            int startSample = (int) ((long) (c * beatsPerBass * BEAT_MS) * SAMPLE_RATE / 1000);
            int endSample = (int) ((long) ((c + 1) * beatsPerBass * BEAT_MS) * SAMPLE_RATE / 1000);
            int samples = Math.min(endSample, totalSamples) - startSample;
            if (samples > 0) {
                generateTone(buffer, startSample, samples,
                        FREQUENCIES[bassRoots[c]] * 0.5, // one octave down
                        0.12);
            }
        }

        // ----- create looping AudioTrack -----
        int bufferSize = buffer.length * 2;
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STATIC
        );
        audioTrack.write(buffer, 0, buffer.length);
        audioTrack.setLoopPoints(0, buffer.length, -1);
    }

    // Generate a sequence of duration indices using the rhythm Markov chain.
    // Returns the indices and sets totalBeats[0] to the total beats used.
    private int[] generateDurations(Random rand, double[] totalBeatsOut) {
        int[] indices = new int[256]; // generous upper bound
        int count = 0;
        double beats = 0;
        int prevDur = 1; // start on quarter

        while (beats < TOTAL_BEATS - 0.25) {
            double remaining = TOTAL_BEATS - beats;

            // Pick next duration from rhythm chain
            double r = rand.nextDouble();
            double cum = 0;
            int chosen = NUM_DURATIONS - 1;
            for (int j = 0; j < NUM_DURATIONS; j++) {
                cum += RHYTHM_WEIGHTS[prevDur][j];
                if (r < cum) { chosen = j; break; }
            }

            double durBeats = DURATION_BEATS[chosen];
            // If it won't fit, pick the largest that does
            if (durBeats > remaining + 0.01) {
                boolean found = false;
                for (int j = NUM_DURATIONS - 1; j >= 0; j--) {
                    if (DURATION_BEATS[j] <= remaining + 0.01) {
                        chosen = j;
                        durBeats = DURATION_BEATS[j];
                        found = true;
                        break;
                    }
                }
                if (!found) break; // shouldn't happen with 0.5 min
            }

            indices[count] = chosen;
            count++;
            beats += durBeats;
            prevDur = chosen;
        }

        totalBeatsOut[0] = beats;
        int[] result = new int[count];
        System.arraycopy(indices, 0, result, 0, count);
        return result;
    }

    // Generate melody notes using the 2nd-order Markov chain with per-chord bias,
    // strong-beat accenting, and a cadence on the tonic.
    private int[] generateMelody(Random rand, int noteCount, int[] durIndices) {
        int[] melody = new int[noteCount];
        if (noteCount == 0) return melody;

        melody[0] = rand.nextInt(NUM_NOTES);
        int p1 = melody[0];
        int[] firstChord = CHORDS[0];
        int p2 = (noteCount > 1) ? pickNextNote(rand, p1, p1, firstChord, true, false) : 0;
        if (noteCount > 1) melody[1] = p2;

        double beatPos = noteCount > 1 ? DURATION_BEATS[durIndices[0]] : 0;
        if (noteCount > 1) beatPos += DURATION_BEATS[durIndices[1]];
        int chordIndex = -1;

        for (int i = 2; i < noteCount; i++) {
            double durBeats = DURATION_BEATS[durIndices[i]];
            double nextBeat = beatPos + durBeats;

            // Determine which chord we're in (each chord spans TOTAL_BEATS / 4 beats)
            int bar = (int) (beatPos / BEATS_PER_BAR);
            int newChord = bar / 2; // 2 bars per chord
            if (newChord >= CHORDS_IN_PROGRESSION) newChord = CHORDS_IN_PROGRESSION - 1;

            boolean chordChanged = newChord != chordIndex;
            chordIndex = newChord;

            // Beat within the bar for strong-beat detection
            double beatInBar = beatPos % BEATS_PER_BAR;
            boolean strongBeat = (beatInBar < 0.75); // first half of beat 0

            // On chord change, reset context to avoid stale state
            if (chordChanged && i > 2) {
                p1 = melody[i - 1];
                p2 = (i >= 2) ? melody[i - 2] : p1;
            }

            int[] chordTones = CHORDS[chordIndex];

            int next = pickNextNote(rand, p2, p1, chordTones, strongBeat,
                    i >= noteCount - 3); // cadence zone

            // Occasionally repeat previous note (motif repetition)
            if (rand.nextDouble() < 0.08 && i > 0) {
                next = melody[i - 1];
            }

            // Cadence: last 1-2 notes should land on tonic
            if (nextBeat >= TOTAL_BEATS - 1.5 || i >= noteCount - 2) {
                next = 0; // C4 (tonic)
            }

            // Occasional rest (~10%) — set melody to -1 but don't update p1/p2
            if (i > 0 && rand.nextDouble() < 0.10 && nextBeat < TOTAL_BEATS - 1.5) {
                melody[i] = -1;
            } else {
                melody[i] = next;
            }
            p2 = p1;
            p1 = melody[i] >= 0 ? melody[i] : p1;
            beatPos += durBeats;
        }

        return melody;
    }

    // Select the next note using the 2nd-order weights, biased by chord and beat.
    private int pickNextNote(Random rand, int prev1, int prev2,
                             int[] chordTones, boolean strongBeat, boolean cadence) {
        double[] base = melodyWeights[prev2][prev1];
        double[] adjusted = new double[NUM_NOTES];
        double total = 0;

        for (int n = 0; n < NUM_NOTES; n++) {
            double w = base[n];

            boolean isTone = false;
            for (int ct : chordTones) {
                if (n == ct) { isTone = true; break; }
            }
            if (strongBeat) {
                w *= isTone ? 3.5 : 0.25;
            } else if (isTone) {
                w *= 1.4;
            }

            // Cadence: push hard toward tonic
            if (cadence) {
                w *= (n == 0) ? 8.0 : 0.3;
            }

            adjusted[n] = w;
            total += w;
        }

        double r = rand.nextDouble();
        double cum = 0;
        for (int n = 0; n < NUM_NOTES; n++) {
            cum += adjusted[n] / total;
            if (r < cum) return n;
        }
        return NUM_NOTES - 1;
    }

    // Generate a triangle-wave tone (+ soft octave) with attack/release envelope,
    // mixing into the buffer at the given offset.
    private void generateTone(short[] buffer, int offset, int numSamples,
                              double freqHz, double volume) {
        for (int i = 0; i < numSamples && offset + i < buffer.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double phase = (t * freqHz) % 1.0;

            double sample = 2.0 * Math.abs(2.0 * phase - 1.0) - 1.0;

            double octPhase = (t * freqHz * 2.0) % 1.0;
            sample += 0.3 * (2.0 * Math.abs(2.0 * octPhase - 1.0) - 1.0);

            int attack = Math.max(1, numSamples / 14);
            int release = Math.max(1, numSamples / 10);
            double envelope = 1.0;
            if (i < attack) envelope = (double) i / attack;
            if (i > numSamples - release) envelope = (double) (numSamples - i) / release;

            double mixed = (buffer[offset + i] / (double) Short.MAX_VALUE)
                    + sample * envelope * volume;
            mixed = Math.max(-1.0, Math.min(1.0, mixed));
            buffer[offset + i] = (short) (mixed * Short.MAX_VALUE);
        }
    }

    public void start() {
        try {
            if (audioTrack == null) generate();
            if (audioTrack != null && !playing) {
                audioTrack.reloadStaticData();
                audioTrack.play();
                playing = true;
            }
        } catch (Exception e) {
            Log.e("MenuMusic", "Failed to start", e);
        }
    }

    public void stop() {
        if (audioTrack != null && playing) {
            try {
                audioTrack.pause();
                playing = false;
            } catch (Exception e) {
                Log.e("MenuMusic", "Failed to stop", e);
            }
        }
    }

    public void setVolume(float vol) {
        if (audioTrack != null) {
            try {
                audioTrack.setVolume(vol);
            } catch (Exception e) {
                Log.e("MenuMusic", "Failed to set volume", e);
            }
        }
    }

    public boolean isPlaying() {
        return playing;
    }

    public void release() {
        stop();
        if (audioTrack != null) {
            try {
                audioTrack.release();
            } catch (Exception e) {
                Log.e("MenuMusic", "Failed to release", e);
            }
            audioTrack = null;
        }
    }
}
