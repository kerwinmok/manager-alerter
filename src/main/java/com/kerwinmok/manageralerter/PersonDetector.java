package com.kerwinmok.manageralerter;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Core;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.HOGDescriptor;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;

/**
 * Finds upright people in a frame. Two things keep it from reporting furniture:
 * a minimum detector confidence, and a check that something in the box is actually moving.
 * Every frame must be passed to {@link #feed(Mat)} so the motion model keeps learning.
 */
final class PersonDetector implements AutoCloseable {
    private static final int WARMUP_FRAMES = 20;
    private static final double MIN_HEIGHT_FRACTION = 0.12;
    private static final double MIN_MOTION_FRACTION = 0.06;

    private final HOGDescriptor hog = new HOGDescriptor();
    private final MatOfRect found = new MatOfRect();
    private final MatOfDouble weights = new MatOfDouble();
    private final Mat foreground = new Mat();
    private final Mat cleaned = new Mat();
    private BackgroundSubtractorMOG2 background = Video.createBackgroundSubtractorMOG2(300, 25, false);
    private int framesSeen;
    private volatile int sensitivity = 5;
    private volatile boolean requireMotion = true;

    PersonDetector() {
        hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector());
    }

    void setSensitivity(int sensitivity) {
        this.sensitivity = DetectionFilter.clampSensitivity(sensitivity);
    }

    void setRequireMotion(boolean requireMotion) {
        this.requireMotion = requireMotion;
    }

    /** Forget what the scene looked like, for example after the camera view is rotated. */
    void reset() {
        background = Video.createBackgroundSubtractorMOG2(300, 25, false);
        framesSeen = 0;
    }

    /** Call for every frame. */
    void feed(Mat frame) {
        background.apply(frame, foreground);
        framesSeen++;
    }

    /** Returns person boxes on the 0..1000 grid, limited to the region when one is given. */
    List<Rectangle> detect(Mat frame, Rectangle region) {
        List<Rectangle> result = new ArrayList<>();
        if (framesSeen < WARMUP_FRAMES) return result;

        hog.detectMultiScale(frame, found, weights, 0, new Size(8, 8), new Size(8, 8), 1.05, 2.0, false);
        if (found.empty()) return result;
        Rect[] rects = found.toArray();
        double[] scores = weights.empty() ? new double[0] : weights.toArray();
        double minScore = DetectionFilter.minimumScore(sensitivity);
        boolean motionRequired = requireMotion;
        if (motionRequired && rects.length > 0) {
            Imgproc.medianBlur(foreground, cleaned, 5);
        }

        for (int i = 0; i < rects.length; i++) {
            Rect rect = rects[i];
            if (i < scores.length && scores[i] < minScore) continue;
            if (rect.height < frame.rows() * MIN_HEIGHT_FRACTION) continue;

            Rectangle box = DetectionFilter.normalize(rect.x, rect.y, rect.width, rect.height,
                    frame.cols(), frame.rows());
            if (!DetectionFilter.touchesRegion(box, region)) continue;
            if (motionRequired && motionFraction(rect) < MIN_MOTION_FRACTION) continue;
            result.add(box);
        }
        return result;
    }

    private double motionFraction(Rect rect) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int right = Math.min(cleaned.cols(), rect.x + rect.width);
        int bottom = Math.min(cleaned.rows(), rect.y + rect.height);
        if (right <= x || bottom <= y) return 0.0;
        Mat part = cleaned.submat(new Rect(x, y, right - x, bottom - y));
        try {
            return Core.countNonZero(part) / (double) ((right - x) * (bottom - y));
        } finally {
            part.release();
        }
    }

    @Override
    public void close() {
        found.release();
        weights.release();
        foreground.release();
        cleaned.release();
    }
}
