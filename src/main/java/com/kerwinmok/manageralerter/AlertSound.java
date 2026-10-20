package com.kerwinmok.manageralerter;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

public final class AlertSound {
    private static final int SAMPLE_RATE = 22_050;
    private volatile int volume = 70;

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
    }

    public void play() {
        int currentVolume = volume;
        if (currentVolume == 0) return;

        Thread player = new Thread(() -> playTone(currentVolume), "doorway-alert-sound");
        player.setDaemon(true);
        player.start();
    }

    private static void playTone(int volume) {
        byte[] samples = createTone(volume);
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (AudioInputStream audio = new AudioInputStream(
                new ByteArrayInputStream(samples), format, samples.length / 2L)) {
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            clip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) clip.close();
            });
            clip.start();
        } catch (LineUnavailableException | IOException exception) {
            throw new IllegalStateException("could not play alert sound", exception);
        }
    }

    private static byte[] createTone(int volume) {
        int frameCount = SAMPLE_RATE / 4;
        byte[] samples = new byte[frameCount * 2];
        double amplitude = Short.MAX_VALUE * 0.28 * volume / 100.0;

        for (int frame = 0; frame < frameCount; frame++) {
            double fade = Math.min(1.0, Math.min(frame / 300.0, (frameCount - frame) / 700.0));
            double wave = Math.sin(2.0 * Math.PI * 880.0 * frame / SAMPLE_RATE);
            short sample = (short) (wave * amplitude * fade);
            samples[frame * 2] = (byte) sample;
            samples[frame * 2 + 1] = (byte) (sample >>> 8);
        }
        return samples;
    }
}