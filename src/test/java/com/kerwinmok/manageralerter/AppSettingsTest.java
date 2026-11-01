package com.kerwinmok.manageralerter;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AppSettingsTest {
    @Test
    void usesDefaultsWhenNoFileExists(@TempDir Path folder) {
        AppSettings settings = new AppSettings(folder.resolve("missing.properties"));

        assertEquals(90, settings.getInt("rotation", 90, 0, 270));
        assertFalse(settings.getBoolean("motionOnly", false));
    }

    @Test
    void remembersValuesBetweenRuns(@TempDir Path folder) {
        Path file = folder.resolve("settings.properties");
        AppSettings first = new AppSettings(file);
        first.set("rotation", 270);
        first.set("motionOnly", false);
        first.save();

        AppSettings second = new AppSettings(file);
        assertEquals(270, second.getInt("rotation", 0, 0, 270));
        assertFalse(second.getBoolean("motionOnly", true));
    }

    @Test
    void ignoresGarbageAndClampsRanges(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("settings.properties");
        Files.writeString(file, "sensitivity=banana\nvolume=500\n");
        AppSettings settings = new AppSettings(file);

        assertEquals(5, settings.getInt("sensitivity", 5, 1, 10));
        assertEquals(100, settings.getInt("volume", 70, 0, 100));
    }
}
