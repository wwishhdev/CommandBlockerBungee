package com.wish.commandblockerbungee.listeners;

import java.util.List;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.managers.ConfigManager;
import com.wish.commandblockerbungee.managers.CooldownManager;
import com.wish.commandblockerbungee.managers.WebhookManager;
import com.wish.commandblockerbungee.utils.CommandMatcher;
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
    private final CommandMatcher commandMatcher;

    public ChatListener(CommandBlockerBungee plugin, ConfigManager config, CooldownManager cooldownManager,
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
                plugin.getLogger().warning(message);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (!event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String fullCommand = event.getMessage();
        String serverName = player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";

        if (commandMatcher.isCommandBlocked(fullCommand, serverName)) {
            if (player.hasPermission(config.getBypassBlockPermission())) return;

            boolean bypassCooldown = player.hasPermission(config.getBypassCooldownPermission());
            boolean onCooldown = false;

            if (!bypassCooldown) {
                onCooldown = cooldownManager.handleCooldown(player);
            }

            event.setCancelled(true);

            if (!onCooldown) {
                String baseCmd = commandMatcher.getBaseCommandForMessage(fullCommand);
                Component customMsg = config.getCustomBlockMessage(baseCmd);
                plugin.adventure().player(player).sendMessage(customMsg != null ? customMsg : config.getBlockMessage());
            }

            if (config.isAuditLogEnabled()) {
                fileLogger.logBlockedCommand(player.getName(), player.getUniqueId().toString(), serverName, fullCommand);
            }

            if (config.isDatabaseEnabled()) {
                plugin.getDatabaseManager().logBlockedCommand(
                        player.getUniqueId().toString(), player.getName(), serverName, fullCommand);
            }

            webhookManager.sendWebhook(player.getName(), fullCommand, serverName, player.getUniqueId().toString());

            if (config.isNotificationsEnabled()) {
                notifyStaff(player, fullCommand, serverName);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (player.hasPermission(config.getBypassAllPermission())) return;

        String serverName = player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";
        boolean bypassBlock = player.hasPermission(config.getBypassBlockPermission());
        boolean whitelistEnabled = config.isTabCompleteWhitelistEnabled();
        List<String> whitelistAllowed = config.getTabCompleteWhitelistAllowed();

        event.getSuggestions().removeIf(suggestion ->
                commandMatcher.shouldHideTabSuggestion(suggestion, serverName, whitelistEnabled, whitelistAllowed)
                        && !(bypassBlock && !whitelistEnabled));

        if (!whitelistEnabled && !bypassBlock
                && commandMatcher.isCommandBlocked(event.getCursor(), serverName)) {
            event.setCancelled(true);
            event.getSuggestions().clear();
        }
    }

    private void notifyStaff(ProxiedPlayer offender, String command, String serverName) {
        String safePlayer = config.escape(offender.getName());
        String safeCommand = config.escape(command);
        String safeServer = config.escape(serverName);

        String msgRaw = config.getNotifyMessageRaw()
                .replace("{player}", safePlayer)
                .replace("{command}", safeCommand)
                .replace("{server}", safeServer);

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
