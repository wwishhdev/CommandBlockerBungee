package com.wish.commandblockerbungee.managers;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CooldownManager {

    private final CommandBlockerBungee plugin;
    private final ConfigManager configManager;
    private final Map<UUID, CommandAttempts> playerAttempts;

    public CooldownManager(CommandBlockerBungee plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerAttempts = new ConcurrentHashMap<>();
        
        // Schedule cleanup task
        plugin.getProxy().getScheduler().schedule(plugin, this::cleanupOldAttempts, 30, 30, TimeUnit.MINUTES);
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

        // Check reset
        long timeSinceLastAttempt = (System.currentTimeMillis() - attempts.lastAttempt) / 1000;
        if (timeSinceLastAttempt > configManager.getResetAfter()) {
            attempts.attempts = 0;
        }

        attempts.attempts++;
        attempts.lastAttempt = System.currentTimeMillis();

        if (attempts.attempts >= configManager.getMaxAttempts()) {
            attempts.timeoutUntil = System.currentTimeMillis() + (configManager.getTimeoutDuration() * 1000L);
            String timeLeft = String.valueOf(configManager.getTimeoutDuration());
            
            plugin.adventure().player(player).sendMessage(configManager.parse(configManager.getTimeoutMessageRaw().replace("{time}", timeLeft + "s")));

            if (configManager.isNotifyOnTimeout()) {
                notifyStaff(configManager.getTimeoutNotificationRaw().replace("{player}", player.getName()));
            }
            return true;
        }

        return false;
    }

    public void removeCooldown(UUID uuid) {
        playerAttempts.remove(uuid);
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