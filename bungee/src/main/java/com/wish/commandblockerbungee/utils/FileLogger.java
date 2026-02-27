package com.wish.commandblockerbungee.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * Writes audit log entries to a date-rolling file inside the plugin's data folder.
 * Each day gets its own file: logs/2026-02-27.log
 * All I/O is delegated to the shared executor to avoid blocking the main thread.
 */
public class FileLogger {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final File logsDir;
    private final ExecutorService executor;
    private final Logger logger;

    public FileLogger(File dataFolder, ExecutorService executor, Logger logger) {
        this.logsDir = new File(dataFolder, "logs");
        this.executor = executor;
        this.logger = logger;

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
            String fileName = DATE_FORMAT.format(new Date()) + ".log";
            File logFile = new File(logsDir, fileName);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                logger.warning("Failed to write to audit log: " + e.getMessage());
            }
        });
    }
}
