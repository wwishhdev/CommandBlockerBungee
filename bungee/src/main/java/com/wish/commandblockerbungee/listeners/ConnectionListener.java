package com.wish.commandblockerbungee.listeners;

import com.wish.commandblockerbungee.CommandBlockerBungee;
import com.wish.commandblockerbungee.managers.CooldownManager;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class ConnectionListener implements Listener {

    private final CooldownManager cooldownManager;

    public ConnectionListener(CooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        cooldownManager.removeCooldown(event.getPlayer().getUniqueId());
    }
}
