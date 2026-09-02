package com.wilder0p.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;

public class EventListener implements Listener {
    private final SpeedrunManager manager;

    public EventListener(SpeedrunManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof EnderDragon)) return;
        for (SpeedrunInstance run : manager.getActiveRuns().values()) {
            if (run.getTheEnd().equals(e.getEntity().getWorld())) {
                Player killer = Bukkit.getPlayer(run.getPlayerUUID());
                if (killer != null) manager.finish(killer);
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        SpeedrunInstance run = manager.getActiveRuns().get(e.getPlayer().getUniqueId());
        if (run != null) {
            e.getPlayer().teleport(run.getOverworld().getSpawnLocation());
            manager.startScoreboard(e.getPlayer(), run);
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        manager.handlePortal(e);
    }
}
