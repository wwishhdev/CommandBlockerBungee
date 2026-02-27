package com.wish.commandblockerbungee.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.database.DatabaseManager;

import net.md_5.bungee.api.connection.ProxiedPlayer;

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
            if (data == null) return;

            // FIX: Don't synchronize inside compute(). Use putIfAbsent + then operate on the
            // canonical instance to avoid lock-ordering deadlocks between the CHM segment
            // lock and the CommandAttempts monitor.
            CommandAttempts fresh = new CommandAttempts();
            CommandAttempts existing = playerAttempts.putIfAbsent(uuid, fresh);
            CommandAttempts canonical = (existing != null) ? existing : fresh;

            synchronized (canonical) {
                // Always keep the highest attempt count (handles fast reconnects)
                canonical.attempts = Math.max(canonical.attempts, data.attempts);
                // Only apply the DB timeout if it is still active
                if (System.currentTimeMillis() < data.timeoutUntil) {
                    canonical.timeoutUntil = data.timeoutUntil;
                }
                // Keep the more recent last-attempt timestamp
                if (data.lastAttempt > canonical.lastAttempt) {
                    canonical.lastAttempt = data.lastAttempt;
                }
            }
        });
    }

    public boolean handleCooldown(ProxiedPlayer player) {
        if (!configManager.isCooldownEnabled()) return false;

        UUID uuid = player.getUniqueId();
        CommandAttempts attempts = playerAttempts.computeIfAbsent(uuid, k -> new CommandAttempts());

        synchronized (attempts) {
            // Check timeout
            if (attempts.isTimedOut()) {
                String timeLeft = String.valueOf(attempts.getRemainingTimeout());
                plugin.adventure().player(player).sendMessage(configManager.parse(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));
                return true;
            }

            long timeSinceLastAttempt = (System.currentTimeMillis() - attempts.lastAttempt) / 1000;

            // Reset if idle time passed OR if they served their timeout penalty
            if (timeSinceLastAttempt > configManager.getResetAfter()
                    || (attempts.timeoutUntil > 0 && System.currentTimeMillis() >= attempts.timeoutUntil)) {
                attempts.resetAttempts();
                attempts.timeoutUntil = 0;
            }

            attempts.incrementAttempts();

            // FIX: Use >= so that the Nth attempt (exactly at max) triggers the timeout,
            // not the (N+1)th attempt.
            if (attempts.attempts >= configManager.getMaxAttempts()) {
                attempts.setTimeout(configManager.getTimeoutDuration());
                String timeLeft = String.valueOf(configManager.getTimeoutDuration());

                plugin.adventure().player(player).sendMessage(configManager.parse(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));

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
            synchronized (attempts) {
                databaseManager.saveCooldown(uuid, attempts.attempts, attempts.lastAttempt, attempts.timeoutUntil);
            }
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
        playerAttempts.entrySet().removeIf(entry -> {
            CommandAttempts a = entry.getValue();
            boolean shouldRemove;
            synchronized (a) {
                shouldRemove = (currentTime - a.lastAttempt) / 1000 > resetAfter * 2L;
                if (shouldRemove && configManager.isDatabaseEnabled()) {
                    databaseManager.saveCooldown(entry.getKey(), a.attempts, a.lastAttempt, a.timeoutUntil);
                }
            }
            return shouldRemove;
        });
    }

    /**
     * FIX: Persist all in-memory cooldown data to DB before clearing,
     * so that active timeouts survive reloads and shutdowns.
     */
    public void clear() {
        if (configManager.isDatabaseEnabled()) {
            playerAttempts.forEach((uuid, attempts) -> {
                synchronized (attempts) {
                    databaseManager.saveCooldown(uuid, attempts.attempts, attempts.lastAttempt, attempts.timeoutUntil);
                }
            });
        }
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