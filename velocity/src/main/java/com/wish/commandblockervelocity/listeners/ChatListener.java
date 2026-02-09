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
            if (!bypassCooldown && cooldownManager.handleCooldown(player)) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
                return;
            }

            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(config.getBlockMessage());
            
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

        String[] parts = partialMessage.split(" ", 2);
        String baseCommand = parts[0];

        if (isCommandBlocked(baseCommand)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;
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
                String cmd = action.getCommand().replace("{player}", offender.getUsername());
                
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
