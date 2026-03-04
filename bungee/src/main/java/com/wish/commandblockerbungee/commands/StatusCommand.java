package com.wish.commandblockerbungee.commands;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.managers.ConfigManager;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class StatusCommand extends Command {

    private final CommandBlockerBungee plugin;

    public StatusCommand(CommandBlockerBungee plugin) {
        super("cbstatus", "commandblocker.admin", "cbinfo");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (!sender.hasPermission("commandblocker.admin")) {
            if (sender instanceof ProxiedPlayer) {
                plugin.adventure().player((ProxiedPlayer) sender).sendMessage(config.getNoPermissionMessage());
            } else {
                plugin.adventure().sender(sender).sendMessage(config.getNoPermissionMessage());
            }
            return;
        }

        int blockedCount = config.getBlockedCommands().size();
        int serverSpecific = config.getAllServerBlockedCommands().values().stream().mapToInt(java.util.List::size).sum();
        boolean dbEnabled = config.isDatabaseEnabled();
        String dbType = config.getDatabaseType();
        boolean webhookEnabled = config.isWebhookEnabled();
        boolean cooldownEnabled = config.isCooldownEnabled();
        boolean auditLogEnabled = config.isAuditLogEnabled();
        boolean autoPunishments = config.isAutoPunishmentsEnabled();
        boolean tabWhitelist = config.isTabCompleteWhitelistEnabled();
        boolean customMessages = config.isCustomMessagesEnabled();
        int onlinePlayers = plugin.getProxy().getOnlineCount();

        String status = "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "<gold><bold>CommandBlocker</bold></gold> <gray>v2.3.0 Status\n" +
                "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "<yellow>Blocked Commands: <white>" + blockedCount + " global" + (serverSpecific > 0 ? " + " + serverSpecific + " server-specific" : "") + "\n" +
                "<yellow>Cooldown: " + (cooldownEnabled ? "<green>Enabled" : "<red>Disabled") + " <gray>(" + config.getMaxAttempts() + " max attempts)\n" +
                "<yellow>Database: " + (dbEnabled ? "<green>Enabled <gray>(" + dbType + ")" : "<red>Disabled") + "\n" +
                "<yellow>Discord Webhook: " + (webhookEnabled ? "<green>Enabled" : "<red>Disabled") + "\n" +
                "<yellow>Audit Log: " + (auditLogEnabled ? "<green>Enabled" : "<red>Disabled") + "\n" +
                "<yellow>Auto-Punishments: " + (autoPunishments ? "<green>Enabled <gray>(" + config.getAutoPunishments().size() + " actions)" : "<red>Disabled") + "\n" +
                "<yellow>Custom Messages: " + (customMessages ? "<green>Enabled" : "<red>Disabled") + "\n" +
                "<yellow>Tab Whitelist: " + (tabWhitelist ? "<green>Enabled" : "<red>Disabled") + "\n" +
                "<yellow>Online Players: <white>" + onlinePlayers + "\n" +
                "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        if (sender instanceof ProxiedPlayer) {
            plugin.adventure().player((ProxiedPlayer) sender).sendMessage(config.parse(status));
        } else {
            plugin.adventure().sender(sender).sendMessage(config.parse(status));
        }
    }
}
