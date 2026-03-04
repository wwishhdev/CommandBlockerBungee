package com.wish.commandblockervelocity.managers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.database.DatabaseManager;
import com.wish.commandblockervelocity.utils.PunishmentAction;

public class CooldownManager {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final Map<UUID, CommandAttempts> playerAttempts;
    private final ScheduledTask cleanupTask;

    public CooldownManager(CommandBlockerVelocity plugin, ConfigManager configManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.playerAttempts = new ConcurrentHashMap<>();

        this.cleanupTask = plugin.getProxy().getScheduler().buildTask(plugin, this::cleanupOldAttempts)
                .repeat(30, TimeUnit.MINUTES)
                .schedule();
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
                canonical.attempts = Math.max(canonical.attempts, data.attempts);
                if (System.currentTimeMillis() < data.timeoutUntil) {
                    canonical.timeoutUntil = data.timeoutUntil;
                }
                if (data.lastAttempt > canonical.lastAttempt) {
                    canonical.lastAttempt = data.lastAttempt;
                }
            }
        });
    }

    public boolean handleCooldown(Player player) {
        if (!configManager.isCooldownEnabled()) return false;

        UUID uuid = player.getUniqueId();
        CommandAttempts attempts = playerAttempts.computeIfAbsent(uuid, k -> new CommandAttempts());

        synchronized (attempts) {
            if (attempts.isTimedOut()) {
                String timeLeft = String.valueOf(attempts.getRemainingTimeout());
                player.sendMessage(configManager.color(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));
                return true;
            }

            long timeSinceLastAttempt = (System.currentTimeMillis() - attempts.lastAttempt) / 1000;

            if (timeSinceLastAttempt > configManager.getResetAfter()
                    || (attempts.timeoutUntil > 0 && System.currentTimeMillis() >= attempts.timeoutUntil)) {
                attempts.resetAttempts();
                attempts.timeoutUntil = 0;
            }

            attempts.incrementAttempts();

            // Auto-punishments: execute commands when thresholds are reached
            if (configManager.isAutoPunishmentsEnabled()) {
                checkAutoPunishments(player, attempts.attempts);
            }

            // FIX: Use >= so the Nth attempt (exactly at max) triggers the timeout.
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

    private void checkAutoPunishments(Player player, int currentAttempts) {
        List<PunishmentAction> punishments = configManager.getAutoPunishments();
        for (PunishmentAction action : punishments) {
            if (currentAttempts == action.getThreshold()) {
                String sanitizedPlayer = player.getUsername().replaceAll("[^a-zA-Z0-9_]", "");
                String cmd = action.getCommand().replace("{player}", sanitizedPlayer);
                plugin.getProxy().getCommandManager().executeAsync(
                        plugin.getProxy().getConsoleCommandSource(), cmd
                );
                plugin.getLogger().info("Auto-punishment executed for " + player.getUsername() + ": " + cmd);
            }
        }
    }

    public void removeCooldown(UUID uuid) {
        CommandAttempts attempts = playerAttempts.remove(uuid);
        if (attempts != null && configManager.isDatabaseEnabled()) {
            synchronized (attempts) {
                databaseManager.saveCooldown(uuid, attempts.attempts, attempts.lastAttempt, attempts.timeoutUntil);
            }
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
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
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
