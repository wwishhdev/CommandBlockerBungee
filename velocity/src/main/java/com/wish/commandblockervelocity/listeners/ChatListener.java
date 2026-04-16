package com.wish.commandblockervelocity.listeners;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.managers.ConfigManager;
import com.wish.commandblockervelocity.managers.CooldownManager;
import com.wish.commandblockervelocity.managers.WebhookManager;
import com.wish.commandblockervelocity.utils.CommandMatcher;
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
    private final CommandMatcher commandMatcher;

    public ChatListener(CommandBlockerVelocity plugin, ConfigManager config, CooldownManager cooldownManager,
                        WebhookManager webhookManager, FileLogger fileLogger) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
        this.webhookManager = webhookManager;
        this.fileLogger = fileLogger;
        this.commandMatcher = new CommandMatcher(new CommandMatcher.Rules() {
            @Override
            public boolean isAllowedCommandsEnabled() {
                return config.isAllowedCommandsEnabled();
            }

            @Override
            public List<String> getAllowedCommands() {
                return config.getAllowedCommands();
            }

            @Override
            public List<String> getBlockedCommands() {
                return config.getBlockedCommands();
            }

            @Override
            public List<String> getServerBlockedCommands(String serverName) {
                return config.getServerBlockedCommands(serverName);
            }

            @Override
            public boolean isAliasDetectionEnabled() {
                return config.isAliasDetectionEnabled();
            }

            @Override
            public boolean isBlockPluginPrefix() {
                return config.isBlockPluginPrefix();
            }

            @Override
            public boolean isBlockHelpSubcommand() {
                return config.isBlockHelpSubcommand();
            }

            @Override
            public void warn(String message) {
                plugin.getLogger().warn(message);
            }
        });
    }

    @Subscribe(order = PostOrder.LATE)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;

        Player player = (Player) event.getCommandSource();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String fullCommand = event.getCommand();
        String serverName = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        if (commandMatcher.isCommandBlocked(fullCommand, serverName)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;

            boolean bypassCooldown = player.hasPermission(config.getBypassCooldownPermission());
            boolean onCooldown = false;

            if (!bypassCooldown) {
                onCooldown = cooldownManager.handleCooldown(player);
            }

            event.setResult(CommandExecuteEvent.CommandResult.denied());

            if (!onCooldown) {
                String baseCmd = commandMatcher.getBaseCommandForMessage(fullCommand);
                Component customMsg = config.getCustomBlockMessage(baseCmd);
                player.sendMessage(customMsg != null ? customMsg : config.getBlockMessage());
            }

            if (config.isAuditLogEnabled()) {
                fileLogger.logBlockedCommand(player.getUsername(), player.getUniqueId().toString(), serverName, fullCommand);
            }

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

        if (config.isTabCompleteWhitelistEnabled()) {
            List<String> allowed = config.getTabCompleteWhitelistAllowed();
            event.getSuggestions().removeIf(suggestion -> {
                String cmd = suggestion.toLowerCase(Locale.ROOT);
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                String finalCmd = cmd;
                return allowed.stream()
                        .filter(Objects::nonNull)
                        .noneMatch(a -> finalCmd.equalsIgnoreCase(a) || finalCmd.startsWith(a.toLowerCase(Locale.ROOT) + " "));
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

        if (commandMatcher.isCommandBlocked(baseCommand, serverName)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;
            event.getSuggestions().clear();
        }
    }

    private void notifyStaff(Player offender, String command, String serverName) {
        String safePlayer = config.escape(offender.getUsername());
        String safeCommand = config.escape(command);
        String safeServer = config.escape(serverName);

        String msg = config.getNotifyMessageRaw()
                .replace("{player}", safePlayer)
                .replace("{command}", safeCommand)
                .replace("{server}", safeServer);

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
