package com.wish.commandblockervelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.wish.commandblockervelocity.managers.CooldownManager;

public class ConnectionListener {

    private final CooldownManager cooldownManager;

    public ConnectionListener(CooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        cooldownManager.removeCooldown(event.getPlayer().getUniqueId());
    }
}
