package com.kerwinmok.manageralerter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSlider;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import nu.pattern.OpenCV;

import org.opencv.core.Core;

public final class ManagerAlerterApp {
    private static final Color INK = new Color(31, 36, 42);
    private static final Color MUTED = new Color(103, 112, 121);
    private static final Color GREEN = new Color(159, 205, 107);
    private static final Color BACKGROUND = new Color(244, 245, 240);

    private final JFrame window = new JFrame("Doorway Alert");
    private final CameraPreview preview = new CameraPreview(this::updateRegion);
    private final JComboBox<CameraChoice> cameraPicker = new JComboBox<>();
    private final JSpinner cooldown = new JSpinner(new SpinnerNumberModel(10, 3, 120, 1));
    private final JSlider volume = new JSlider(0, 100, 70);
    private final JLabel volumeValue = new JLabel("70%");
    private final JButton testSoundButton = new JButton("test sound");
    private final JButton startButton = new JButton("start monitoring");
    private final JButton clearRegionButton = new JButton("clear region");
    private final JLabel status = new JLabel("camera off", SwingConstants.LEFT);
    private final AlertSound alertSound = new AlertSound();
    private CameraMonitor monitor;

    private record CameraChoice(int index) {
        @Override
        public String toString() {
            return "camera " + index;
        }
    }

    public static void main(String[] args) {
        OpenCV.loadLocally();
        SwingUtilities.invokeLater(() -> new ManagerAlerterApp().show());
    }

