package com.wish.commandblockervelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.wish.commandblockervelocity.CommandBlockerVelocity;
import com.wish.commandblockervelocity.managers.ConfigManager;

public class ReloadCommand implements SimpleCommand {

    private final CommandBlockerVelocity plugin;

    public ReloadCommand(CommandBlockerVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        ConfigManager config = plugin.getConfigManager();

        if (source.hasPermission("commandblocker.reload")) {
            try {
                config.loadConfiguration();
                plugin.getDatabaseManager().reload();
                source.sendMessage(config.getReloadSuccessMessage());
                
                String playerName = (source instanceof Player) ? ((Player) source).getUsername() : "Console";
                if (source instanceof Player) { // Only log to console if player reloaded, otherwise it's duplicate
                     plugin.getLogger().info(config.getConsoleReloadMessageRaw().replace("{player}", playerName));
                }
            } catch (Exception e) {
                source.sendMessage(config.color(config.getReloadErrorMessageRaw().replace("{error}", e.getMessage())));
                plugin.getLogger().error("Error reloading configuration: " + e.getMessage());
            }
        } else {
            source.sendMessage(config.getNoPermissionMessage());
        }
    }
    
    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("commandblocker.reload");
    }
}
