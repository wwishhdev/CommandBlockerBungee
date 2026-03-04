package com.wish.commandblockervelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.managers.ConfigManager;

public class StatusCommand implements SimpleCommand {

    private final CommandBlockerVelocity plugin;

    public StatusCommand(CommandBlockerVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        ConfigManager config = plugin.getConfigManager();

        if (!source.hasPermission("commandblocker.admin")) {
            source.sendMessage(config.getNoPermissionMessage());
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
        int onlinePlayers = plugin.getProxy().getPlayerCount();

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

        source.sendMessage(config.parse(status));
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("commandblocker.admin");
    }
}
