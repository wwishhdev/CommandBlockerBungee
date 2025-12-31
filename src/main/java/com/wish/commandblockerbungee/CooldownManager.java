package com.wish.commandblockerbungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CooldownManager {
    private final CommandBlockerBungee plugin;
    private final Map<UUID, CommandAttempts> playerAttempts;

    public CooldownManager(CommandBlockerBungee plugin) {
        this.plugin = plugin;
        this.playerAttempts = new ConcurrentHashMap<>();

        // Schedule cleanup
        plugin.getProxy().getScheduler().schedule(plugin, this::cleanupOldAttempts, 30, 30, TimeUnit.MINUTES);
    }

    private static class CommandAttempts {
        private int attempts;
        private long lastAttempt;
        private long timeoutUntil;

        public CommandAttempts() {
            this.attempts = 0;
            this.lastAttempt = System.currentTimeMillis();
            this.timeoutUntil = 0;
        }

        public boolean isTimedOut() {
            return System.currentTimeMillis() < timeoutUntil;
        }

        public long getRemainingTimeout() {
            return Math.max(0, timeoutUntil - System.currentTimeMillis()) / 1000;
        }
    }

    public boolean handleCooldown(ProxiedPlayer player, String command) {
        if (!plugin.getConfigManager().getBoolean("cooldown.enabled", true) || player == null) return false;

        try {
            UUID uuid = player.getUniqueId();
            CommandAttempts attempts = playerAttempts.computeIfAbsent(uuid, k -> new CommandAttempts());

            // Check for timeout
            if (attempts.isTimedOut()) {
                String timeoutMessage = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfigManager().getString("cooldown.timeout-message", "&cYou have exceeded the attempt limit. Wait &e{time} &cto try again.")
                        .replace("{time}", attempts.getRemainingTimeout() + "s"));

                player.sendMessage(new TextComponent(timeoutMessage));
                return true;
            }

            int resetAfter = plugin.getConfigManager().getInt("cooldown.reset-after", 600);

            // Check if attempts should be reset
            long timeSinceLastAttempt = (System.currentTimeMillis() - attempts.lastAttempt) / 1000;
            if (timeSinceLastAttempt > resetAfter) {
                attempts.attempts = 0;
            }

            // Increment attempts
            attempts.attempts++;
            attempts.lastAttempt = System.currentTimeMillis();

            int maxAttempts = plugin.getConfigManager().getInt("cooldown.max-attempts", 3);

            // Check if should be timed out
            if (attempts.attempts >= maxAttempts) {
                int timeoutDuration = plugin.getConfigManager().getInt("cooldown.timeout-duration", 300);
                attempts.timeoutUntil = System.currentTimeMillis() + (timeoutDuration * 1000L);

                String timeoutMessage = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfigManager().getString("cooldown.timeout-message", "&cYou have exceeded the attempt limit. Wait &e{time} &cto try again.")
                        .replace("{time}", timeoutDuration + "s"));

                player.sendMessage(new TextComponent(timeoutMessage));

                if (plugin.getConfigManager().getBoolean("notifications.notify-on-timeout", true)) {
                    String timeoutNotification = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfigManager().getString("notifications.timeout-message", "&e{player} &7has been timed out for exceeding attempts.")
                            .replace("{player}", player.getName()));
                    plugin.notifyStaff(timeoutNotification);
                }

                return true;
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Critical error in cooldown system: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void cleanupOldAttempts() {
        if (playerAttempts == null) return;

        int resetAfter = plugin.getConfigManager().getInt("cooldown.reset-after", 600);

        try {
            long currentTime = System.currentTimeMillis();
            playerAttempts.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue().lastAttempt) / 1000 > resetAfter * 2);
        } catch (Exception e) {
            plugin.getLogger().warning("Error cleaning up old attempts: " + e.getMessage());
        }
    }

    public void cleanup() {
        playerAttempts.clear();
    }
}
