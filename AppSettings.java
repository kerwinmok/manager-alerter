package com.kerwinmok.manageralerter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Remembers the camera rotation, sensitivity and volume between runs in a small file in the user's home folder. */
final class AppSettings {
    private final Path file;
    private final Properties values = new Properties();

    AppSettings() {
        this(Path.of(System.getProperty("user.home"), ".doorway-alert.properties"));
    }

    AppSettings(Path file) {
        this.file = file;
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        } catch (IOException | IllegalArgumentException ignored) {
            // first run or unreadable file: use defaults
        }
    }

    int getInt(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(values.getProperty(key, String.valueOf(fallback)).trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    boolean getBoolean(String key, boolean fallback) {
        String raw = values.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    void set(String key, Object value) {
        values.setProperty(key, String.valueOf(value));
    }

    void save() {
        try (OutputStream output = Files.newOutputStream(file)) {
            values.store(output, "doorway alert settings");
        } catch (IOException ignored) {
            // settings just will not be remembered
        }
    }
}
