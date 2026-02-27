package com.wish.commandblockervelocity.listeners;

import java.util.List;
import java.util.Set;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.managers.ConfigManager;
import com.wish.commandblockervelocity.managers.CooldownManager;
import com.wish.commandblockervelocity.managers.WebhookManager;
import com.wish.commandblockervelocity.utils.FileLogger;
import com.wish.commandblockervelocity.utils.NotificationAction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ChatListener {

    private final CommandBlockerVelocity plugin;
    private final ConfigManager config;
    private final CooldownManager cooldownManager;
    private final WebhookManager webhookManager;
    private final FileLogger fileLogger;

    /**
     * FIX: Deep scan is only valid for commands that chain execution to other commands.
     */
    private static final Set<String> EXECUTION_CHAIN_COMMANDS = Set.of(
            "execute", "sudo", "shell", "run", "cmd", "console"
    );

    public ChatListener(CommandBlockerVelocity plugin, ConfigManager config, CooldownManager cooldownManager,
                        WebhookManager webhookManager, FileLogger fileLogger) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
        this.webhookManager = webhookManager;
        this.fileLogger = fileLogger;
    }

    @Subscribe(order = PostOrder.LATE)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;

        Player player = (Player) event.getCommandSource();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String fullCommand = event.getCommand();
        String serverName  = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        if (isCommandBlocked(fullCommand, serverName)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;

            boolean bypassCooldown = player.hasPermission(config.getBypassCooldownPermission());
            boolean onCooldown = false;

            if (!bypassCooldown) {
                onCooldown = cooldownManager.handleCooldown(player);
            }

            event.setResult(CommandExecuteEvent.CommandResult.denied());

            if (!onCooldown) {
                player.sendMessage(config.getBlockMessage());
            }

            // Audit log
            if (config.isAuditLogEnabled()) {
                fileLogger.logBlockedCommand(player.getUsername(), player.getUniqueId().toString(), serverName, fullCommand);
            }

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

        String[] parts = partialMessage.trim().split("(?U)\\s+", 2);
        String baseCommand = parts[0];

        String serverName = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        if (isCommandBlocked(baseCommand, serverName)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;
            event.getSuggestions().clear();
        }
    }

    private boolean isCommandBlocked(String command, String serverName) {
        if (command == null || command.trim().isEmpty()) return false;

        String cleanCommand = command.trim().toLowerCase();
        cleanCommand = cleanCommand.replaceAll("^/+", "");
        cleanCommand = cleanCommand.replaceAll("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]", "");
        cleanCommand = cleanCommand.trim();
        cleanCommand = cleanCommand.replaceAll("\\s*:\\s*", ":");

        String[] parts = cleanCommand.split("(?U)\\s+", 2);
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

        // 2. Global Blocked Commands
        if (matchesBlockedList(baseCommand, cleanCommand, parts, config.getBlockedCommands())) return true;

        // 3. Server-specific Blocked Commands
        if (matchesBlockedList(baseCommand, cleanCommand, parts, config.getServerBlockedCommands(serverName))) return true;

        return false;
    }

    private boolean matchesBlockedList(String baseCommand, String cleanCommand, String[] parts, List<String> blockedCommands) {
        for (String blockedCmd : blockedCommands) {
            if (blockedCmd == null) continue;
            String blockedLower = blockedCmd.toLowerCase();

            if (baseCommand.equals(blockedLower)) return true;

            if (config.isAliasDetectionEnabled()) {
                if (config.isBlockPluginPrefix() && baseCommand.contains(":")) {
                    String[] cmdParts = baseCommand.split(":", 2);
                    if (cmdParts.length > 1 && cmdParts[1].equals(blockedLower)) return true;
                }
                if (config.isBlockHelpSubcommand()) {
                    if (cleanCommand.equals(blockedLower + " help") || cleanCommand.startsWith(blockedLower + " help ")) return true;
                }
            }
        }

        // FIX: Deep scan only for execution-chain base commands
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

    private void notifyStaff(Player offender, String command) {
        String safePlayer  = config.escape(offender.getUsername());
        String safeCommand = config.escape(command);

        String msg = config.getNotifyMessageRaw()
                .replace("{player}", safePlayer)
                .replace("{command}", safeCommand);

        Component message = config.color(msg);

        if (config.isNotificationActionsEnabled()) {
            List<NotificationAction> actions = config.getNotificationActions();
            for (NotificationAction action : actions) {
                String label = action.getLabel().replace("{player}", safePlayer);
                String hover = action.getHover().replace("{player}", safePlayer);
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
