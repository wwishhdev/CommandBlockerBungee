package com.wish.commandblockervelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.managers.ConfigManager;
import com.wish.commandblockervelocity.managers.CooldownManager;

import java.util.List;

public class ChatListener {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager config;
    private final CooldownManager cooldownManager;

    public ChatListener(CommandBlockerVelocity plugin, ConfigManager config, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;

        Player player = (Player) event.getCommandSource();
        if (player.hasPermission(config.getBypassPermission())) return;

        String fullCommand = event.getCommand(); // Velocity gives command without slash usually

        if (isCommandBlocked(fullCommand)) {
             // Check Cooldown
            if (cooldownManager.handleCooldown(player)) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
                return;
            }

            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(config.getBlockMessage());

            if (config.isNotificationsEnabled()) {
                notifyStaff(player, fullCommand);
            }
        }
    }

    @Subscribe
    public void onTabComplete(TabCompleteEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(config.getBypassPermission())) return;

        String partialMessage = event.getPartialMessage();
        if (partialMessage.startsWith("/")) partialMessage = partialMessage.substring(1);

        String[] parts = partialMessage.split(" ", 2);
        String baseCommand = parts[0];

        if (isCommandBlocked(baseCommand)) {
            event.getSuggestions().clear();
        }
    }

    private boolean isCommandBlocked(String command) {
        if (command == null || command.trim().isEmpty()) return false;

        String cleanCommand = command.toLowerCase();
        // Just in case
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
            if (config.isBlockPluginPrefix() && baseCommand.contains(":" + blockedCmd)) return true; 

            // Alias: Help subcommand (op help)
            if (config.isBlockHelpSubcommand()) {
                 if (cleanCommand.equals(blockedCmd + " help") || cleanCommand.startsWith(blockedCmd + " help ")) {
                     return true;
                 }
            }
        }
        return false;
    }

    private void notifyStaff(Player offender, String command) {
        String msg = config.getNotifyMessageRaw()
                .replace("{player}", offender.getUsername())
                .replace("{command}", command);
        
        plugin.getProxy().getAllPlayers().stream()
                .filter(p -> p.hasPermission(config.getNotifyPermission()))
                .forEach(p -> p.sendMessage(config.color(msg)));
    }
}
