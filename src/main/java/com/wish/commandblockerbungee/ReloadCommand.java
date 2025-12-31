package com.wish.commandblockerbungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

public class ReloadCommand extends Command {
    private final CommandBlockerBungee plugin;

    public ReloadCommand(CommandBlockerBungee plugin) {
        super("cblockerreload", "commandblocker.reload", "cbreload");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (sender == null) return;

        if (sender.hasPermission("commandblocker.reload")) {
            try {
                plugin.reloadConfig();

                String successMsg = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfigManager().getString("messages.reload-success", "&aCommandBlocker has been reloaded successfully!"));
                sender.sendMessage(new TextComponent(successMsg));

                String consoleMsg = plugin.getConfigManager().getString("messages.console-reload", "Configuration reloaded by {player}")
                        .replace("{player}", sender.getName());
                plugin.getLogger().info(consoleMsg);
            } catch (Exception e) {
                String errorMsg = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfigManager().getString("messages.reload-error", "&cError reloading configuration: {error}")
                        .replace("{error}", e.getMessage()));
                sender.sendMessage(new TextComponent(errorMsg));
                plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            String noPermMsg = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getString("messages.no-permission", "&cYou don't have permission to use this command."));
            sender.sendMessage(new TextComponent(noPermMsg));
        }
    }
}
