package com.wish.commandblockervelocity.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;

/**
 * Writes audit log entries to a date-rolling file inside the plugin's data directory.
 * Each day gets its own file: logs/2026-02-27.log
 * All I/O is delegated to the shared executor to avoid blocking the event thread.
 */
public class FileLogger {

    private static final DateTimeFormatter DATE_FORMAT      = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logsDir;
    private final ExecutorService executor;
    private final Logger logger;
    private final int maxFiles;

    public FileLogger(Path dataDirectory, ExecutorService executor, Logger logger, int maxFiles) {
        this.logsDir  = dataDirectory.resolve("logs");
        this.executor = executor;
        this.logger   = logger;
        this.maxFiles = maxFiles;

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
        String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
        String line = "[" + timestamp + "] BLOCKED | Player: " + playerName
                + " (" + playerUUID + ") | Server: " + server
                + " | Command: " + command;
        writeAsync(line);
    }

    /**
     * Appends a timeout event to today's audit log file.
     */
    public void logTimeout(String playerName, String playerUUID, String server) {
        String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
        String line = "[" + timestamp + "] TIMEOUT | Player: " + playerName
                + " (" + playerUUID + ") | Server: " + server;
        writeAsync(line);
    }

    private void writeAsync(String line) {
        executor.execute(() -> {
            Path logFile = logsDir.resolve(DATE_FORMAT.format(LocalDate.now()) + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                logger.error("Failed to write to audit log: " + e.getMessage());
            }
            rotateOldLogs();
        });
    }

    /**
     * Deletes the oldest log files when the number of files exceeds maxFiles.
     */
    private void rotateOldLogs() {
        try {
            List<Path> logFiles = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir, "*.log")) {
                for (Path entry : stream) {
                    logFiles.add(entry);
                }
            }
            if (logFiles.size() <= maxFiles) return;

            logFiles.sort(Comparator.comparingLong(p -> {
                try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0L; }
            }));
            int toDelete = logFiles.size() - maxFiles;
            for (int i = 0; i < toDelete; i++) {
                try {
                    Files.deleteIfExists(logFiles.get(i));
                } catch (IOException e) {
                    logger.error("Failed to delete old audit log: " + logFiles.get(i).getFileName());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to rotate audit logs: " + e.getMessage());
        }
    }
}
