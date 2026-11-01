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

/**
 * Shows the camera picture without stretching it. Detections and the doorway region are both
 * kept on a 0..1000 grid over the picture itself, so they line up whatever the window size.
 */
public final class CameraPreview extends JPanel {
    private static final Color BACKGROUND = new Color(22, 25, 29);
    private static final Color ACCENT = new Color(167, 220, 106);
    private static final Color DETECTION = new Color(250, 108, 88);
    private static final int GRID = DetectionFilter.GRID;

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
                if (image == null) return;
                Rectangle bounds = imageBounds();
                dragStartX = clamp(event.getX(), bounds.x, bounds.x + bounds.width);
                dragStartY = clamp(event.getY(), bounds.y, bounds.y + bounds.height);
                dragSelection = new Rectangle(dragStartX, dragStartY, 0, 0);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (image == null || dragSelection == null) return;
                Rectangle bounds = imageBounds();
                int x = clamp(event.getX(), bounds.x, bounds.x + bounds.width);
                int y = clamp(event.getY(), bounds.y, bounds.y + bounds.height);
                dragSelection = rectangleBetween(dragStartX, dragStartY, x, y);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (image == null || dragSelection == null) return;
                Rectangle drawn = dragSelection;
                dragSelection = null;
                if (drawn.width >= 20 && drawn.height >= 20) {
                    Rectangle bounds = imageBounds();
                    int x = (int) Math.round((drawn.x - bounds.x) * (double) GRID / bounds.width);
                    int y = (int) Math.round((drawn.y - bounds.y) * (double) GRID / bounds.height);
                    int right = (int) Math.round((drawn.x + drawn.width - bounds.x) * (double) GRID / bounds.width);
                    int bottom = (int) Math.round((drawn.y + drawn.height - bounds.y) * (double) GRID / bounds.height);
                    x = clamp(x, 0, GRID - 1);
                    y = clamp(y, 0, GRID - 1);
                    normalizedRegion = new Rectangle(x, y,
                            clamp(right - x, 1, GRID - x), clamp(bottom - y, 1, GRID - y));
                    regionListener.accept(new Rectangle(normalizedRegion));
                }
                repaint();
            }
        };
        addMouseListener(selectionHandler);
        addMouseMotionListener(selectionHandler);
    }

    /** Detections must be on the 0..1000 grid. */
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

    /** The selected doorway region on the 0..1000 grid, or null. */
    public Rectangle currentRegion() {
        return normalizedRegion == null ? null : new Rectangle(normalizedRegion);
    }

    public boolean hasRegion() {
        return normalizedRegion != null;
    }

    /** Where the picture is drawn inside the panel, keeping its proportions. */
    private Rectangle imageBounds() {
        if (image == null) return new Rectangle(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        double scale = Math.min(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
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
            Rectangle bounds = imageBounds();
            g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);
            g.setStroke(new BasicStroke(2));
            for (Rectangle detection : detections) {
                g.setColor(DETECTION);
                drawGridRect(g, bounds, detection);
            }
            if (normalizedRegion != null) {
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8, 5}, 0));
                drawGridRect(g, bounds, normalizedRegion);
            }
        }

        if (dragSelection != null) {
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(2));
            g.drawRect(dragSelection.x, dragSelection.y, dragSelection.width, dragSelection.height);
        }
        g.dispose();
    }

    private static void drawGridRect(Graphics2D g, Rectangle bounds, Rectangle grid) {
        int x = bounds.x + (int) Math.round(grid.x * bounds.width / (double) GRID);
        int y = bounds.y + (int) Math.round(grid.y * bounds.height / (double) GRID);
        int width = (int) Math.round(grid.width * bounds.width / (double) GRID);
        int height = (int) Math.round(grid.height * bounds.height / (double) GRID);
        g.drawRect(x, y, width, height);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }

    private static Rectangle rectangleBetween(int x1, int y1, int x2, int y2) {
        return new Rectangle(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }
}
