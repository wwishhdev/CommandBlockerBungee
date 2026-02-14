package com.wish.commandblockervelocity.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.managers.ConfigManager;
import com.wish.commandblockervelocity.managers.CooldownManager;
import com.wish.commandblockervelocity.managers.WebhookManager;
import com.wish.commandblockervelocity.utils.NotificationAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public class ChatListener {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager config;
    private final CooldownManager cooldownManager;
    private final WebhookManager webhookManager;

    public ChatListener(CommandBlockerVelocity plugin, ConfigManager config, CooldownManager cooldownManager, WebhookManager webhookManager) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
        this.webhookManager = webhookManager;
    }

    @Subscribe(order = PostOrder.LATE)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;

        Player player = (Player) event.getCommandSource();
        
        // Granular Permissions
        boolean bypassAll = player.hasPermission(config.getBypassAllPermission());
        if (bypassAll) return;

        String fullCommand = event.getCommand(); // Velocity gives command without slash usually

        if (isCommandBlocked(fullCommand)) {
             // Check Bypass Block
            if (player.hasPermission(config.getBypassBlockPermission())) return;

             // Check Cooldown
            boolean bypassCooldown = player.hasPermission(config.getBypassCooldownPermission());
            boolean onCooldown = false;
            
            if (!bypassCooldown) {
                onCooldown = cooldownManager.handleCooldown(player);
            }

            event.setResult(CommandExecuteEvent.CommandResult.denied());
            
            if (!onCooldown) {
                player.sendMessage(config.getBlockMessage());
            }
            
            // Webhook
            webhookManager.sendWebhook(player.getUsername(), fullCommand);

            if (config.isNotificationsEnabled()) {
                notifyStaff(player, fullCommand);
            }
        }
    }

    @Subscribe(order = PostOrder.LATE)
    public void onTabComplete(TabCompleteEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String partialMessage = event.getPartialMessage();
        if (partialMessage.startsWith("/")) partialMessage = partialMessage.substring(1);

        String[] parts = partialMessage.trim().split("\\s+", 2);
        String baseCommand = parts[0];

        if (isCommandBlocked(baseCommand)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;
            event.getSuggestions().clear();
        }
    }

    private boolean isCommandBlocked(String command) {
        if (command == null || command.trim().isEmpty()) return false;

        String cleanCommand = command.trim().toLowerCase();
        // Just in case
        if (cleanCommand.startsWith("/")) {
            cleanCommand = cleanCommand.substring(1);
        }

        // FIX: Limpiamos espacios sobrantes despues de la barra y usamos regex para el split
        cleanCommand = cleanCommand.trim();
        String[] parts = cleanCommand.split("\\s+", 2);
        
        if (parts.length == 0) return false;
        String baseCommand = parts[0];

        if (baseCommand.isEmpty()) return false;

        // 1. Allowed Commands Check
        if (config.isAllowedCommandsEnabled()) {
            for (String allowed : config.getAllowedCommands()) {
                if (allowed == null) continue;
                String allowedLower = allowed.toLowerCase();
                if (baseCommand.equals(allowedLower) || cleanCommand.startsWith(allowedLower + " ")) return false;
            }
        }

        // 2. Blocked Commands Check
        List<String> blockedCommands = config.getBlockedCommands();

        for (String blockedCmd : blockedCommands) {
            if (blockedCmd == null) continue;
            String blockedLower = blockedCmd.toLowerCase();

            // Exact match
            if (baseCommand.equals(blockedLower)) return true;

            // Plugin prefix: "minecraft:op" == "minecraft:op"
            if (config.isBlockPluginPrefix()) {
                 // Check if command is exactly "plugin:command" where command is blocked
                 if (baseCommand.contains(":")) {
                     String[] cmdParts = baseCommand.split(":", 2);
                     if (cmdParts.length > 1 && cmdParts[1].equals(blockedLower)) {
                         return true;
                     }
                 }
            }

            // Alias: Help subcommand (op help)
            if (config.isBlockHelpSubcommand()) {
                 if (cleanCommand.equals(blockedLower + " help") || cleanCommand.startsWith(blockedLower + " help ")) {
                     return true;
                 }
            }
        }
        return false;
    }

    private void notifyStaff(Player offender, String command) {
        String safePlayer = config.escape(offender.getUsername());
        String safeCommand = config.escape(command);

        String msg = config.getNotifyMessageRaw()
                .replace("{player}", safePlayer)
                .replace("{command}", safeCommand);
        
        Component message = config.color(msg);
        
        // Interactive Actions
        if (config.isNotificationActionsEnabled()) {
            List<NotificationAction> actions = config.getNotificationActions();
            for (NotificationAction action : actions) {
                String label = action.getLabel().replace("{player}", safePlayer);
                String hover = action.getHover().replace("{player}", safePlayer);
                // Sanitize username for command execution
                String sanitizedPlayer = offender.getUsername().replaceAll("[^a-zA-Z0-9_]", "");
                String cmd = action.getCommand().replace("{player}", sanitizedPlayer);
                
                Component actionComp = MiniMessage.miniMessage().deserialize(label)
                        .hoverEvent(HoverEvent.showText(MiniMessage.miniMessage().deserialize(hover)))
                        .clickEvent(ClickEvent.runCommand(cmd));
                
                message = message.append(actionComp);
            }
        }

        final Component finalMessage = message;
        plugin.getProxy().getAllPlayers().stream()
                .filter(p -> p.hasPermission(config.getNotifyPermission()))
                .forEach(p -> p.sendMessage(finalMessage));
    }
}
