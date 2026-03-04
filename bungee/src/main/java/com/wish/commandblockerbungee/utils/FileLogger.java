package com.wish.commandblockerbungee.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * Writes audit log entries to a date-rolling file inside the plugin's data folder.
 * Each day gets its own file: logs/2026-02-27.log
 * All I/O is delegated to the shared executor to avoid blocking the main thread.
 */
public class FileLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final File logsDir;
    private final ExecutorService executor;
    private final Logger logger;
    private final int maxFiles;

    public FileLogger(File dataFolder, ExecutorService executor, Logger logger, int maxFiles) {
        this.logsDir = new File(dataFolder, "logs");
        this.executor = executor;
        this.logger = logger;
        this.maxFiles = maxFiles;

        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
    }

    /**
     * Appends a blocked-command event to today's audit log file.
     *
     * @param playerName  The player's username
     * @param playerUUID  The player's UUID string
     * @param server      The backend server name the player was on
     * @param command     The raw command that was blocked
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
            String fileName = DATE_FORMAT.format(LocalDate.now()) + ".log";
            File logFile = new File(logsDir, fileName);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                logger.warning("Failed to write to audit log: " + e.getMessage());
            }
            rotateOldLogs();
        });
    }

    /**
     * Deletes the oldest log files when the number of files exceeds maxFiles.
     */
    private void rotateOldLogs() {
        File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));
        if (logFiles == null || logFiles.length <= maxFiles) return;

        Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));
        int toDelete = logFiles.length - maxFiles;
        for (int i = 0; i < toDelete; i++) {
            if (!logFiles[i].delete()) {
                logger.warning("Failed to delete old audit log: " + logFiles[i].getName());
            }
        }
    }
}
