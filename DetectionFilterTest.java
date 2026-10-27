package com.kerwinmok.manageralerter;

import java.awt.Rectangle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectionFilterTest {
    @Test
    void normalizesPixelBoxesOntoTheThousandGrid() {
        Rectangle box = DetectionFilter.normalize(100, 50, 200, 100, 400, 200);

        assertEquals(new Rectangle(250, 250, 500, 500), box);
    }

    @Test
    void noRegionAcceptsEverything() {
        assertTrue(DetectionFilter.touchesRegion(new Rectangle(0, 0, 10, 10), null));
    }

    @Test
    void acceptsBoxesInsideOrMostlyInsideTheRegion() {
        Rectangle region = new Rectangle(400, 0, 300, 1000);

        assertTrue(DetectionFilter.touchesRegion(new Rectangle(450, 100, 200, 400), region));
        assertTrue(DetectionFilter.touchesRegion(new Rectangle(300, 100, 200, 400), region));
    }

    @Test
    void rejectsBoxesOutsideTheRegion() {
        Rectangle region = new Rectangle(400, 0, 300, 1000);

        assertFalse(DetectionFilter.touchesRegion(new Rectangle(0, 100, 200, 400), region));
        assertFalse(DetectionFilter.touchesRegion(new Rectangle(100, 100, 250, 400), region));
    }

    @Test
    void sensitivityMapsToAStricterScoreWhenLower() {
        assertTrue(DetectionFilter.minimumScore(1) > DetectionFilter.minimumScore(5));
        assertTrue(DetectionFilter.minimumScore(5) > DetectionFilter.minimumScore(10));
        assertEquals(DetectionFilter.minimumScore(10), DetectionFilter.minimumScore(99));
        assertEquals(DetectionFilter.minimumScore(1), DetectionFilter.minimumScore(-4));
    }
}
