package org.example.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppLogger {
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = LOG_DIR + File.separator + "application.log";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Ensures that the target logging directory exists on the file system.
     */
    private static void ensureLogDirExists() {
        File dir = new File(LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Logs a message with a specific log level to standard output/error and appends it to the log file.
     */
    public static synchronized void log(LogLevel level, String message) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String formattedMessage = String.format("[%s] [%-9s] %s", timestamp, level.name(), message);

        if (level == LogLevel.ERROR) {
            System.err.println(formattedMessage);
        } else {
            System.out.println(formattedMessage);
        }

        ensureLogDirExists();
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.println(formattedMessage);

        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    /**
     * Logs an error message along with the full exception stack trace to the log file.
     */
    public static synchronized void logError(String message, Throwable throwable) {
        log(LogLevel.ERROR, message + " | Exception: " + throwable.getMessage());
        ensureLogDirExists();
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            throwable.printStackTrace(out);

        } catch (IOException e) {
            System.err.println("Failed to write exception stacktrace: " + e.getMessage());
        }
    }
}