    private void show() {
        configureAppearance();
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.setMinimumSize(new Dimension(760, 620));
        window.setSize(980, 760);
        window.setLocationRelativeTo(null);
        window.setContentPane(buildContent());
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                stopMonitoring();
                window.dispose();
            }
        });
        startButton.addActionListener(event -> toggleMonitoring());
        clearRegionButton.addActionListener(event -> preview.clearRegion());
        testSoundButton.addActionListener(event -> testSound());
        volume.addChangeListener(event -> {
            int level = volume.getValue();
            alertSound.setVolume(level);
            volumeValue.setText(level + "%");
        });
        scanForCameras();
        window.setVisible(true);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("doorway alert");
        title.setForeground(INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel("local person detection · no video is saved · let people know the camera is on");
        subtitle.setForeground(MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        heading.add(title);
        heading.add(subtitle);
        root.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        preview.setBorder(BorderFactory.createLineBorder(new Color(205, 211, 204)));
        center.add(preview, BorderLayout.CENTER);

        JPanel tipRow = new JPanel(new BorderLayout());
        tipRow.setOpaque(false);
        JLabel tip = new JLabel("drag over the doorway to limit the alert area · full-body detector");
        tip.setForeground(MUTED);
        tip.setFont(tip.getFont().deriveFont(12f));
        tipRow.add(tip, BorderLayout.WEST);
        tipRow.add(clearRegionButton, BorderLayout.EAST);
        center.add(tipRow, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(0, 10));
        controls.setOpaque(false);
        JPanel controlStack = new JPanel();
        controlStack.setOpaque(false);
        controlStack.setLayout(new BoxLayout(controlStack, BoxLayout.Y_AXIS));

        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        controlRow.setOpaque(false);
        styleButton(startButton, true);
        styleButton(clearRegionButton, false);
        styleButton(testSoundButton, false);
        cameraPicker.setEnabled(false);
        cameraPicker.setToolTipText("Only cameras detected on this computer are listed.");
        cooldown.setToolTipText("Minimum seconds between dings for separate entries.");
        volume.setMajorTickSpacing(25);
        volume.setPaintTicks(true);
        volume.setPreferredSize(new Dimension(135, 36));
        startButton.setEnabled(false);

        controlRow.add(new JLabel("camera"));
        controlRow.add(cameraPicker);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(new JLabel("ding cooldown (seconds)"));
        controlRow.add(cooldown);
        controlRow.add(startButton);

        JPanel soundRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 2));
        soundRow.setOpaque(false);
        soundRow.add(new JLabel("ding volume"));
        soundRow.add(volume);
        soundRow.add(volumeValue);
        soundRow.add(testSoundButton);

        controlStack.add(controlRow);
        controlStack.add(soundRow);
        controls.add(controlStack, BorderLayout.NORTH);

        status.setOpaque(true);
        status.setBackground(new Color(233, 236, 229));
        status.setForeground(INK);
        status.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        controls.add(status, BorderLayout.SOUTH);
        root.add(controls, BorderLayout.SOUTH);
        return root;
    }

    private void scanForCameras() {
        status.setText("checking connected cameras…");
        new SwingWorker<List<Integer>, Void>() {
            @Override
            protected List<Integer> doInBackground() {
                return CameraMonitor.findAvailableCameras();
            }

            @Override
            protected void done() {
                try {
                    List<Integer> available = get();
                    cameraPicker.removeAllItems();
                    available.forEach(index -> cameraPicker.addItem(new CameraChoice(index)));
                    boolean found = !available.isEmpty();
                    cameraPicker.setEnabled(found);
                    startButton.setEnabled(found);
                    status.setText(found
                            ? "found " + available.size() + " camera" + (available.size() == 1 ? "" : "s") + " · camera is off"
                            : "no cameras found · connect a camera and refresh");
                } catch (Exception exception) {
                    cameraPicker.setEnabled(false);
                    startButton.setEnabled(false);
                    status.setText("could not scan cameras · " + exception.getMessage());
                }
            }
        }.execute();
    }

    private void testSound() {
        try {
            alertSound.play();
            status.setText(volume.getValue() == 0 ? "volume is muted" : "playing test ding · " + volume.getValue() + "%");
        } catch (RuntimeException exception) {
            status.setText("could not play sound · check audio output");
        }
    }

    private void configureAppearance() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Keep Swing's default look if the system theme is unavailable.
        }
    }

    private void styleButton(JButton button, boolean primary) {
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? GREEN.darker() : new Color(205, 211, 204)),
                BorderFactory.createEmptyBorder(8, 13, 8, 13)));
        button.setBackground(primary ? GREEN : Color.WHITE);
        button.setForeground(INK);
        button.setOpaque(true);
    }

    private void toggleMonitoring() {
        if (monitor != null && monitor.isRunning()) {
            stopMonitoring();
            return;
        }

        CameraChoice selectedCamera = (CameraChoice) cameraPicker.getSelectedItem();
        if (selectedCamera == null) return;
        int cameraIndex = selectedCamera.index();
        long cooldownMillis = ((Number) cooldown.getValue()).longValue() * 1_000;
        monitor = new CameraMonitor(cameraIndex, cooldownMillis, new CameraMonitor.Listener() {
            @Override
            public void onFrame(java.awt.image.BufferedImage image, List<Rectangle> detections,
                    boolean personPresent, boolean ding) {
                SwingUtilities.invokeLater(() -> {
                    preview.setFrame(image, detections);
                    status.setText(personPresent ? "camera active · person detected" : "camera active · no person");
                    status.setBackground(personPresent ? new Color(244, 223, 205) : new Color(226, 239, 215));
                    window.setTitle("Doorway Alert · camera active");
                    if (ding) {
                        try {
                            alertSound.play();
                        } catch (RuntimeException exception) {
                            status.setText("person detected · sound unavailable");
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> status.setText(message));
            }

            @Override
            public void onStopped() {
                SwingUtilities.invokeLater(() -> {
                    status.setText("camera off");
                    status.setBackground(new Color(233, 236, 229));
                    startButton.setText("start monitoring");
                    cameraPicker.setEnabled(true);
                    cooldown.setEnabled(true);
                    window.setTitle("Doorway Alert");
                });
            }
        });

        startButton.setEnabled(false);
        status.setText("opening camera " + cameraIndex + "…");
        Thread opener = new Thread(() -> {
            try {
                monitor.start();
                SwingUtilities.invokeLater(() -> {
                    startButton.setText("stop monitoring");
                    startButton.setEnabled(true);
                    cameraPicker.setEnabled(false);
                    cooldown.setEnabled(false);
                });
            } catch (RuntimeException exception) {
                SwingUtilities.invokeLater(() -> {
                    status.setText("could not open camera " + cameraIndex + " · check the camera number");
                    startButton.setEnabled(true);
                    window.setTitle("Doorway Alert");
                });
            }
        }, "camera-open");
        opener.setDaemon(true);
        opener.start();
    }

    private void updateRegion(Rectangle region) {
        CameraMonitor current = monitor;
        if (current != null) current.setRegion(region);
        status.setText(region == null ? "no region selected · watching the full frame" : "region selected · alerts limited to the doorway");
    }

    private void stopMonitoring() {
        CameraMonitor current = monitor;
        if (current != null) current.close();
    }
}