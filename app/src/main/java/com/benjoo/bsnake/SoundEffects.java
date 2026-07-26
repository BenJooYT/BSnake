package com.benjoo.bsnake;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

public class SoundEffects {

    private static final int SAMPLE_RATE = 22050;
    private static final int CLICK_MS = 60;
    private static final int CRUNCH_MS = 80;
    private static final int DAMAGE_MS = 120;
    private static final int BOSS_DEFEAT_MS = 3200;

    private short[] clickBuffer;
    private short[] crunchBuffer;
    private short[] damageBuffer;
    private short[] bossDefeatBuffer;
    private AudioTrack clickTrack, crunchTrack, damageTrack, bossDefeatTrack;
    private float volume = 1.0f;

    public SoundEffects() {
        generateClick();
        generateCrunch();
        generateDamage();
        generateBossDefeat();
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

    public void playClick()      { playTrack(clickTrack); }
    public void playCrunch()     { playTrack(crunchTrack); }
    public void playDamage()     { playTrack(damageTrack); }
    public void playBossDefeat() { playTrack(bossDefeatTrack); }

    public void setVolume(float vol) {
        volume = Math.max(0, Math.min(1, vol));
    }

    public void release() {
        AudioTrack[] tracks = { clickTrack, crunchTrack, damageTrack, bossDefeatTrack };
        for (AudioTrack t : tracks) {
            if (t != null) {
                try { t.stop(); t.release(); } catch (Exception e) { }
            }
        }
        clickTrack = null; crunchTrack = null;
        damageTrack = null; bossDefeatTrack = null;
    }
}
