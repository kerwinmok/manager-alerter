package com.kerwinmok.manageralerter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
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
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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
    private final JComboBox<String> cameraPicker = new JComboBox<>(cameraChoices());
    private final JSpinner cooldown = new JSpinner(new SpinnerNumberModel(10, 3, 120, 1));
    private final JButton startButton = new JButton("start monitoring");
    private final JButton clearRegionButton = new JButton("clear region");
    private final JLabel status = new JLabel("camera off", SwingConstants.LEFT);
    private CameraMonitor monitor;

    public static void main(String[] args) {
        OpenCV.loadLocally();
        SwingUtilities.invokeLater(() -> new ManagerAlerterApp().show());
    }

    private static String[] cameraChoices() {
        String[] choices = new String[8];
        for (int index = 0; index < choices.length; index++) {
            choices[index] = "camera " + index;
        }
        return choices;
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
        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        controlRow.setOpaque(false);
        styleButton(startButton, true);
        styleButton(clearRegionButton, false);
        cameraPicker.setSelectedIndex(0);
        cameraPicker.setToolTipText("Select a camera index; unavailable cameras will report an error when started.");
        cooldown.setToolTipText("Minimum seconds between dings for separate entries.");

        controlRow.add(new JLabel("camera"));
        controlRow.add(cameraPicker);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(new JLabel("ding cooldown (seconds)"));
        controlRow.add(cooldown);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(startButton);
        controls.add(controlRow, BorderLayout.NORTH);

        status.setOpaque(true);
        status.setBackground(new Color(233, 236, 229));
        status.setForeground(INK);
        status.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        controls.add(status, BorderLayout.SOUTH);
        root.add(controls, BorderLayout.SOUTH);
        return root;
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

        int cameraIndex = cameraPicker.getSelectedIndex();
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
                    if (ding) Toolkit.getDefaultToolkit().beep();
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