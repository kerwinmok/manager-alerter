package com.kerwinmok.manageralerter;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

public final class CameraMonitor implements AutoCloseable {
    public interface Listener {
        void onFrame(BufferedImage image, List<Rectangle> detections, boolean personPresent, boolean ding);
        void onError(String message);
        void onStopped();
    }

    private static final int MAX_FRAME_SIDE = 800;
    private static final int DETECTION_INTERVAL = 3;
    private static final int EXIT_GRACE_MILLIS = 1_500;
    private static final int CAMERA_SCAN_LIMIT = 10;

    private final int cameraIndex;
    private final long cooldownMillis;
    private final AtomicReference<Rectangle> region = new AtomicReference<>();
    private final Listener listener;
    private final PersonDetector detector = new PersonDetector();
    private volatile int rotationDegrees;
    private volatile boolean running;
    private VideoCapture capture;
    private Thread worker;

    public static List<Integer> findAvailableCameras() {
        List<Integer> available = new ArrayList<>();
        for (int index = 0; index < CAMERA_SCAN_LIMIT; index++) {
            VideoCapture probe = new VideoCapture(index);
            Mat testFrame = new Mat();
            try {
                if (probe.isOpened() && probe.read(testFrame) && !testFrame.empty()) {
                    available.add(index);
                }
            } finally {
                testFrame.release();
                probe.release();
            }
        }
        return List.copyOf(available);
    }

    public CameraMonitor(int cameraIndex, long cooldownMillis, Listener listener) {
        this.cameraIndex = cameraIndex;
        this.cooldownMillis = cooldownMillis;
        this.listener = listener;
    }

    /** Region on a 0..1000 grid over the (already rotated) picture, or null for the whole picture. */
    public void setRegion(Rectangle normalizedRegion) {
        region.set(normalizedRegion == null ? null : new Rectangle(normalizedRegion));
    }

    /** Clockwise degrees to turn the camera picture so people appear upright: 0, 90, 180 or 270. */
    public void setRotation(int degrees) {
        rotationDegrees = ((degrees % 360) + 360) % 360;
    }

    public void setSensitivity(int sensitivity) {
        detector.setSensitivity(sensitivity);
    }

    public void setRequireMotion(boolean requireMotion) {
        detector.setRequireMotion(requireMotion);
    }

    public synchronized void start() {
        if (running) return;

        capture = new VideoCapture(cameraIndex);
        if (!capture.isOpened()) {
            capture.release();
            capture = null;
            throw new IllegalStateException("could not open camera " + cameraIndex);
        }

        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 1280);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 720);
        running = true;
        worker = new Thread(this::captureLoop, "camera-monitor");
        worker.setDaemon(true);
        worker.start();
    }

    private void captureLoop() {
        Mat frame = new Mat();
        Mat rotated = new Mat();
        Mat resized = new Mat();
        PersonPresenceGate presence = new PersonPresenceGate(cooldownMillis, EXIT_GRACE_MILLIS);
        List<Rectangle> lastDetections = List.of();
        int frameNumber = 0;
        int appliedRotation = rotationDegrees;

        try {
            while (running) {
                if (!capture.read(frame) || frame.empty()) {
                    if (running) listener.onError("camera stopped sending frames");
                    break;
                }

                int rotation = rotationDegrees;
                if (rotation != appliedRotation) {
                    appliedRotation = rotation;
                    detector.reset();
                    lastDetections = List.of();
                }
                Mat upright = frame;
                int code = DetectionFilter.rotationCode(rotation);
                if (code >= 0) {
                    Core.rotate(frame, rotated, code);
                    upright = rotated;
                }

                double scale = Math.min(1.0, (double) MAX_FRAME_SIDE / Math.max(upright.width(), upright.height()));
                int width = Math.max(1, (int) Math.round(upright.width() * scale));
                int height = Math.max(1, (int) Math.round(upright.height() * scale));
                Imgproc.resize(upright, resized, new Size(width, height));
                detector.feed(resized);

                boolean ding = false;
                boolean personPresent;
                if (frameNumber++ % DETECTION_INTERVAL == 0) {
                    lastDetections = detector.detect(resized, region.get());
                    PersonPresenceGate.Update update = presence.update(!lastDetections.isEmpty(), System.nanoTime() / 1_000_000L);
                    personPresent = update.present();
                    ding = update.ring();
                } else {
                    personPresent = !lastDetections.isEmpty();
                }

                listener.onFrame(toBufferedImage(resized), lastDetections, personPresent, ding);
            }
        } catch (RuntimeException exception) {
            if (running) listener.onError("camera monitor stopped: " + exception.getMessage());
        } finally {
            running = false;
            frame.release();
            rotated.release();
            resized.release();
            detector.close();
            synchronized (this) {
                if (capture != null) {
                    capture.release();
                    capture = null;
                }
                worker = null;
            }
            listener.onStopped();
        }
    }

    private static BufferedImage toBufferedImage(Mat mat) {
        int channels = mat.channels();
        int imageType = channels == 1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), imageType);
        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        byte[] source = new byte[target.length];
        mat.get(0, 0, source);
        System.arraycopy(source, 0, target, 0, target.length);
        return image;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    public synchronized void close() {
        running = false;
        if (capture != null) capture.release();
    }
}