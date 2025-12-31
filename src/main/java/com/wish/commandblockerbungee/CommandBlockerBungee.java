package com.wish.commandblockerbungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.plugin.Listener;
import org.bstats.bungeecord.Metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CommandBlockerBungee extends Plugin implements Listener {
    private ConfigManager configManager;
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        // Initialize bStats
        int pluginId = 24030;
        new Metrics(this, pluginId);

        this.configManager = new ConfigManager(this);
        this.configManager.loadConfiguration();

        this.cooldownManager = new CooldownManager(this);

        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand(this));

        getProxy().getConsole().sendMessage(ChatColor.GOLD + "\n" +
                " ██████╗ ██████╗ ███╗   ███╗███╗   ███╗ █████╗ ███╗   ██╗██████╗ ██████╗ ██╗      ██████╗  ██████╗██╗  ██╗███████╗██████╗ \n" +
                "██╔════╝██╔═══██╗████╗ ████║████╗ ████║██╔══██╗████╗  ██║██╔══██╗██╔══██╗██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝██╔══██╗\n" +
                "██║     ██║   ██║██╔████╔██║██╔████╔██║███████║██╔██╗ ██║██║  ██║██████╔╝██║     ██║   ██║██║     █████╔╝ █████╗  ██████╔╝\n" +
                "██║     ██║   ██║██║╚██╔╝██║██║╚██╔╝██║██╔══██║██║╚██╗██║██║  ██║██╔══██╗██║     ██║   ██║██║     ██╔═██╗ ██╔══╝  ██╔══██╗\n" +
                "╚██████╗╚██████╔╝██║ ╚═╝ ██║██║ ╚═╝ ██║██║  ██║██║ ╚████║██████╔╝██████╔╝███████╗╚██████╔╝╚██████╗██║  ██╗███████╗██║  ██║\n" +
                " ╚═════╝ ╚═════╝ ╚═╝     ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═════╝ ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝\n" +
                ChatColor.YELLOW + "                Thanks for using CommandBlockerBungee! " + ChatColor.RED + "❤\n" +
                ChatColor.AQUA + "                                                          by wwishh\n" +
                ChatColor.GREEN + "        If you like the plugin, please leave a review at: " +
                ChatColor.GOLD + "https://www.spigotmc.org/resources/❌-commandblockerbungee-1-8-1-21.120890/\n");

        getLogger().info("CommandBlockerBungee has been enabled! by wwishh <3");
    }

    public void reloadConfig() {
        configManager.loadConfiguration();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public void notifyStaff(String message) {
        if (!configManager.getBoolean("notifications.enabled", true) || message == null) return;

        String notifyPermission = configManager.getString("notifications.permission", "commandblocker.notify");

        try {
            for (ProxiedPlayer player : getProxy().getPlayers()) {
                if (player != null && player.isConnected() && player.hasPermission(notifyPermission)) {
                    player.sendMessage(new TextComponent(message));
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error sending staff notification: " + e.getMessage());
        }
    }

    private boolean isCommandBlocked(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        try {
            // Remove initial slash
            String cleanCommand = command.toLowerCase();
            if (cleanCommand.startsWith("/")) {
                cleanCommand = cleanCommand.substring(1);
            }

            // Get base command (no args)
            String[] parts = cleanCommand.split(" ", 2);
            String baseCommand = parts[0];

            if (baseCommand.isEmpty()) {
                return false;
            }

            // Check allowed commands
            if (configManager.getBoolean("allowed-commands-settings.enabled", true)) {
                List<String> allowedCommands = configManager.getStringList("allowed-commands-settings.commands");
                for (String allowedCmd : allowedCommands) {
                    if (allowedCmd == null) continue;

                    allowedCmd = allowedCmd.toLowerCase();
                    // Exact match
                    if (baseCommand.equals(allowedCmd)) {
                        return false;
                    }

                    // Starts with
                    if (cleanCommand.startsWith(allowedCmd + " ")) {
                        return false;
                    }
                }
            }

            // Check regex blocked commands (using pre-compiled patterns)
            List<Pattern> blockedPatterns = configManager.getBlockedRegexPatterns();
            if (blockedPatterns != null && !blockedPatterns.isEmpty()) {
                for (Pattern pattern : blockedPatterns) {
                    if (pattern.matcher(command).matches() || pattern.matcher(cleanCommand).matches()) {
                        return true;
                    }
                }
            }

            if (!configManager.getBoolean("alias-detection.enabled", true)) {
                return configManager.getStringList("blocked-commands").contains(baseCommand);
            }

            List<String> blockedCommands = configManager.getStringList("blocked-commands");
            boolean blockPluginPrefix = configManager.getBoolean("alias-detection.block-plugin-prefix", true);

            for (String blockedCmd : blockedCommands) {
                if (blockedCmd == null) continue;

                blockedCmd = blockedCmd.toLowerCase();

                // Exact match
                if (baseCommand.equals(blockedCmd)) {
                    return true;
                }

                // Plugin prefix check
                if (blockPluginPrefix && baseCommand.startsWith(blockedCmd + ":")) {
                    return true;
                }

                // Alias check
                if (isAlias(baseCommand, blockedCmd)) {
                    return true;
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error processing command for blocking: " + e.getMessage());
        }

        return false;
    }

    private boolean isAlias(String command, String blockedCmd) {
        List<String> patterns = new ArrayList<>();
        patterns.add(blockedCmd);

        boolean blockShortVersion = configManager.getBoolean("alias-detection.block-short-version", true);
        if (blockShortVersion && blockedCmd.length() > 3) {
            patterns.add(blockedCmd.substring(0, 3));
        }

        boolean blockHelpSubcommand = configManager.getBoolean("alias-detection.block-help-subcommand", true);
        for (String pattern : patterns) {
            if (command.equals(pattern)) {
                return true;
            }

            if (blockHelpSubcommand && (
                    command.equals(pattern + "help") ||
                            command.startsWith(pattern + " help"))) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (!event.isCommand()) return;

        String fullCommand = event.getMessage();

        if (event.getSender() instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) event.getSender();

            String bypassPermission = configManager.getString("bypass-permission", "commandblocker.bypass");
            if (player.hasPermission(bypassPermission)) {
                return;
            }

            if (isCommandBlocked(fullCommand)) {
                if (cooldownManager.handleCooldown(player, fullCommand)) {
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);
                String blockMessage = ChatColor.translateAlternateColorCodes('&',
                    configManager.getString("messages.block-message", "&cThis command is blocked."));
                player.sendMessage(new TextComponent(blockMessage));

                if (configManager.getBoolean("notifications.enabled", true)) {
                    String notifyMessage = ChatColor.translateAlternateColorCodes('&',
                        configManager.getString("notifications.command-message", "&e{player} &7tried to use blocked command: &c{command}")
                            .replace("{player}", player.getName())
                            .replace("{command}", fullCommand));
                    notifyStaff(notifyMessage);
                }
            }
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer)) {
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();

        String bypassPermission = configManager.getString("bypass-permission", "commandblocker.bypass");
        if (player.hasPermission(bypassPermission)) {
            return;
        }

        String cursor = event.getCursor().toLowerCase();

        if (isCommandBlocked(cursor)) {
            event.setCancelled(true);
            event.getSuggestions().clear();
        }
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) {
            cooldownManager.cleanup();
        }
        getLogger().info("CommandBlockerBungee has been disabled! by wwishh <3");
    }
}
