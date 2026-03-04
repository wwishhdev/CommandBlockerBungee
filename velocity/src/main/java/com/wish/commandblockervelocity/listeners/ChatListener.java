package com.wish.commandblockervelocity.listeners;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
                // Extract base command for custom message lookup
                String baseCmd = fullCommand.replaceAll("^/+", "").split("(?U)\\s+", 2)[0].toLowerCase();
                if (baseCmd.contains(":")) baseCmd = baseCmd.split(":", 2)[1];
                net.kyori.adventure.text.Component customMsg = config.getCustomBlockMessage(baseCmd);
                player.sendMessage(customMsg != null ? customMsg : config.getBlockMessage());
            }

            // Audit log (file)
            if (config.isAuditLogEnabled()) {
                fileLogger.logBlockedCommand(player.getUsername(), player.getUniqueId().toString(), serverName, fullCommand);
            }

            // Audit log (database)
            if (config.isDatabaseEnabled()) {
                plugin.getDatabaseManager().logBlockedCommand(
                        player.getUniqueId().toString(), player.getUsername(), serverName, fullCommand);
            }

            webhookManager.sendWebhook(player.getUsername(), fullCommand, serverName, player.getUniqueId().toString());

            if (config.isNotificationsEnabled()) {
                notifyStaff(player, fullCommand, serverName);
            }
        }
    }

    @Subscribe(order = PostOrder.LATE)
    public void onTabComplete(TabCompleteEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        // Tab-complete whitelist mode: remove all suggestions except allowed ones
        if (config.isTabCompleteWhitelistEnabled()) {
            List<String> allowed = config.getTabCompleteWhitelistAllowed();
            event.getSuggestions().removeIf(suggestion -> {
                String cmd = suggestion.toLowerCase();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                String finalCmd = cmd;
                return allowed.stream().noneMatch(a -> finalCmd.equalsIgnoreCase(a) || finalCmd.startsWith(a.toLowerCase() + " "));
            });
            return;
        }

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
        // Strip zero-width and invisible Unicode characters to prevent bypass
        cleanCommand = cleanCommand.replaceAll("[\\u00AD\\u034F\\u061C\\u070F\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\u206A-\\u206F\\uFEFF\\uFFA0]", "");
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

    /**
     * Converts a wildcard pattern (using * and ?) into a regex pattern.
     */
    private Pattern wildcardToPattern(String wildcard) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append("."); break;
                case '.': case '(': case ')': case '[': case ']':
                case '{': case '}': case '\\': case '^': case '$':
                case '|': case '+': sb.append('\\').append(c); break;
                default: sb.append(c);
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Core matching logic shared between global and server-specific blocked lists.
     * Supports plain commands, "regex:pattern", and "wildcard:pattern" entries.
     */
    private boolean matchesBlockedList(String baseCommand, String cleanCommand, String[] parts, List<String> blockedCommands) {
        for (String blockedCmd : blockedCommands) {
            if (blockedCmd == null) continue;

            // Regex mode: "regex:<pattern>"
            if (blockedCmd.toLowerCase().startsWith("regex:")) {
                String pattern = blockedCmd.substring(6);
                try {
                    if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(baseCommand).matches()) return true;
                } catch (PatternSyntaxException e) {
                    plugin.getLogger().warn("Invalid regex pattern in blocked-commands: '" + pattern + "' - " + e.getMessage());
                }
                continue;
            }

            // Wildcard mode: "wildcard:<pattern>"
            if (blockedCmd.toLowerCase().startsWith("wildcard:")) {
                String pattern = blockedCmd.substring(9).toLowerCase();
                if (wildcardToPattern(pattern).matcher(baseCommand).matches()) return true;
                continue;
            }

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
                    if (blockedCmd.toLowerCase().startsWith("regex:") || blockedCmd.toLowerCase().startsWith("wildcard:")) continue;
                    if (cleanToken.equalsIgnoreCase(blockedCmd)) return true;
                }
            }
        }

        return false;
    }

    private void notifyStaff(Player offender, String command, String serverName) {
        String safePlayer  = config.escape(offender.getUsername());
        String safeCommand = config.escape(command);

        String msg = config.getNotifyMessageRaw()
                .replace("{player}", safePlayer)
                .replace("{command}", safeCommand)
                .replace("{server}", serverName);

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
