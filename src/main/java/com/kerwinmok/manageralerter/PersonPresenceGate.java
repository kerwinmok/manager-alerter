package com.kerwinmok.manageralerter;

public final class PersonPresenceGate {
    public record Update(boolean present, boolean ring) {
    }

    private static final int REQUIRED_DETECTIONS = 2;

    private final long cooldownMillis;
    private final long exitGraceMillis;
    private boolean present;
    private int consecutiveDetections;
    private long lastDetectionMillis = Long.MIN_VALUE;
    private long lastRingMillis = Long.MIN_VALUE;

    public PersonPresenceGate(long cooldownMillis, long exitGraceMillis) {
        if (cooldownMillis < 0 || exitGraceMillis < 0) {
            throw new IllegalArgumentException("timings cannot be negative");
        }
        this.cooldownMillis = cooldownMillis;
        this.exitGraceMillis = exitGraceMillis;
    }

    public synchronized Update update(boolean detected, long nowMillis) {
        if (present && nowMillis - lastDetectionMillis >= exitGraceMillis) {
            present = false;
            consecutiveDetections = 0;
        }

        if (!detected) {
            consecutiveDetections = 0;
            return new Update(present, false);
        }

        lastDetectionMillis = nowMillis;
        consecutiveDetections = Math.min(REQUIRED_DETECTIONS, consecutiveDetections + 1);
        if (present || consecutiveDetections < REQUIRED_DETECTIONS) {
            return new Update(present, false);
        }

        present = true;
        boolean ring = lastRingMillis == Long.MIN_VALUE || nowMillis - lastRingMillis >= cooldownMillis;
        if (ring) {
            lastRingMillis = nowMillis;
        }
        return new Update(true, ring);
    }
}