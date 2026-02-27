package com.wish.commandblockervelocity.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;

/**
 * Writes audit log entries to a date-rolling file inside the plugin's data directory.
 * Each day gets its own file: logs/2026-02-27.log
 * All I/O is delegated to the shared executor to avoid blocking the event thread.
 */
public class FileLogger {

    private static final SimpleDateFormat DATE_FORMAT      = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Path logsDir;
    private final ExecutorService executor;
    private final Logger logger;

    public FileLogger(Path dataDirectory, ExecutorService executor, Logger logger) {
        this.logsDir  = dataDirectory.resolve("logs");
        this.executor = executor;
        this.logger   = logger;

        try {
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            logger.error("Failed to create audit log directory: " + e.getMessage());
        }
    }

    /**
     * Appends a blocked-command event to today's audit log file.
     */
    public void logBlockedCommand(String playerName, String playerUUID, String server, String command) {
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        String line = "[" + timestamp + "] BLOCKED | Player: " + playerName
                + " (" + playerUUID + ") | Server: " + server
                + " | Command: " + command;
        writeAsync(line);
    }

    /**
     * Appends a timeout event to today's audit log file.
     */
    public void logTimeout(String playerName, String playerUUID, String server) {
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        String line = "[" + timestamp + "] TIMEOUT | Player: " + playerName
                + " (" + playerUUID + ") | Server: " + server;
        writeAsync(line);
    }

    private void writeAsync(String line) {
        executor.execute(() -> {
            Path logFile = logsDir.resolve(DATE_FORMAT.format(new Date()) + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                logger.error("Failed to write to audit log: " + e.getMessage());
            }
        });
    }
}
