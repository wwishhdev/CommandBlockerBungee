package com.wish.commandblockervelocity.managers;

import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CooldownManager {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager configManager;
    private final Map<UUID, CommandAttempts> playerAttempts;

    public CooldownManager(CommandBlockerVelocity plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerAttempts = new ConcurrentHashMap<>();

        plugin.getProxy().getScheduler().buildTask(plugin, this::cleanupOldAttempts)
                .repeat(30, TimeUnit.MINUTES)
                .schedule();
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

        long timeSinceLastAttempt = (System.currentTimeMillis() - attempts.lastAttempt) / 1000;
        if (timeSinceLastAttempt > configManager.getResetAfter()) {
            attempts.attempts = 0;
        }

        attempts.attempts++;
        attempts.lastAttempt = System.currentTimeMillis();

        if (attempts.attempts >= configManager.getMaxAttempts()) {
            attempts.timeoutUntil = System.currentTimeMillis() + (configManager.getTimeoutDuration() * 1000L);
            String timeLeft = String.valueOf(configManager.getTimeoutDuration());

            player.sendMessage(configManager.color(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));

            if (configManager.isNotifyOnTimeout()) {
                notifyStaff(configManager.getTimeoutNotificationRaw().replace("{player}", player.getUsername()));
            }
            return true;
        }

        return false;
    }

    public void removeCooldown(UUID uuid) {
        playerAttempts.remove(uuid);
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

        boolean isTimedOut() {
            return System.currentTimeMillis() < timeoutUntil;
        }

        long getRemainingTimeout() {
            return Math.max(0, timeoutUntil - System.currentTimeMillis()) / 1000;
        }
    }
}
