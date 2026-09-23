package com.wilder0p.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-player horror events. Only the runner hears/sees these.
 * Does not edit blocks, teleport, or touch lobby players.
 */
public class HorrorHauntTask extends BukkitRunnable {
    private static final Sound[] WHISPERS = {
            Sound.AMBIENT_CAVE,
            Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD,
            Sound.ENTITY_WARDEN_HEARTBEAT,
            Sound.ENTITY_WARDEN_NEARBY_CLOSER,
            Sound.ENTITY_ENDERMAN_STARE,
            Sound.ENTITY_GHAST_HURT,
            Sound.ENTITY_PHANTOM_AMBIENT,
            Sound.ENTITY_VEX_AMBIENT,
            Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
            Sound.ENTITY_WITHER_AMBIENT
    };
    private static final EntityType[] STALKERS = {
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY,
            EntityType.SPIDER, EntityType.CREEPER, EntityType.ENDERMAN, EntityType.PHANTOM
    };

    private final SpeedrunManager manager;
    private final UUIDHolder holder;
    private final List<Entity> temps = new ArrayList<>();

    public HorrorHauntTask(SpeedrunManager manager, Player player) {
        this.manager = manager;
        this.holder = new UUIDHolder(player.getUniqueId());
    }

    @Override
    public void run() {
        Player player = Bukkit.getPlayer(holder.uuid);
        if (player == null || !player.isOnline()) {
            cleanup();
            cancel();
            return;
        }
        SpeedrunInstance run = manager.getActiveRuns().get(holder.uuid);
        if (run == null || !run.isHorror()) {
            cleanup();
            cancel();
            return;
        }
        if (!manager.isSpeedrunWorld(player.getWorld())) return;

        cullTemps();
        manager.forceMobSpawning(player.getWorld());
        keepNight(player.getWorld());

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int roll = rng.nextInt(100);
        if (roll < 28) whisper(player, rng);
        else if (roll < 42) footsteps(player, rng);
        else if (roll < 52) thunder(player);
        else if (roll < 62) darkness(player);
        else if (roll < 74) fakeCreeper(player, rng);
        else if (roll < 84) stalkerMob(player, rng);
        else if (roll < 93) ownHeadStalker(player, rng);
        else souls(player, rng);

        if (rng.nextInt(8) == 0) lookDespawn(player);
    }

    public void cleanup() {
        for (Entity e : temps) {
            if (e != null && e.isValid()) e.remove();
        }
        temps.clear();
    }

    private void keepNight(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        if (world.getTime() < 14000 || world.getTime() > 23000) {
            world.setTime(18000);
        }
    }

    private void whisper(Player player, ThreadLocalRandom rng) {
        Sound sound = WHISPERS[rng.nextInt(WHISPERS.length)];
        Location at = offset(player.getLocation(), 6, 18, rng);
        player.playSound(at, sound, 0.9f, rng.nextBoolean() ? 0.6f : 1.4f);
    }

    private void footsteps(Player player, ThreadLocalRandom rng) {
        Location behind = player.getLocation().clone();
        Vector back = player.getLocation().getDirection().multiply(-1).setY(0);
        if (back.lengthSquared() < 0.01) back = new Vector(1, 0, 0);
        behind.add(back.normalize().multiply(3 + rng.nextDouble() * 4));
        player.playSound(behind, Sound.BLOCK_STONE_STEP, 1.0f, 0.8f);
        player.getWorld().spawnParticle(Particle.SMOKE, behind.add(0, 0.2, 0), 4, 0.2, 0.1, 0.2, 0.01);
    }

    private void thunder(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.55f, 0.7f);
    }

    private void darkness(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, true, true));
    }

    private void fakeCreeper(Player player, ThreadLocalRandom rng) {
        Location at = offset(player.getLocation(), 2, 5, rng);
        player.playSound(at, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
    }

    private void stalkerMob(Player player, ThreadLocalRandom rng) {
        Location at = offset(player.getLocation(), 14, 28, rng);
        at.setY(player.getLocation().getY());
        if (!at.getWorld().getWorldBorder().isInside(at)) return;
        EntityType type = STALKERS[rng.nextInt(STALKERS.length)];
        // NATURAL so WorldGuard block-plugin-spawning does not cancel these.
        Entity spawned = at.getWorld().spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.NATURAL);
        if (spawned instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(true);
            living.setCanPickupItems(false);
        }
        temps.add(spawned);
        Bukkit.getScheduler().runTaskLater(BrokenSpeedruns.getInstance(), () -> {
            if (spawned.isValid()) spawned.remove();
            temps.remove(spawned);
        }, 160L);
    }

    private void ownHeadStalker(Player player, ThreadLocalRandom rng) {
        Location at = offset(player.getLocation(), 12, 22, rng);
        at.setY(player.getEyeLocation().getY());
        Vector toPlayer = player.getEyeLocation().toVector().subtract(at.toVector());
        if (toPlayer.lengthSquared() > 0.01) {
            at.setDirection(toPlayer);
        }
        ArmorStand stand = at.getWorld().spawn(at, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setSilent(true);
            s.setInvulnerable(true);
            s.setCollidable(false);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(player);
                head.setItemMeta(meta);
            }
            s.getEquipment().setHelmet(head);
        });
        temps.add(stand);
        Bukkit.getScheduler().runTaskLater(BrokenSpeedruns.getInstance(), () -> {
            if (stand.isValid()) stand.remove();
            temps.remove(stand);
        }, 80L);
    }

    private void souls(Player player, ThreadLocalRandom rng) {
        Location at = player.getLocation().add(0, 1, 0);
        player.spawnParticle(Particle.SOUL, at, 12, 1.2, 0.6, 1.2, 0.02);
        player.playSound(offset(at, 4, 10, rng), Sound.PARTICLE_SOUL_ESCAPE, 0.7f, 0.5f);
    }

    private void lookDespawn(Player player) {
        Iterator<Entity> it = temps.iterator();
        Vector look = player.getEyeLocation().getDirection().normalize();
        while (it.hasNext()) {
            Entity e = it.next();
            if (e == null || !e.isValid()) {
                it.remove();
                continue;
            }
            Vector to = e.getLocation().toVector().subtract(player.getEyeLocation().toVector());
            if (to.lengthSquared() < 1 || to.normalize().dot(look) < 0.92) continue;
            e.remove();
            it.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.5f);
        }
    }

    private void cullTemps() {
        temps.removeIf(e -> e == null || !e.isValid());
    }

    private Location offset(Location from, double min, double max, ThreadLocalRandom rng) {
        double dist = min + rng.nextDouble() * (max - min);
        double yaw = rng.nextDouble() * Math.PI * 2;
        return from.clone().add(Math.cos(yaw) * dist, rng.nextDouble() * 2 - 0.5, Math.sin(yaw) * dist);
    }

    private record UUIDHolder(java.util.UUID uuid) {}
}
