package com.wish.commandblockerbungee.listeners;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.managers.ConfigManager;
import com.wish.commandblockerbungee.managers.CooldownManager;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.List;

public class ChatListener implements Listener {

    private final CommandBlockerBungee plugin;
    private final ConfigManager config;
    private final CooldownManager cooldownManager;

    public ChatListener(CommandBlockerBungee plugin, ConfigManager config, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (!event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (player.hasPermission(config.getBypassPermission())) return;

        String fullCommand = event.getMessage();

        if (isCommandBlocked(fullCommand)) {
            // Check Cooldown
            if (cooldownManager.handleCooldown(player)) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            plugin.adventure().player(player).sendMessage(config.getBlockMessage());

            if (config.isNotificationsEnabled()) {
                notifyStaff(player, fullCommand);
            }
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (player.hasPermission(config.getBypassPermission())) return;

        String cursor = event.getCursor().toLowerCase();
        if (cursor.startsWith("/")) cursor = cursor.substring(1);
        
        String[] parts = cursor.split(" ", 2);
        String base = parts[0];
        
        if (isCommandBlocked(base)) {
            event.setCancelled(true);
            event.getSuggestions().clear();
        }
    }

    private boolean isCommandBlocked(String command) {
        if (command == null || command.trim().isEmpty()) return false;

        String cleanCommand = command.toLowerCase();
        if (cleanCommand.startsWith("/")) cleanCommand = cleanCommand.substring(1);

        String[] parts = cleanCommand.split(" ", 2);
        String baseCommand = parts[0];

        if (baseCommand.isEmpty()) return false;

        // 1. Allowed Commands Check
        if (config.isAllowedCommandsEnabled()) {
            for (String allowed : config.getAllowedCommands()) {
                if (allowed == null) continue;
                String allowedLower = allowed.toLowerCase();
                if (baseCommand.equals(allowedLower)) return false;
                if (cleanCommand.startsWith(allowedLower + " ")) return false;
            }
        }

        // 2. Blocked Commands Check
        List<String> blockedCommands = config.getBlockedCommands();
        if (!config.isAliasDetectionEnabled()) {
            return blockedCommands.contains(baseCommand);
        }

        for (String blockedCmd : blockedCommands) {
            if (blockedCmd == null) continue;
            blockedCmd = blockedCmd.toLowerCase();

            // Exact match
            if (baseCommand.equals(blockedCmd)) return true;

            // Plugin prefix (minecraft:op)
            if (config.isBlockPluginPrefix() && baseCommand.startsWith(blockedCmd + ":")) return true;
            if (config.isBlockPluginPrefix() && baseCommand.contains(":" + blockedCmd)) return true; // Safety catch for other prefixes

            // Alias: Help subcommand (op help)
            if (config.isBlockHelpSubcommand()) {
                 if (cleanCommand.equals(blockedCmd + " help") || cleanCommand.startsWith(blockedCmd + " help ")) {
                     return true;
                 }
            }
        }
        return false;
    }

    private void notifyStaff(ProxiedPlayer offender, String command) {
        String msgRaw = config.getNotifyMessageRaw()
                .replace("{player}", offender.getName())
                .replace("{command}", command);
        
        plugin.getProxy().getPlayers().stream()
                .filter(p -> p.hasPermission(config.getNotifyPermission()))
                .forEach(p -> plugin.adventure().player(p).sendMessage(config.parse(msgRaw)));
    }
}