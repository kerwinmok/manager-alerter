package com.kerwinmok.manageralerter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertSoundTest {
    private static int peak(byte[] samples) {
        int peak = 0;
        for (int i = 0; i < samples.length; i += 2) {
            peak = Math.max(peak, Math.abs((short) ((samples[i] & 0xff) | (samples[i + 1] << 8))));
        }
        return peak;
    }

    @Test
    void toneIsNotSilentAndGetsLouderWithVolume() {
        int quiet = peak(AlertSound.createTone(20));
        int loud = peak(AlertSound.createTone(100));

        assertTrue(quiet > 500);
        assertTrue(loud > quiet * 3);
        assertTrue(loud < Short.MAX_VALUE);
    }

    @Test
    void zeroVolumeToneIsSilent() {
        assertEquals(0, peak(AlertSound.createTone(0)));
    }
}
