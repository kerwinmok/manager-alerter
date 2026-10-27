package com.kerwinmok.manageralerter;

import java.awt.Rectangle;

/** Small pure helpers for turning detector output into something the preview and alert logic can use. */
final class DetectionFilter {
    /** Boxes and regions are expressed on a 0..1000 grid so they do not depend on frame size. */
    static final int GRID = 1000;

    private static final double MIN_OVERLAP_OF_BOX = 0.25;

    private DetectionFilter() {
    }

    static Rectangle normalize(int x, int y, int width, int height, int frameWidth, int frameHeight) {
        return new Rectangle(
                Math.round(x * (float) GRID / frameWidth),
                Math.round(y * (float) GRID / frameHeight),
                Math.round(width * (float) GRID / frameWidth),
                Math.round(height * (float) GRID / frameHeight));
    }

    /** No region means the whole frame counts. Otherwise the box must be mostly or centrally inside it. */
    static boolean touchesRegion(Rectangle box, Rectangle region) {
        if (region == null) return true;
        if (region.contains(box.getCenterX(), box.getCenterY())) return true;
        Rectangle overlap = box.intersection(region);
        if (overlap.width <= 0 || overlap.height <= 0) return false;
        double boxArea = Math.max(1.0, (double) box.width * box.height);
        return (overlap.width * (double) overlap.height) / boxArea >= MIN_OVERLAP_OF_BOX;
    }

    static int clampSensitivity(int sensitivity) {
        return Math.max(1, Math.min(10, sensitivity));
    }

    /** Minimum detector confidence. Higher sensitivity accepts weaker detections. */
    static double minimumScore(int sensitivity) {
        return 1.5 - (clampSensitivity(sensitivity) - 1) * 0.15;
    }

    static int rotationCode(int degrees) {
        return switch (((degrees % 360) + 360) % 360) {
            case 90 -> org.opencv.core.Core.ROTATE_90_CLOCKWISE;
            case 180 -> org.opencv.core.Core.ROTATE_180;
            case 270 -> org.opencv.core.Core.ROTATE_90_COUNTERCLOCKWISE;
            default -> -1;
        };
    }
}
