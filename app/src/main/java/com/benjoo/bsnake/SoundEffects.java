package com.benjoo.bsnake;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Random;

public class SoundEffects {

    private static final int SAMPLE_RATE = 22050;
    private static final int CLICK_MS = 55;
    private static final int CRUNCH_MS = 95;
    private static final int HEAL_MS = 160;
    private static final int DAMAGE_MS = 120;
    private static final int BOSS_DAMAGE_MS = 450;
    private static final int BOSS_DEFEAT_MS = 2600;
    private static final int BOSS_WARNING_MS = 1000;
    private static final int BOSS_SPAWN_MS = 700;
    private static final int WALL_DESTROY_MS = 260;
    private static final int CHALLENGE_MS = 520;
    private static final int CHALLENGE_FAIL_MS = 450;
    private static final int SEGMENT_LOST_MS = 180;
    private static final int DEATH_MS = 900;
    private static final int PAUSE_MS = 150;
    private static final int UPGRADE_MS = 650;
    private static final int UPGRADE_PICK_MS = 220;

    private short[] clickBuffer;
    private short[] crunchBuffer;
    private short[] healBuffer;
    private short[] bossDamageBuffer;
    private short[] bossDefeatBuffer;
    private short[] bossWarningBuffer;
    private short[] bossSpawnBuffer;
    private short[] wallDestroyBuffer;
    private short[] challengeBuffer;
    private short[] challengeFailBuffer;
    private short[] segmentLostBuffer;
    private short[] deathBuffer;
    private short[] pauseBuffer;
    private short[] upgradeBuffer;
    private short[] upgradePickBuffer;

    private AudioTrack clickTrack, crunchTrack, healTrack, bossDamageTrack, bossDefeatTrack,
            bossWarningTrack, bossSpawnTrack, wallDestroyTrack, challengeTrack,
            challengeFailTrack, segmentLostTrack, deathTrack, pauseTrack, upgradeTrack,
            upgradePickTrack;
    private float volume = 1.0f;
    private boolean muted;

    public SoundEffects() {
        generateClick();
        generateCrunch();
        generateHeal();
        generateBossDamage();
        generateBossDefeat();
        generateBossWarning();
        generateBossSpawn();
        generateWallDestroyed();
        generateChallengeComplete();
        generateChallengeFailed();
        generateSegmentLost();
        generateDeath();
        generatePause();
        generateUpgrade();
        generateUpgradePick();
        initTracks();
    }

