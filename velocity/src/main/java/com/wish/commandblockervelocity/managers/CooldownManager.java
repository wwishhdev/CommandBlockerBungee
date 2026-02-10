package com.wish.commandblockervelocity.managers;

import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;

import com.wish.commandblockervelocity.database.DatabaseManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CooldownManager {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final Map<UUID, CommandAttempts> playerAttempts;

    public CooldownManager(CommandBlockerVelocity plugin, ConfigManager configManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerAttempts = new ConcurrentHashMap<>();

        plugin.getProxy().getScheduler().buildTask(plugin, this::cleanupOldAttempts)
                .repeat(30, TimeUnit.MINUTES)
                .schedule();
    }

    public void loadPlayer(UUID uuid) {
        if (!configManager.isDatabaseEnabled()) return;

        databaseManager.loadCooldown(uuid).thenAccept(data -> {
            if (data != null) {
                playerAttempts.compute(uuid, (key, current) -> {
                    if (current == null) {
                         CommandAttempts attempts = new CommandAttempts();
                        attempts.attempts = data.attempts;
                        attempts.lastAttempt = data.lastAttempt;
                        attempts.timeoutUntil = data.timeoutUntil;
                        return attempts;
                    } else {
                        // Merge
                        if (System.currentTimeMillis() < data.timeoutUntil) {
                            current.timeoutUntil = data.timeoutUntil;
                        }
                        current.attempts = Math.max(current.attempts, data.attempts);
                        return current;
                    }
                });
            }
        });
    }

    public boolean handleCooldown(Player player) {
        if (!configManager.isCooldownEnabled()) return false;

        UUID uuid = player.getUniqueId();
        CommandAttempts attempts = playerAttempts.computeIfAbsent(uuid, k -> new CommandAttempts());

        if (attempts.isTimedOut()) {
            String timeLeft = String.valueOf(attempts.getRemainingTimeout());
            player.sendMessage(configManager.color(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));
            return true;
        }

        // Check reset
        synchronized(attempts) {
            long timeSinceLastAttempt = (System.currentTimeMillis() - attempts.lastAttempt) / 1000;
            if (timeSinceLastAttempt > configManager.getResetAfter()) {
                attempts.resetAttempts();
            }

            attempts.incrementAttempts();

            if (attempts.attempts >= configManager.getMaxAttempts()) {
                attempts.setTimeout(configManager.getTimeoutDuration());
                String timeLeft = String.valueOf(configManager.getTimeoutDuration());

                player.sendMessage(configManager.color(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));

                if (configManager.isNotifyOnTimeout()) {
                    notifyStaff(configManager.getTimeoutNotificationRaw().replace("{player}", configManager.escape(player.getUsername())));
                }
                return true;
            }
        }

        return false;
    }

    public void removeCooldown(UUID uuid) {
        CommandAttempts attempts = playerAttempts.remove(uuid);
        if (attempts != null && configManager.isDatabaseEnabled()) {
            databaseManager.saveCooldown(uuid, attempts.attempts, attempts.lastAttempt, attempts.timeoutUntil);
        }
    }

    private void notifyStaff(String message) {
        if (!configManager.isNotificationsEnabled() || message == null) return;
        
        plugin.getProxy().getAllPlayers().stream()
                .filter(p -> p.hasPermission(configManager.getNotifyPermission()))
                .forEach(p -> p.sendMessage(configManager.color(message)));
    }

    private void cleanupOldAttempts() {
        long currentTime = System.currentTimeMillis();
        int resetAfter = configManager.getResetAfter();
        playerAttempts.entrySet().removeIf(entry ->
                (currentTime - entry.getValue().lastAttempt) / 1000 > resetAfter * 2);
    }

    public void clear() {
        playerAttempts.clear();
    }

    private static class CommandAttempts {
        int attempts = 0;
        long lastAttempt = System.currentTimeMillis();
        long timeoutUntil = 0;

        synchronized boolean isTimedOut() {
            return System.currentTimeMillis() < timeoutUntil;
        }

        synchronized long getRemainingTimeout() {
            return Math.max(0, timeoutUntil - System.currentTimeMillis()) / 1000;
        }

        synchronized void incrementAttempts() {
            this.attempts++;
            this.lastAttempt = System.currentTimeMillis();
        }

        synchronized void resetAttempts() {
            this.attempts = 0;
        }

        synchronized void setTimeout(long durationSeconds) {
            this.timeoutUntil = System.currentTimeMillis() + (durationSeconds * 1000L);
        }
    }
}
