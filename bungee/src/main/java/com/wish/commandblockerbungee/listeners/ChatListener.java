package com.wish.commandblockerbungee.listeners;

import java.util.List;
import java.util.Set;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.managers.ConfigManager;
import com.wish.commandblockerbungee.managers.CooldownManager;
import com.wish.commandblockerbungee.managers.WebhookManager;
import com.wish.commandblockerbungee.utils.FileLogger;
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

public class ChatListener implements Listener {

    private final CommandBlockerBungee plugin;
    private final ConfigManager config;
    private final CooldownManager cooldownManager;
    private final WebhookManager webhookManager;
    private final FileLogger fileLogger;

    /**
     * FIX: Commands that legitimately accept sub-commands to execute other commands.
     * The deep-scan (argument scanning) is only meaningful for these "execution chain" commands.
     * Applying it globally caused false positives (e.g. /tell player op being blocked).
     */
    private static final Set<String> EXECUTION_CHAIN_COMMANDS = Set.of(
            "execute", "sudo", "shell", "run", "cmd", "console"
    );

    public ChatListener(CommandBlockerBungee plugin, ConfigManager config, CooldownManager cooldownManager,
                        WebhookManager webhookManager, FileLogger fileLogger) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
        this.webhookManager = webhookManager;
        this.fileLogger = fileLogger;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (!event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();

        // Granular Permissions
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String fullCommand = event.getMessage();
        String serverName  = player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";

        if (isCommandBlocked(fullCommand, serverName)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;

            boolean bypassCooldown = player.hasPermission(config.getBypassCooldownPermission());
            boolean onCooldown = false;

            if (!bypassCooldown) {
                onCooldown = cooldownManager.handleCooldown(player);
            }

            event.setCancelled(true);

            if (!onCooldown) {
                plugin.adventure().player(player).sendMessage(config.getBlockMessage());
            }

            // Audit log
            if (config.isAuditLogEnabled()) {
                fileLogger.logBlockedCommand(player.getName(), player.getUniqueId().toString(), serverName, fullCommand);
            }

            webhookManager.sendWebhook(player.getName(), fullCommand);

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

        String[] parts = cursor.trim().split("(?U)\\s+", 2);
        String base = parts[0];

        String serverName = player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";

        if (isCommandBlocked(base, serverName)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;
            event.setCancelled(true);
            event.getSuggestions().clear();
        }
    }

    private boolean isCommandBlocked(String command, String serverName) {
        if (command == null || command.trim().isEmpty()) return false;

        String cleanCommand = command.trim().toLowerCase();

        // Strip ALL leading slashes to prevent "//op" bypass
        cleanCommand = cleanCommand.replaceAll("^/+", "");

        // Strip zero-width Unicode characters to prevent invisible char bypass
        cleanCommand = cleanCommand.replaceAll("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]", "");

        cleanCommand = cleanCommand.trim();

        // Normalize spacing around colons to prevent "/minecraft : op" bypass
        cleanCommand = cleanCommand.replaceAll("\\s*:\\s*", ":");

        // Use Unicode-aware regex to catch non-breaking spaces
        String[] parts = cleanCommand.split("(?U)\\s+", 2);
        if (parts.length == 0) return false;
        String baseCommand = parts[0];
        if (baseCommand.isEmpty()) return false;

        // 1. Allowed Commands Check (takes priority over all blocked lists)
        if (config.isAllowedCommandsEnabled()) {
            for (String allowed : config.getAllowedCommands()) {
                if (allowed == null) continue;
                String allowedLower = allowed.toLowerCase();
                if (baseCommand.equals(allowedLower) || cleanCommand.startsWith(allowedLower + " ")) return false;
            }
        }

        // 2. Global Blocked Commands Check
        List<String> blockedCommands = config.getBlockedCommands();
        if (matchesBlockedList(baseCommand, cleanCommand, parts, blockedCommands)) return true;

        // 3. Server-specific Blocked Commands Check
        if (matchesBlockedList(baseCommand, cleanCommand, parts, config.getServerBlockedCommands(serverName))) return true;

        return false;
    }

    /**
     * Core matching logic shared between global and server-specific blocked lists.
     */
    private boolean matchesBlockedList(String baseCommand, String cleanCommand, String[] parts, List<String> blockedCommands) {
        for (String blockedCmd : blockedCommands) {
            if (blockedCmd == null) continue;
            String blockedLower = blockedCmd.toLowerCase();

            // Exact match
            if (baseCommand.equals(blockedLower)) return true;

            if (config.isAliasDetectionEnabled()) {
                // Plugin prefix: "minecraft:op" → blocked
                if (config.isBlockPluginPrefix() && baseCommand.contains(":")) {
                    String[] cmdParts = baseCommand.split(":", 2);
                    if (cmdParts.length > 1 && cmdParts[1].equals(blockedLower)) return true;
                }

                // Help subcommand: "op help" → blocked
                if (config.isBlockHelpSubcommand()) {
                    if (cleanCommand.equals(blockedLower + " help") || cleanCommand.startsWith(blockedLower + " help ")) return true;
                }
            }
        }

        // FIX: Deep scan — only scan arguments when the BASE command is a known
        // execution-chain command (e.g. /execute, /sudo). This prevents false positives
        // like /tell player op being caught because "op" appears as an argument.
        if (parts.length > 1 && EXECUTION_CHAIN_COMMANDS.contains(baseCommand)) {
            String[] allTokens = parts[1].split("(?U)\\s+");
            for (String token : allTokens) {
                String cleanToken = token.contains(":") ? token.split(":", 2)[1] : token;
                for (String blockedCmd : blockedCommands) {
                    if (blockedCmd == null) continue;
                    if (cleanToken.equalsIgnoreCase(blockedCmd)) return true;
                }
            }
        }

        return false;
    }

    private void notifyStaff(ProxiedPlayer offender, String command) {
        String safePlayer  = config.escape(offender.getName());
        String safeCommand = config.escape(command);

        String msgRaw = config.getNotifyMessageRaw()
                .replace("{player}", safePlayer)
                .replace("{command}", safeCommand);

        Component message = config.parse(msgRaw);

        if (config.isNotificationActionsEnabled()) {
            List<NotificationAction> actions = config.getNotificationActions();
            for (NotificationAction action : actions) {
                String label = action.getLabel().replace("{player}", safePlayer);
                String hover = action.getHover().replace("{player}", safePlayer);
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