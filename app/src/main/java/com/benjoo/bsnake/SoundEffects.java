package com.benjoo.bsnake;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

// Generates and plays short button-click sounds (click, crunch, damage).
// A continuous looping keepalive track (50 Hz sub-bass at low amplitude)
// prevents the audio HAL from entering standby, which would otherwise
// mute the first ~200ms of the next track.
// Tracks are MODE_STATIC and accumulate during the session.
public class SoundEffects {

    private static final int SAMPLE_RATE = 22050;
    private static final int KEEPALIVE_MS = 300;
    private static final int CLICK_MS = 60;
    private static final int CRUNCH_MS = 80;
    private static final int DAMAGE_MS = 120;
    private static final int BOSS_DEFEAT_MS = 800;

    private short[] clickBuffer;
    private short[] crunchBuffer;
    private short[] damageBuffer;
    private short[] bossDefeatBuffer;
    private AudioTrack clickTrack, crunchTrack, damageTrack, bossDefeatTrack;
    private AudioTrack keepalive;
    private float volume = 1.0f;

    public SoundEffects() {
        generateClick();
        generateCrunch();
        generateDamage();
        generateBossDefeat();
        initTracks();
        startKeepalive();
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
        damageTrack = createTrack(damageBuffer.length * 2);
        damageTrack.write(damageBuffer, 0, damageBuffer.length);
        bossDefeatTrack = createTrack(bossDefeatBuffer.length * 2);
        bossDefeatTrack.write(bossDefeatBuffer, 0, bossDefeatBuffer.length);
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

    // Crunchy bite: mix of high harmonics + noise with fast decay.
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

    // Crunchy damage: low thud mixed with high crunch harmonics and noise.
    private void generateDamage() {
        int n = SAMPLE_RATE * DAMAGE_MS / 1000;
        damageBuffer = new short[n];
        Random rng = new Random(2);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.exp(-t * 30.0);
            double thud = Math.sin(2 * Math.PI * 150.0 * t) * 0.6;
            double crunch = Math.sin(2 * Math.PI * 2000.0 * t) * 0.4
                          + Math.sin(2 * Math.PI * 3500.0 * t) * 0.2;
            double noise = (rng.nextDouble() - 0.5) * 0.35;
            double s = (thud + crunch + noise) * envelope * 0.5;
            damageBuffer[i] = (short) (s * Short.MAX_VALUE);
        }
    }

    // Dramatic boss defeat: the same hit sound with a cinematic echo tail.
    // The initial impact is sharp and punchy; echoes fade in volume, lower
    // in pitch, and become progressively muffled (low-pass filtered) to
    // sound distant and heavy — "POW! Pow... pow... pow..."
    private void generateBossDefeat() {
        int n = SAMPLE_RATE * BOSS_DEFEAT_MS / 1000;
        double[] mix = new double[n];
        Random rng = new Random(3);

        // Main hit (unchanged character)
        addDamageHit(mix, 0,   1.00, 1.00, 1.0, rng);

        // Cinematic echoes: delay(ms), volume, pitch, low-pass alpha
        addDamageHit(mix, 160, 0.50, 0.95, 0.25, rng);
        addDamageHit(mix, 320, 0.30, 0.90, 0.12, rng);
        addDamageHit(mix, 480, 0.15, 0.85, 0.06, rng);
        addDamageHit(mix, 640, 0.07, 0.80, 0.03, rng);

        // Room reverb: shorter, quieter, very muffled reflections
        addDamageHit(mix, 90,  0.18, 0.93, 0.10, rng);
        addDamageHit(mix, 210, 0.10, 0.88, 0.07, rng);
        addDamageHit(mix, 300, 0.06, 0.83, 0.04, rng);

        // Normalise to prevent clipping, then convert to short[]
        double max = 0;
        for (double v : mix) if (Math.abs(v) > max) max = Math.abs(v);
        double scale = 0.95 / Math.max(max, 0.001);

        bossDefeatBuffer = new short[n];
        for (int i = 0; i < n; i++) {
            bossDefeatBuffer[i] = (short) (mix[i] * scale * Short.MAX_VALUE);
        }
    }

    // Render a single damage hit into the mix buffer at the given delay,
    // scaled by volume, shifted in pitch, and low-pass filtered with alpha.
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

    // ----- keepalive -----

    @SuppressWarnings("deprecation")
    private void startKeepalive() {
        try {
            int n = SAMPLE_RATE * KEEPALIVE_MS / 1000;
            short[] buf = new short[n];
            for (int i = 0; i < n; i++) {
                double t = (double) i / SAMPLE_RATE;
                buf[i] = (short) (Math.sin(2 * Math.PI * 50.0 * t) * 0.05 * Short.MAX_VALUE);
            }
            int bs = n * 2;
            keepalive = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bs,
                    AudioTrack.MODE_STATIC
            );
            keepalive.write(buf, 0, buf.length);
            keepalive.setLoopPoints(0, n, -1);
            keepalive.play();
        } catch (Exception e) {
            Log.e("SoundEffects", "Failed to start keepalive", e);
        }
    }

    // ----- shared play helper (reuses pre-allocated track) -----

    private void playTrack(AudioTrack track) {
        if (track == null) return;
        try {
            track.stop();
            track.reloadStaticData();
            track.setVolume(volume);
            track.play();
        } catch (Exception e) {
            Log.e("SoundEffects", "play failed", e);
        }
    }

    // ----- public API -----

    public void playClick()      { playTrack(clickTrack); }
    public void playCrunch()     { playTrack(crunchTrack); }
    public void playDamage()     { playTrack(damageTrack); }
    public void playBossDefeat() { playTrack(bossDefeatTrack); }

    public void setVolume(float vol) {
        volume = Math.max(0, Math.min(1, vol));
    }

    public void release() {
        if (keepalive != null) {
            try { keepalive.stop(); keepalive.release(); } catch (Exception e) { }
            keepalive = null;
        }
        AudioTrack[] tracks = { clickTrack, crunchTrack, damageTrack, bossDefeatTrack };
        for (AudioTrack t : tracks) {
            if (t != null) {
                try { t.stop(); t.release(); } catch (Exception e) { }
            }
        }
    }
}
