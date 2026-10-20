package com.kerwinmok.manageralerter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JPanel;

public final class CameraPreview extends JPanel {
    private static final Color BACKGROUND = new Color(22, 25, 29);
    private static final Color ACCENT = new Color(167, 220, 106);
    private static final Color DETECTION = new Color(250, 108, 88);

    private final Consumer<Rectangle> regionListener;
    private BufferedImage image;
    private List<Rectangle> detections = List.of();
    private Rectangle normalizedRegion;
    private int dragStartX;
    private int dragStartY;
    private Rectangle dragSelection;

    public CameraPreview(Consumer<Rectangle> regionListener) {
        this.regionListener = regionListener;
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(800, 450));
        setMinimumSize(new Dimension(320, 240));
        setOpaque(true);

        MouseAdapter selectionHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragStartX = event.getX();
                dragStartY = event.getY();
                dragSelection = new Rectangle(dragStartX, dragStartY, 0, 0);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                dragSelection = rectangleBetween(dragStartX, dragStartY, event.getX(), event.getY());
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                Rectangle drawn = rectangleBetween(dragStartX, dragStartY, event.getX(), event.getY());
                dragSelection = null;
                if (drawn.width >= 20 && drawn.height >= 20 && image != null) {
                    normalizedRegion = new Rectangle(
                            clamp((int) ((drawn.x / (double) getWidth()) * 1000), 0, 999),
                            clamp((int) ((drawn.y / (double) getHeight()) * 1000), 0, 999),
                            clamp((int) ((drawn.width / (double) getWidth()) * 1000), 1, 1000),
                            clamp((int) ((drawn.height / (double) getHeight()) * 1000), 1, 1000));
                    regionListener.accept(new Rectangle(normalizedRegion));
                }
                repaint();
            }
        };
        addMouseListener(selectionHandler);
        addMouseMotionListener(selectionHandler);
    }

    public void setFrame(BufferedImage image, List<Rectangle> detections) {
        this.image = image;
        this.detections = detections;
        repaint();
    }

    public void clearRegion() {
        normalizedRegion = null;
        dragSelection = null;
        regionListener.accept(null);
        repaint();
    }

    public boolean hasRegion() {
        return normalizedRegion != null;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (image == null) {
            g.setColor(new Color(172, 179, 184));
            String message = "camera preview appears here";
            int textWidth = g.getFontMetrics().stringWidth(message);
            g.drawString(message, (getWidth() - textWidth) / 2, getHeight() / 2);
        } else {
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            g.setStroke(new BasicStroke(2));
            for (Rectangle detection : detections) {
                g.setColor(DETECTION);
                g.drawRect(toX(detection.x), toY(detection.y), toWidth(detection.width), toHeight(detection.height));
            }
            if (normalizedRegion != null) {
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8, 5}, 0));
                g.drawRect(toX(normalizedRegion.x), toY(normalizedRegion.y), toWidth(normalizedRegion.width), toHeight(normalizedRegion.height));
            }
        }

        if (dragSelection != null) {
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(2));
            g.drawRect(dragSelection.x, dragSelection.y, dragSelection.width, dragSelection.height);
        }
        g.dispose();
    }

    private int toX(int normalized) { return (int) (normalized * getWidth() / 1000.0); }
    private int toY(int normalized) { return (int) (normalized * getHeight() / 1000.0); }
    private int toWidth(int normalized) { return (int) (normalized * getWidth() / 1000.0); }
    private int toHeight(int normalized) { return (int) (normalized * getHeight() / 1000.0); }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }

    private static Rectangle rectangleBetween(int x1, int y1, int x2, int y2) {
        return new Rectangle(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }
}