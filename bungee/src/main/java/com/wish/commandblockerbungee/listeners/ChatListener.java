package com.wish.commandblockerbungee.listeners;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.managers.ConfigManager;
import com.wish.commandblockerbungee.managers.CooldownManager;
import com.wish.commandblockerbungee.managers.WebhookManager;
import com.wish.commandblockerbungee.utils.NotificationAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.List;

public class ChatListener implements Listener {

    private final CommandBlockerBungee plugin;
    private final ConfigManager config;
    private final CooldownManager cooldownManager;
    private final WebhookManager webhookManager;

    public ChatListener(CommandBlockerBungee plugin, ConfigManager config, CooldownManager cooldownManager, WebhookManager webhookManager) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
        this.webhookManager = webhookManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (!event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        
        // Granular Permissions
        boolean bypassAll = player.hasPermission(config.getBypassAllPermission());
        if (bypassAll) return;

        String fullCommand = event.getMessage();

        if (isCommandBlocked(fullCommand)) {
            // Check Bypass Block
            if (player.hasPermission(config.getBypassBlockPermission())) return;

            // Check Cooldown (if not bypassed)
            boolean bypassCooldown = player.hasPermission(config.getBypassCooldownPermission());
            boolean onCooldown = false;
            
            if (!bypassCooldown) {
                // handleCooldown returns true if they are currently timed out OR just got timed out
                onCooldown = cooldownManager.handleCooldown(player);
            }

            event.setCancelled(true);
            
            // Only send block message if NOT on cooldown (cooldown manager sends its own message)
            // OR if you want both messages. Usually, if timed out, you get the timeout message.
            if (!onCooldown) {
                plugin.adventure().player(player).sendMessage(config.getBlockMessage());
            }
            
            // Webhook - Always send (Managers queues it)
            webhookManager.sendWebhook(player.getName(), fullCommand);

            // Notify Staff - Always notify (or filtered by settings)
            if (config.isNotificationsEnabled()) {
                notifyStaff(player, fullCommand);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String cursor = event.getCursor().toLowerCase();
        if (cursor.startsWith("/")) cursor = cursor.substring(1);
        
        // Fix: Properly handle spacing in tab complete (Unicode-aware Regex split)
        // (?U) enables UNICODE_CHARACTER_CLASS mode
        String[] parts = cursor.trim().split("(?U)\\s+", 2);
        String base = parts[0];
        
        if (isCommandBlocked(base)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;
            event.setCancelled(true);
            event.getSuggestions().clear();
        }
    }

    private boolean isCommandBlocked(String command) {
        if (command == null || command.trim().isEmpty()) return false;

        String cleanCommand = command.trim().toLowerCase();
        if (cleanCommand.startsWith("/")) {
            cleanCommand = cleanCommand.substring(1);
        }
        
                        // Fix: Trim again to handle "/ op" -> " op" -> "op"
        
                        cleanCommand = cleanCommand.trim();
        
                        
        
                        // Fix: Normalize spacing around colons to prevent "/minecraft : op" bypass
        
                        cleanCommand = cleanCommand.replaceAll("\\s*:\\s*", ":");
        
                
        
                        // Use Unicode-aware regex to catch non-breaking spaces
        
                String[] parts = cleanCommand.split("(?U)\\s+", 2);
        
                
        
                if (parts.length == 0) return false;
        String baseCommand = parts[0];

        if (baseCommand.isEmpty()) return false;

        // 1. Allowed Commands Check
        if (config.isAllowedCommandsEnabled()) {
            for (String allowed : config.getAllowedCommands()) {
                if (allowed == null) continue;
                String allowedLower = allowed.toLowerCase();
                // Exact match or sub-command
                if (baseCommand.equals(allowedLower) || cleanCommand.startsWith(allowedLower + " ")) return false;
            }
        }

        // 2. Blocked Commands Check
        List<String> blockedCommands = config.getBlockedCommands();
        
        for (String blockedCmd : blockedCommands) {
            if (blockedCmd == null) continue;
            String blockedLower = blockedCmd.toLowerCase();

            // Exact match: "op" == "op"
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

    private void notifyStaff(ProxiedPlayer offender, String command) {
        String safePlayer = config.escape(offender.getName());
        String safeCommand = config.escape(command);
        
        String msgRaw = config.getNotifyMessageRaw()
                .replace("{player}", safePlayer)
                .replace("{command}", safeCommand);
        
        Component message = config.parse(msgRaw);
        
        // Interactive Actions
        if (config.isNotificationActionsEnabled()) {
            List<NotificationAction> actions = config.getNotificationActions();
            for (NotificationAction action : actions) {
                String label = action.getLabel().replace("{player}", safePlayer);
                String hover = action.getHover().replace("{player}", safePlayer);
                // Sanitize username for command execution to prevent injection
                String sanitizedPlayer = offender.getName().replaceAll("[^a-zA-Z0-9_]", "");
                String cmd = action.getCommand().replace("{player}", sanitizedPlayer); 
                
                Component actionComp = MiniMessage.miniMessage().deserialize(label)
                        .hoverEvent(HoverEvent.showText(MiniMessage.miniMessage().deserialize(hover)))
                        .clickEvent(ClickEvent.runCommand(cmd));
                
                message = message.append(actionComp);
            }
        }

        final Component finalMessage = message;
        plugin.getProxy().getPlayers().stream()
                .filter(p -> p.hasPermission(config.getNotifyPermission()))
                .forEach(p -> plugin.adventure().player(p).sendMessage(finalMessage));
    }
}