    @SuppressWarnings("deprecation")
    private AudioTrack createTrack(int bufSize) {
        return new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
                AudioTrack.MODE_STATIC
        );
    }

    private void initTracks() {
        clickTrack = createTrack(clickBuffer.length * 2);
        clickTrack.write(clickBuffer, 0, clickBuffer.length);
        crunchTrack = createTrack(crunchBuffer.length * 2);
        crunchTrack.write(crunchBuffer, 0, crunchBuffer.length);
        healTrack = createTrack(healBuffer.length * 2);
        healTrack.write(healBuffer, 0, healBuffer.length);
        bossDamageTrack = createTrack(bossDamageBuffer.length * 2);
        bossDamageTrack.write(bossDamageBuffer, 0, bossDamageBuffer.length);
        bossDefeatTrack = createTrack(bossDefeatBuffer.length * 2);
        bossDefeatTrack.write(bossDefeatBuffer, 0, bossDefeatBuffer.length);
        bossWarningTrack = createTrack(bossWarningBuffer.length * 2);
        bossWarningTrack.write(bossWarningBuffer, 0, bossWarningBuffer.length);
        bossSpawnTrack = createTrack(bossSpawnBuffer.length * 2);
        bossSpawnTrack.write(bossSpawnBuffer, 0, bossSpawnBuffer.length);
        wallDestroyTrack = createTrack(wallDestroyBuffer.length * 2);
        wallDestroyTrack.write(wallDestroyBuffer, 0, wallDestroyBuffer.length);
        challengeTrack = createTrack(challengeBuffer.length * 2);
        challengeTrack.write(challengeBuffer, 0, challengeBuffer.length);
        challengeFailTrack = createTrack(challengeFailBuffer.length * 2);
        challengeFailTrack.write(challengeFailBuffer, 0, challengeFailBuffer.length);
        segmentLostTrack = createTrack(segmentLostBuffer.length * 2);
        segmentLostTrack.write(segmentLostBuffer, 0, segmentLostBuffer.length);
        deathTrack = createTrack(deathBuffer.length * 2);
        deathTrack.write(deathBuffer, 0, deathBuffer.length);
        pauseTrack = createTrack(pauseBuffer.length * 2);
        pauseTrack.write(pauseBuffer, 0, pauseBuffer.length);
        upgradeTrack = createTrack(upgradeBuffer.length * 2);
        upgradeTrack.write(upgradeBuffer, 0, upgradeBuffer.length);
        upgradePickTrack = createTrack(upgradePickBuffer.length * 2);
        upgradePickTrack.write(upgradePickBuffer, 0, upgradePickBuffer.length);
    }

    // ----- sound generation -----

    // UI tick: a short woody pluck on two overtones.
    private void generateClick() {
        int n = SAMPLE_RATE * CLICK_MS / 1000;
        clickBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.002);
            double d = Math.exp(-t * 95.0);
            double tone = Math.sin(2 * Math.PI * 1150.0 * t) * 0.7
                        + Math.sin(2 * Math.PI * 2300.0 * t) * 0.25;
            double s = tone * a * d * 0.5;
            clickBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Eat pop: a quick upward pitch sweep with a noise crunch and a sparkle.
    private void generateCrunch() {
        int n = SAMPLE_RATE * CRUNCH_MS / 1000;
        crunchBuffer = new short[n];
        Random rng = new Random(1);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.006);
            double d = Math.exp(-t * 30.0);
            double freq = 300.0 + 800.0 * Math.min(1.0, t / 0.055);
            double tone = Math.sin(2 * Math.PI * freq * t) * 0.6;
            double noise = (rng.nextDouble() - 0.5) * 0.35 * Math.exp(-t * 55.0);
            double blip = Math.sin(2 * Math.PI * 1600.0 * t) * Math.exp(-t * 70.0) * 0.3;
            double s = (tone + noise + blip) * a * d * 0.5;
            crunchBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Heal pickup: bright E6->G6 two-note chime.
    private void generateHeal() {
        int n = SAMPLE_RATE * HEAL_MS / 1000;
        healBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.005);
            double d = Math.exp(-t * 15.0);
            double n1 = (t < 0.075 ? 1 : 0) * Math.sin(2 * Math.PI * 1318.51 * t);
            double n2 = (t >= 0.075 ? 1 : 0) * Math.sin(2 * Math.PI * 1567.98 * (t - 0.075));
            double s = (n1 + n2) * a * d * 0.28;
            healBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Boss hit: a punchy sub thud under a falling low-passed noise crunch.
    private void generateBossDamage() {
        int n = SAMPLE_RATE * BOSS_DAMAGE_MS / 1000;
        bossDamageBuffer = new short[n];
        Random rng = new Random(4);
        double lp = 0, bp = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double cutoff = Math.max(60.0, 700.0 * Math.exp(-t * 20.0));
            cutoff = Math.min(cutoff, SAMPLE_RATE * 0.45);
            double f = 2.0 * Math.sin(Math.PI * cutoff / SAMPLE_RATE);
            double noise = (rng.nextDouble() - 0.5) * 0.8;
            double hp = noise - lp - 0.45 * bp;
            bp += f * hp;
            lp += f * bp;
            double thud = Math.sin(2 * Math.PI * (95.0 - 45.0 * t) * t) * Math.exp(-t * 18.0) * 0.9;
            double env = Math.min(1.0, t / 0.004) * Math.exp(-t * 8.0);
            double s = (lp * 0.7 + thud) * env * 0.55;
            bossDamageBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Boss defeat: a cascading multi-hit explosion with a rising finale.
    private void generateBossDefeat() {
        int n = SAMPLE_RATE * BOSS_DEFEAT_MS / 1000;
        double[] mix = new double[n];
        Random rng = new Random(3);
        addDamageHit(mix, 0, 1.00, 1.00, 1.0, rng);
        for (int e = 0; e < 6; e++) {
            int delay = 160 + e * 300;
            double vol = 0.60 * Math.pow(0.6, e);
            double pitch = 0.97 - e * 0.04;
            double alpha = 0.30 * Math.pow(0.45, e);
            addDamageHit(mix, delay, vol, pitch, alpha, rng);
        }
        // final bright boom
        addDamageHit(mix, 1900, 0.9, 1.2, 0.5, rng);
        double max = 0;
        for (double v : mix) if (Math.abs(v) > max) max = Math.abs(v);
        double scale = 0.95 / Math.max(max, 0.001);
        bossDefeatBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            bossDefeatBuffer[i] = (short) (mix[i] * scale * Short.MAX_VALUE);
        }
    }

    private void addDamageHit(double[] mix, int delayMs, double vol, double pitch,
                              double lpAlpha, Random rng) {
        int delaySamples = SAMPLE_RATE * delayMs / 1000;
        int numSamples = SAMPLE_RATE * DAMAGE_MS / 1000;
        double prev = 0;
        for (int i = 0; i < numSamples && delaySamples + i < mix.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.exp(-t * 30.0);
            double thud = Math.sin(2 * Math.PI * 150.0 * pitch * t) * 0.6;
            double crunch = Math.sin(2 * Math.PI * 2000.0 * pitch * t) * 0.4
                          + Math.sin(2 * Math.PI * 3500.0 * pitch * t) * 0.2;
            double noise = (rng.nextDouble() - 0.5) * 0.35;
            double s = (thud + crunch + noise) * envelope * vol * 0.5;
            if (lpAlpha < 1.0) {
                prev = prev + lpAlpha * (s - prev);
                s = prev;
            }
            mix[delaySamples + i] += s;
        }
    }

    // Boss warning: a two-tone siren alarm with a slow tremolo.
    private void generateBossWarning() {
        int n = SAMPLE_RATE * BOSS_WARNING_MS / 1000;
        bossWarningBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.02);
            double d = Math.min(1.0, (1.0 - t) / 0.08);
            double freq = ((t % 0.28) < 0.14) ? 620.0 : 820.0;
            double tremolo = 0.7 + 0.3 * Math.sin(2 * Math.PI * 9.0 * t);
            double s = Math.sin(2 * Math.PI * freq * t) * tremolo * a * d * 0.32;
            bossWarningBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Boss arrival: a deep descending growl with a noise bite.
    private void generateBossSpawn() {
        int n = SAMPLE_RATE * BOSS_SPAWN_MS / 1000;
        bossSpawnBuffer = new short[n];
        Random rng = new Random(6);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.01);
            double d = Math.exp(-t * 5.0);
            double freq = 200.0 - 130.0 * Math.min(1.0, t / 0.6);
            double growl = Math.sin(2 * Math.PI * freq * t) * 0.8
                         + Math.sin(2 * Math.PI * freq * 1.5 * t) * 0.3;
            double noise = (rng.nextDouble() - 0.5) * 0.3 * Math.exp(-t * 12.0);
            double s = (growl + noise) * a * d * 0.5;
            bossSpawnBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Wall shatter: thud, rubble noise and a short glassy crack.
    private void generateWallDestroyed() {
        int n = SAMPLE_RATE * WALL_DESTROY_MS / 1000;
        wallDestroyBuffer = new short[n];
        Random rng = new Random(7);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.004);
            double d = Math.exp(-t * 17.0);
            double thud = Math.sin(2 * Math.PI * (140.0 - 50.0 * t) * t) * 0.5;
            double noise = (rng.nextDouble() - 0.5) * 0.5;
            double shatter = Math.sin(2 * Math.PI * 2600.0 * t) * Math.exp(-t * 90.0) * 0.3;
            double s = (thud + noise + shatter) * a * d * 0.6;
            wallDestroyBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Challenge complete: a bright C-E-G ascending arpeggio with a sparkle.
    private void generateChallengeComplete() {
        int n = SAMPLE_RATE * CHALLENGE_MS / 1000;
        challengeBuffer = new short[n];
        double[] notes = { 523.25, 659.25, 783.99 };
        double[] starts = { 0.0, 0.11, 0.22 };
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double s = 0;
            for (int k = 0; k < 3; k++) {
                double lt = t - starts[k];
                if (lt < 0) continue;
                double env = Math.min(1.0, lt / 0.005) * Math.exp(-lt * 9.0);
                s += Math.sin(2 * Math.PI * notes[k] * lt) * env;
            }
            double sparkle = t - 0.30;
            if (sparkle >= 0) {
                s += Math.sin(2 * Math.PI * 1567.98 * sparkle) * Math.exp(-sparkle * 12.0) * 0.5;
            }
            double a = Math.min(1.0, t / 0.004);
            challengeBuffer[i] = (short) (s * a * 0.30 * Short.MAX_VALUE);
        }
    }

    // Challenge failed: a descending saw buzz with a low body.
    private void generateChallengeFailed() {
        int n = SAMPLE_RATE * CHALLENGE_FAIL_MS / 1000;
        challengeFailBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.01);
            double d = Math.exp(-t * 6.0);
            double freq = 400.0 * Math.pow(0.6, t / 0.5);
            double saw = 2.0 * (t * freq - Math.floor(t * freq)) - 1.0;
            double low = Math.sin(2 * Math.PI * 220.0 * t);
            double s = (saw * 0.5 + low * 0.4) * a * d * 0.4;
            challengeFailBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Segment lost: a quick descending "snip".
    private void generateSegmentLost() {
        int n = SAMPLE_RATE * SEGMENT_LOST_MS / 1000;
        segmentLostBuffer = new short[n];
        Random rng = new Random(9);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.004);
            double d = Math.exp(-t * 30.0);
            double freq = 900.0 - 600.0 * Math.min(1.0, t / 0.15);
            double tone = Math.sin(2 * Math.PI * freq * t) * 0.6;
            double noise = (rng.nextDouble() - 0.5) * 0.3;
            double s = (tone + noise) * a * d * 0.5;
            segmentLostBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Game over: a sad F-E-C descent over a soft low thud.
    private void generateDeath() {
        int n = SAMPLE_RATE * DEATH_MS / 1000;
        deathBuffer = new short[n];
        double[] notes = { 349.23, 329.63, 261.63 };
        double[] starts = { 0.0, 0.18, 0.42 };
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double s = 0;
            for (int k = 0; k < 3; k++) {
                double lt = t - starts[k];
                if (lt < 0) continue;
                double env = Math.min(1.0, lt / 0.02) * Math.exp(-lt * 5.0);
                s += Math.sin(2 * Math.PI * notes[k] * lt) * env;
            }
            double thud = Math.sin(2 * Math.PI * 70.0 * t) * Math.exp(-t * 9.0) * 0.5;
            double a = Math.min(1.0, t / 0.01);
            double v = (s + thud) * a * 0.45;
            deathBuffer[i] = (short) (Math.max(-1.0, Math.min(1.0, v)) * Short.MAX_VALUE);
        }
    }

    // Pause/resume: a soft double blip descending.
    private void generatePause() {
        int n = SAMPLE_RATE * PAUSE_MS / 1000;
        pauseBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.004);
            double d = Math.exp(-t * 22.0);
            double n1 = (t < 0.07 ? 1 : 0) * Math.sin(2 * Math.PI * 880.0 * t);
            double n2 = (t >= 0.07 ? 1 : 0) * Math.sin(2 * Math.PI * 660.0 * (t - 0.07));
            double s = (n1 + n2) * a * d * 0.3;
            pauseBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Upgrade reveal: a warm C-E-G-C octave arpeggio with a sparkle tail.
    private void generateUpgrade() {
        int n = SAMPLE_RATE * UPGRADE_MS / 1000;
        upgradeBuffer = new short[n];
        double[] notes = { 523.25, 659.25, 783.99, 1046.50 };
        double[] starts = { 0.0, 0.10, 0.20, 0.30 };
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double s = 0;
            for (int k = 0; k < 4; k++) {
                double lt = t - starts[k];
                if (lt < 0) continue;
                double env = Math.min(1.0, lt / 0.005) * Math.exp(-lt * 9.0);
                s += Math.sin(2 * Math.PI * notes[k] * lt) * env;
            }
            double sp = t - 0.40;
            if (sp >= 0) {
                s += Math.sin(2 * Math.PI * 1567.98 * sp) * Math.exp(-sp * 10.0) * 0.4;
            }
            double a = Math.min(1.0, t / 0.004);
            upgradeBuffer[i] = (short) (s * a * 0.28 * Short.MAX_VALUE);
        }
    }

    // Upgrade picked: a short bright rising blip with a shimmer.
    private void generateUpgradePick() {
        int n = SAMPLE_RATE * UPGRADE_PICK_MS / 1000;
        upgradePickBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double a = Math.min(1.0, t / 0.004);
            double d = Math.exp(-t * 14.0);
            double freq = 660.0 + 500.0 * Math.min(1.0, t / 0.10);
            double tone = Math.sin(2 * Math.PI * freq * t) * 0.7;
            double shimmer = Math.sin(2 * Math.PI * 1800.0 * t) * Math.exp(-t * 40.0) * 0.3;
            double s = (tone + shimmer) * a * d * 0.4;
            upgradePickBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // ----- shared play helper -----

    private void playTrack(AudioTrack track) {
        if (track == null) return;
        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop();
            }
            track.reloadStaticData();
            track.setVolume(volume);
            track.play();
        } catch (Exception e) {
            Log.e("SoundEffects", "play failed", e);
        }
    }

    // ----- public API -----

    public void playClick()      { if (!muted) playTrack(clickTrack); }
    public void playCrunch()     { if (!muted) playTrack(crunchTrack); }
    public void playHeal()       { if (!muted) playTrack(healTrack); }
    public void playBossDamage() { if (!muted) playTrack(bossDamageTrack); }
    public void playBossDefeat() { if (!muted) playTrack(bossDefeatTrack); }
    public void playBossWarning(){ if (!muted) playTrack(bossWarningTrack); }
    public void playBossSpawn()  { if (!muted) playTrack(bossSpawnTrack); }
    public void playWallDestroyed() { if (!muted) playTrack(wallDestroyTrack); }
    public void playChallengeComplete() { if (!muted) playTrack(challengeTrack); }
    public void playChallengeFailed() { if (!muted) playTrack(challengeFailTrack); }
    public void playSegmentLost() { if (!muted) playTrack(segmentLostTrack); }
    public void playDeath()      { if (!muted) playTrack(deathTrack); }
    public void playPause()      { if (!muted) playTrack(pauseTrack); }
    public void playUpgrade()    { if (!muted) playTrack(upgradeTrack); }
    public void playUpgradePick(){ if (!muted) playTrack(upgradePickTrack); }
    public void setMuted(boolean m) { muted = m; }

    public void setVolume(float vol) {
        volume = Math.max(0, Math.min(1, vol));
    }

    public void stopAll() {
        AudioTrack[] tracks = { clickTrack, crunchTrack, healTrack, bossDamageTrack,
                bossDefeatTrack, bossWarningTrack, bossSpawnTrack, wallDestroyTrack,
                challengeTrack, challengeFailTrack, segmentLostTrack, deathTrack, pauseTrack,
        upgradeTrack, upgradePickTrack };
        for (AudioTrack t : tracks) {
            if (t != null) {
                try { t.stop(); } catch (Exception e) { }
            }
        }
    }

    public void release() {
        AudioTrack[] tracks = { clickTrack, crunchTrack, healTrack, bossDamageTrack,
                bossDefeatTrack, bossWarningTrack, bossSpawnTrack, wallDestroyTrack,
                challengeTrack, challengeFailTrack, segmentLostTrack, deathTrack, pauseTrack,
        upgradeTrack, upgradePickTrack };
        for (AudioTrack t : tracks) {
            if (t != null) {
                try { t.stop(); t.release(); } catch (Exception e) { }
            }
        }
        clickTrack = null; crunchTrack = null; healTrack = null;
        bossDamageTrack = null; bossDefeatTrack = null;
        bossWarningTrack = null; bossSpawnTrack = null;
        wallDestroyTrack = null; challengeTrack = null;
        challengeFailTrack = null; segmentLostTrack = null;
        deathTrack = null; pauseTrack = null; upgradeTrack = null;
        upgradePickTrack = null;
    }
}
