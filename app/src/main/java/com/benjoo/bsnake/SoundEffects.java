package com.benjoo.bsnake;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Random;

public class SoundEffects {

    private static final int SAMPLE_RATE = 22050;
    private static final int CLICK_MS = 60;
    private static final int CRUNCH_MS = 80;
    private static final int DAMAGE_MS = 120;
    private static final int BOSS_DAMAGE_MS = 500;
    private static final int BOSS_DEFEAT_MS = 3200;
    private static final int WALL_DESTROY_MS = 300;
    private static final int CHALLENGE_MS = 350;

    private short[] clickBuffer;
    private short[] crunchBuffer;
    private short[] bossDamageBuffer;
    private short[] bossDefeatBuffer;
    private short[] wallDestroyBuffer;
    private short[] challengeBuffer;
    private AudioTrack clickTrack, crunchTrack, bossDamageTrack, bossDefeatTrack, wallDestroyTrack, challengeTrack;
    private float volume = 1.0f;
    private boolean muted;

    public SoundEffects() {
        generateClick();
        generateCrunch();
        generateBossDamage();
        generateBossDefeat();
        generateWallDestroyed();
        generateChallengeComplete();
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
        bossDamageTrack = createTrack(bossDamageBuffer.length * 2);
        bossDamageTrack.write(bossDamageBuffer, 0, bossDamageBuffer.length);
        bossDefeatTrack = createTrack(bossDefeatBuffer.length * 2);
        bossDefeatTrack.write(bossDefeatBuffer, 0, bossDefeatBuffer.length);
        wallDestroyTrack = createTrack(wallDestroyBuffer.length * 2);
        wallDestroyTrack.write(wallDestroyBuffer, 0, wallDestroyBuffer.length);
        challengeTrack = createTrack(challengeBuffer.length * 2);
        challengeTrack.write(challengeBuffer, 0, challengeBuffer.length);
    }

    // ----- sound generation -----

    private void generateClick() {
        int n = SAMPLE_RATE * CLICK_MS / 1000;
        clickBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double s = Math.sin(2 * Math.PI * 1000.0 * t) * Math.exp(-t * 80.0) * 0.5;
            clickBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    private void generateCrunch() {
        int n = SAMPLE_RATE * CRUNCH_MS / 1000;
        crunchBuffer = new short[n];
        Random rng = new Random(1);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.exp(-t * 45.0);
            double noise = (rng.nextDouble() - 0.5) * 0.3;
            double tone = Math.sin(2 * Math.PI * 1800.0 * t) * 0.6
                        + Math.sin(2 * Math.PI * 3000.0 * t) * 0.3
                        + Math.sin(2 * Math.PI * 4200.0 * t) * 0.15;
            double s = (tone + noise) * envelope * 0.5;
            crunchBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    private void generateBossDamage() {
        int n = SAMPLE_RATE * BOSS_DAMAGE_MS / 1000;
        bossDamageBuffer = new short[n];
        Random rng = new Random(4);
        double lp = 0, bp = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;

            double cutoff = 753.3 * Math.pow(2.0, -73.44 * t);
            cutoff = Math.max(cutoff, 3.528);
            cutoff *= 1.0 + 0.2285 * Math.sin(2.0 * Math.PI * 0.8799 * t);
            cutoff = Math.min(cutoff, SAMPLE_RATE * 0.45);

            double f = 2.0 * Math.sin(Math.PI * cutoff / SAMPLE_RATE);
            double noise = (rng.nextDouble() - 0.5) * 0.7;

            double hp = noise - lp - 0.45 * bp;
            bp += f * hp;
            lp += f * bp;

            double env = t < 0.005224 ? 1.0 : Math.exp(-(t - 0.005224) * 6.0);
            bossDamageBuffer[i] = (short) (lp * env * Short.MAX_VALUE * 0.5);
        }
    }

    private void generateBossDefeat() {
        int n = SAMPLE_RATE * BOSS_DEFEAT_MS / 1000;
        double[] mix = new double[n];
        Random rng = new Random(3);
        addDamageHit(mix, 0, 1.00, 1.00, 1.0, rng);
        for (int e = 0; e < 7; e++) {
            int delay = (e + 1) * 400;
            double vol = 0.55 * Math.pow(0.55, e);
            double pitch = 0.97 - e * 0.04;
            double alpha = 0.30 * Math.pow(0.45, e);
            addDamageHit(mix, delay, vol, pitch, alpha, rng);
        }
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

    // Wall shatter: short burst of noise with a descending tone.
    private void generateWallDestroyed() {
        int n = SAMPLE_RATE * WALL_DESTROY_MS / 1000;
        wallDestroyBuffer = new short[n];
        Random rng = new Random(7);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.exp(-t * 20.0);
            double thud = Math.sin(2 * Math.PI * (130.0 - 55.0 * t) * t) * 0.45;
            double noise = (rng.nextDouble() - 0.5) * 0.5;
            double s = (thud + noise) * envelope * 0.6;
            wallDestroyBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Challenge complete: a bright two-tone chime.
    private void generateChallengeComplete() {
        int n = SAMPLE_RATE * CHALLENGE_MS / 1000;
        challengeBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.min(1.0, t * 60.0) * Math.exp(-t * 9.0);
            double first = Math.sin(2 * Math.PI * 880.0 * t);
            double second = Math.sin(2 * Math.PI * 1174.66 * t);
            double s = (first + second) * envelope * 0.28;
            challengeBuffer[i] = (short) (s * Short.MAX_VALUE);
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
    public void playBossDamage() { if (!muted) playTrack(bossDamageTrack); }
    public void playBossDefeat() { if (!muted) playTrack(bossDefeatTrack); }
    public void playWallDestroyed() { if (!muted) playTrack(wallDestroyTrack); }
    public void playChallengeComplete() { if (!muted) playTrack(challengeTrack); }
    public void setMuted(boolean m) { muted = m; }

    public void setVolume(float vol) {
        volume = Math.max(0, Math.min(1, vol));
    }

    public void stopAll() {
        AudioTrack[] tracks = { clickTrack, crunchTrack, bossDamageTrack, bossDefeatTrack, wallDestroyTrack, challengeTrack };
        for (AudioTrack t : tracks) {
            if (t != null) {
                try { t.stop(); } catch (Exception e) { }
            }
        }
    }

    public void release() {
        AudioTrack[] tracks = { clickTrack, crunchTrack, bossDamageTrack, bossDefeatTrack, wallDestroyTrack, challengeTrack };
        for (AudioTrack t : tracks) {
            if (t != null) {
                try { t.stop(); t.release(); } catch (Exception e) { }
            }
        }
        clickTrack = null; crunchTrack = null;
        bossDamageTrack = null; bossDefeatTrack = null;
        wallDestroyTrack = null; challengeTrack = null;
    }
}
