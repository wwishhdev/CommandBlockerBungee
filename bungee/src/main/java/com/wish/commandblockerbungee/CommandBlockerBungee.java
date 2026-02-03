package com.wish.commandblockerbungee;

import com.wish.commandblockerbungee.commands.ReloadCommand;
import com.wish.commandblockerbungee.listeners.ChatListener;
import com.wish.commandblockerbungee.managers.ConfigManager;
import com.wish.commandblockerbungee.managers.CooldownManager;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;

public class CommandBlockerBungee extends Plugin {

    private ConfigManager configManager;
    private CooldownManager cooldownManager;
    private BungeeAudiences adventure;

    @Override
    public void onEnable() {
        // Initialize Adventure
        this.adventure = BungeeAudiences.create(this);

        // ASCII Art
        getProxy().getConsole().sendMessage(new net.md_5.bungee.api.chat.TextComponent(
                ChatColor.GOLD + "\n" +
                " ██████╗ ██████╗ ███╗   ███╗███╗   ███╗ █████╗ ███╗   ██╗██████╗ ██████╗ ██╗      ██████╗  ██████╗██╗  ██╗███████╗██████╗ \n" +
                "██╔════╝██╔═══██╗████╗ ████║████╗ ████║██╔══██╗████╗  ██║██╔══██╗██╔══██╗██║     ██╔═══██╗██╔════╝██║ ██╔╝██╔════╝██╔══██╗\n" +
                "██║     ██║   ██║██╔████╔██║██╔████╔██║███████║██╔██╗ ██║██║  ██║██████╔╝██║     ██║   ██║██║     █████╔╝ █████╗  ██████╔╝\n" +
                "██║     ██║   ██║██║╚██╔╝██║██║╚██╔╝██║██╔══██║██║╚██╗██║██║  ██║██╔══██╗██║     ██║   ██║██║     ██╔═██╗ ██╔══╝  ██╔══██╗\n" +
                "╚██████╗╚██████╔╝██║ ╚═╝ ██║██║ ╚═╝ ██║██║  ██║██║ ╚████║██████╔╝██████╔╝███████╗╚██████╔╝╚██████╗██║  ██╗███████╗██║  ██║\n" +
                " ╚═════╝ ╚═════╝ ╚═╝     ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═════╝ ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝\n" +
                ChatColor.YELLOW + "                CommandBlockerBungee v2.0.0 " + ChatColor.RED + "❤\n" +
                ChatColor.AQUA + "                                                          by wwishhdev\n"
        ));

        // Managers
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfiguration();

        this.cooldownManager = new CooldownManager(this, configManager);

        // Listeners & Commands
        getProxy().getPluginManager().registerListener(this, new ChatListener(this, configManager, cooldownManager));
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand(this));

        // bStats
        new Metrics(this, 24030);

        getLogger().info("CommandBlockerBungee has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        if (cooldownManager != null) {
            cooldownManager.clear();
        }
        getLogger().info("CommandBlockerBungee has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public BungeeAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Cannot retrieve audience provider while plugin is not enabled");
        }
        return this.adventure;
    }
}