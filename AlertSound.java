package com.kerwinmok.manageralerter;

import java.awt.Toolkit;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays the ding. Tries the normal audio paths one after another and finally the system beep,
 * and reports problems back instead of failing silently on a background thread.
 */
public final class AlertSound {
    private static final int SAMPLE_RATE = 22_050;
    private static final double SECONDS = 0.7;
    private volatile int volume = 70;

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
    }

    /** Plays the ding on a background thread. {@code problems} is called (from that thread) if a fallback was needed. */
    public void play(Consumer<String> problems) {
        int currentVolume = volume;
        if (currentVolume == 0) return;

        Thread player = new Thread(() -> playTone(currentVolume, problems), "doorway-alert-sound");
        player.setDaemon(true);
        player.start();
    }

    private static void playTone(int volume, Consumer<String> problems) {
        byte[] samples = createTone(volume);
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

        String clipProblem = tryClip(samples, format);
        if (clipProblem == null) return;

        String lineProblem = tryLines(samples, format);
        if (lineProblem == null) return;

        try {
            Toolkit.getDefaultToolkit().beep();
            problems.accept("ding used the system beep because audio failed (" + lineProblem + ")");
        } catch (Exception exception) {
            problems.accept("no sound could be played: " + lineProblem);
        }
    }

    private static String tryClip(byte[] samples, AudioFormat format) {
        Clip clip = null;
        try {
            clip = AudioSystem.getClip();
            Clip playing = clip;
            clip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) playing.close();
            });
            clip.open(format, samples, 0, samples.length);
            clip.start();
            return null;
        } catch (Exception exception) {
            if (clip != null) clip.close();
            return describe(exception);
        }
    }

    private static String tryLines(byte[] samples, AudioFormat format) {
        String lastProblem = "no audio output device";
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        try {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            playOn(line, samples, format);
            return null;
        } catch (Exception exception) {
            lastProblem = describe(exception);
        }

        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (!mixer.isLineSupported(info)) continue;
                playOn((SourceDataLine) mixer.getLine(info), samples, format);
                return null;
            } catch (Exception exception) {
                lastProblem = describe(exception);
            }
        }
        return lastProblem;
    }

    private static void playOn(SourceDataLine line, byte[] samples, AudioFormat format) throws Exception {
        try {
            line.open(format);
            line.start();
            line.write(samples, 0, samples.length);
            line.drain();
        } finally {
            line.close();
        }
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** A short bell-like ding. Louder at higher volume, silent at 0. */
    static byte[] createTone(int volume) {
        int frameCount = (int) (SAMPLE_RATE * SECONDS);
        byte[] samples = new byte[frameCount * 2];
        double amplitude = Short.MAX_VALUE * 0.6 * Math.max(0, Math.min(100, volume)) / 100.0;

        for (int frame = 0; frame < frameCount; frame++) {
            double time = frame / (double) SAMPLE_RATE;
            double attack = Math.min(1.0, time / 0.005);
            double low = Math.sin(2.0 * Math.PI * 988.0 * time) * Math.exp(-5.0 * time);
            double high = 0.5 * Math.sin(2.0 * Math.PI * 1319.0 * time) * Math.exp(-7.0 * time);
            short sample = (short) ((low + high) / 1.5 * amplitude * attack);
            samples[frame * 2] = (byte) sample;
            samples[frame * 2 + 1] = (byte) (sample >>> 8);
        }
        return samples;
    }
}
