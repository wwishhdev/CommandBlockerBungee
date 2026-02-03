package com.wish.commandblockerbungee.commands;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.managers.ConfigManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class ReloadCommand extends Command {

    private final CommandBlockerBungee plugin;

    public ReloadCommand(CommandBlockerBungee plugin) {
        super("cblockerreload", "commandblocker.reload", "cbreload");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (sender.hasPermission("commandblocker.reload")) {
            try {
                config.loadConfiguration();
                
                if (sender instanceof ProxiedPlayer) {
                    plugin.adventure().player((ProxiedPlayer) sender).sendMessage(config.getReloadSuccessMessage());
                } else {
                    sender.sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                        config.getReloadSuccessMessage().toString() // Basic fallback for console if needed or use adventure console
                    ));
                    // Actually, Adventure supports console too
                    plugin.adventure().sender(sender).sendMessage(config.getReloadSuccessMessage());
                }
                
                plugin.getLogger().info(config.getConsoleReloadMessage().replace("{player}", sender.getName()));
            } catch (Exception e) {
                 if (sender instanceof ProxiedPlayer) {
                    plugin.adventure().player((ProxiedPlayer) sender).sendMessage(config.parse(config.getReloadErrorMessageRaw().replace("{error}", e.getMessage())));
                } else {
                    plugin.adventure().sender(sender).sendMessage(config.parse(config.getReloadErrorMessageRaw().replace("{error}", e.getMessage())));
                }
                plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
            }
        } else {
            if (sender instanceof ProxiedPlayer) {
                plugin.adventure().player((ProxiedPlayer) sender).sendMessage(config.getNoPermissionMessage());
            } else {
                 plugin.adventure().sender(sender).sendMessage(config.getNoPermissionMessage());
            }
        }
    }
}