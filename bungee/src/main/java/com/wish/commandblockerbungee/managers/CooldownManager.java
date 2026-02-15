package com.wish.commandblockerbungee.managers;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import com.wish.commandblockerbungee.database.DatabaseManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CooldownManager {

    private final CommandBlockerBungee plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final Map<UUID, CommandAttempts> playerAttempts;

    public CooldownManager(CommandBlockerBungee plugin, ConfigManager configManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerAttempts = new ConcurrentHashMap<>();
        
        // Schedule cleanup task
        plugin.getProxy().getScheduler().schedule(plugin, this::cleanupOldAttempts, 30, 30, TimeUnit.MINUTES);
    }

    public void loadPlayer(UUID uuid) {
        if (!configManager.isDatabaseEnabled()) return;
        
        databaseManager.loadCooldown(uuid).thenAccept(data -> {
            if (data != null) {
                playerAttempts.compute(uuid, (key, current) -> {
                    if (current == null) {
                        // No local attempts yet, just load db
                        CommandAttempts attempts = new CommandAttempts();
                        attempts.attempts = data.attempts;
                        attempts.lastAttempt = data.lastAttempt;
                        attempts.timeoutUntil = data.timeoutUntil;
                        return attempts;
                    } else {
                        // Race condition hit: Player chattered before DB loaded.
                        // Strategy: Keep the stricter of the two or merge.
                        // Here we take DB timeout if it's longer, and sum attempts? 
                        // Safer to just adopt DB state if it's a timeout, otherwise keep local activity?
                        // Let's adopt DB state BUT if local has more recent activity, update lastAttempt.
                        
                        // Simple merge: If DB says timed out, enforce it.
                        if (System.currentTimeMillis() < data.timeoutUntil) {
                            current.timeoutUntil = data.timeoutUntil;
                        }
                        // Add DB attempts to current? Or just max?
                        current.attempts = Math.max(current.attempts, data.attempts);
                        return current;
                    }
                });
            }
        });
    }

    public boolean handleCooldown(ProxiedPlayer player) {
        if (!configManager.isCooldownEnabled()) return false;

        UUID uuid = player.getUniqueId();
        CommandAttempts attempts = playerAttempts.computeIfAbsent(uuid, k -> new CommandAttempts());

        // Check timeout
        if (attempts.isTimedOut()) {
            String timeLeft = String.valueOf(attempts.getRemainingTimeout());
            plugin.adventure().player(player).sendMessage(configManager.parse(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));
            return true;
        }

        // Check reset logic (Fix Bear Trap)
        synchronized(attempts) {
            long timeSinceLastAttempt = (System.currentTimeMillis() - attempts.lastAttempt) / 1000;
            
            // Reset if reset time passed OR if they served their timeout penalty
            if (timeSinceLastAttempt > configManager.getResetAfter() || (attempts.attempts >= configManager.getMaxAttempts() && !attempts.isTimedOut())) {
                attempts.resetAttempts();
            }

            attempts.incrementAttempts();

            if (attempts.attempts > configManager.getMaxAttempts()) {
                attempts.setTimeout(configManager.getTimeoutDuration());
                String timeLeft = String.valueOf(configManager.getTimeoutDuration());
                
                plugin.adventure().player(player).sendMessage(configManager.parse(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));

                // Notify staff about the timeout event specifically
                if (configManager.isNotifyOnTimeout()) {
                    notifyStaff(configManager.getTimeoutNotificationRaw().replace("{player}", configManager.escape(player.getName())));
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

    private void notifyStaff(String messageRaw) {
        if (!configManager.isNotificationsEnabled() || messageRaw == null) return;

        plugin.getProxy().getPlayers().stream()
                .filter(p -> p.hasPermission(configManager.getNotifyPermission()))
                .forEach(p -> plugin.adventure().player(p).sendMessage(configManager.parse(messageRaw)));
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
        volatile long lastAttempt = System.currentTimeMillis();
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