package com.wilder0p.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
            manager.applyRunSurvival(e.getPlayer());
            manager.startScoreboard(e.getPlayer(), run);
            if (run.isHorror()) manager.startHorror(e.getPlayer());
            return;
        }
        manager.scheduleJoinHint(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.handleQuit(e.getPlayer());
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        manager.handlePortal(e);
    }

    @EventHandler
    public void onBed(PlayerBedEnterEvent e) {
        if (!manager.isHorrorRun(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage("§8You don't sleep here.");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (manager.isInRun(e.getPlayer().getUniqueId())) {
            e.getPlayer().clearActivePotionEffects();
            manager.applyRunSurvival(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!manager.isInRun(p.getUniqueId())) return;
        if (e.getAction() == EntityPotionEffectEvent.Action.REMOVED) return;
        if (e.getCause() != EntityPotionEffectEvent.Cause.PLUGIN
                && e.getCause() != EntityPotionEffectEvent.Cause.COMMAND) {
            return;
        }
        var effect = e.getNewEffect();
        if (effect == null) return;
        // Hidden Resistance/Regen at amplifier -1/255 is ExtraFlags godmode
        if (!effect.hasParticles() && effect.getAmplifier() != 0) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        if (!manager.isInRun(e.getPlayer().getUniqueId())) return;
        if (manager.isModeChangeAllowed(e.getPlayer().getUniqueId())) return;
        if (e.getNewGameMode() == GameMode.SURVIVAL) return;
        e.setCancelled(true);
    }
}
