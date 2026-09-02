package com.wilder0p.speedrun;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.util.*;

public class SpeedrunManager {
    private final BrokenSpeedruns plugin;
    private final DataManager dataManager;
    private final Map<UUID, SpeedrunInstance> activeRuns = new HashMap<>();

    public SpeedrunManager(BrokenSpeedruns plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void loadActiveRuns() {
        dataManager.getActiveStartTimes().forEach((uuid, startTime) -> {
            String base = "speedrun-" + uuid;
            World ow = Bukkit.getWorld(base);
            if (ow == null) return;
            World net = Bukkit.getWorld(base + "_nether");
            World end = Bukkit.getWorld(base + "_the_end");
            if (net == null || end == null) return;

            Player p = Bukkit.getPlayer(uuid);
            ItemStack[] empty = new ItemStack[0];
            SpeedrunInstance inst = new SpeedrunInstance(uuid, ow, net, end, startTime, empty);
            activeRuns.put(uuid, inst);
            if (p != null) {
                p.teleport(ow.getSpawnLocation());
                startScoreboard(p, inst);
            }
        });
    }

    public void startSpeedrun(Player player) {
        if (activeRuns.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active speedrun!");
            return;
        }

        long seed = new Random().nextLong();
        String base = "speedrun-" + player.getUniqueId();

        World ow = createWorld(base, World.Environment.NORMAL, seed);
        World net = createWorld(base + "_nether", World.Environment.NETHER, seed);
        World end = createWorld(base + "_the_end", World.Environment.THE_END, seed);

        preGenerateSpawn(ow);
        preGenerateSpawn(net);
        preGenerateSpawn(end);
        createStarterPortal(ow, net);

        ItemStack[] savedInv = player.getInventory().getContents().clone();
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20);

        SpeedrunInstance inst = new SpeedrunInstance(player.getUniqueId(), ow, net, end, System.currentTimeMillis(), savedInv);
        activeRuns.put(player.getUniqueId(), inst);
        dataManager.saveActiveRuns(getStartTimesMap());

        player.teleport(ow.getSpawnLocation());
        ow.setDifficulty(Difficulty.HARD);

        startScoreboard(player, inst);
        start24HourAutoEnd(inst);

        player.sendMessage("§a§lSpeedrun started! Kill the dragon to finish.");
    }

    private World createWorld(String name, World.Environment env, long seed) {
        WorldCreator wc = new WorldCreator(name);
        wc.environment(env);
        wc.seed(seed);
        wc.generateStructures(true);
        return Bukkit.createWorld(wc);
    }

    private void preGenerateSpawn(World w) {
        new BukkitRunnable() {
            public void run() {
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) w.getChunkAt(x, z).load(true);
            }
        }.runTask(plugin);
    }

    private void createStarterPortal(World ow, World net) {
        Location portal = ow.getSpawnLocation().add(10, 1, 10);
        for (int y = 0; y < 5; y++) {
            ow.getBlockAt(portal.clone().add(0, y, 0)).setType(Material.OBSIDIAN);
            ow.getBlockAt(portal.clone().add(3, y, 0)).setType(Material.OBSIDIAN);
        }
    }

    public void handlePortal(PlayerPortalEvent e) {
        SpeedrunInstance run = activeRuns.get(e.getPlayer().getUniqueId());
        if (run == null) return;
        e.setCancelled(true);

        Location dest;
        if (e.getCause() == PlayerPortalEvent.TeleportCause.NETHER_PORTAL) {
            dest = (e.getFrom().getWorld().getEnvironment() == World.Environment.NORMAL)
                    ? calculateScaled(e.getFrom(), run.getNether())
                    : calculateScaled(e.getFrom(), run.getOverworld());
        } else if (e.getCause() == PlayerPortalEvent.TeleportCause.END_PORTAL) {
            dest = run.getTheEnd().getSpawnLocation().add(0.5, 1, 0.5);
        } else return;

        preGenerateChunks(dest.getWorld(), dest, 3);
        e.getPlayer().teleport(dest);
    }

    private Location calculateScaled(Location from, World to) {
        double scale = (to.getEnvironment() == World.Environment.NETHER) ? 1.0 / 8.0 : 8.0;
        return new Location(to, from.getX() * scale, from.getY(), from.getZ() * scale);
    }

    private void preGenerateChunks(World w, Location loc, int r) {
        new BukkitRunnable() {
            public void run() {
                int cx = loc.getBlockX() >> 4, cz = loc.getBlockZ() >> 4;
                for (int x = cx - r; x <= cx + r; x++)
                    for (int z = cz - r; z <= cz + r; z++)
                        w.getChunkAt(x, z).load(true);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void quit(Player p) {
        SpeedrunInstance run = activeRuns.remove(p.getUniqueId());
        if (run == null) return;

        restoreInventory(p, run);
        p.teleport(Bukkit.getWorld("world").getSpawnLocation());
        cleanupWorlds(run);
        dataManager.saveActiveRuns(getStartTimesMap());
        p.sendMessage("§cSpeedrun quit.");
    }

    public void finish(Player p) {
        SpeedrunInstance run = activeRuns.remove(p.getUniqueId());
        if (run == null) return;

        long time = run.getTimeMillis();
        long pb = plugin.getDataManager().getPersonalBest(p.getUniqueId());
        boolean newPB = pb == -1 || time < pb;

        if (newPB) plugin.getDataManager().setPersonalBest(p.getUniqueId(), time);

        String formatted = run.getFormattedTime();
        restoreInventory(p, run);
        p.teleport(Bukkit.getWorld("world").getSpawnLocation());

        // Fireworks
        for (int i = 0; i < 5; i++) {
            Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(Color.RED, Color.GREEN).with(FireworkEffect.Type.BALL_LARGE).build());
            fw.setFireworkMeta(meta);
        }

        // Velocity broadcast
        String msg = plugin.getConfig().getString("finish-broadcast", "")
                .replace("%player%", p.getName())
                .replace("%time%", formatted);
        broadcastToNetwork(msg);

        // Config command
        String cmd = plugin.getConfig().getString("finish-command", "give %player% diamond 1")
                .replace("%player%", p.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        p.sendMessage("§6§lSpeedrun finished in " + formatted + (newPB ? " §a§l(PB!)" : ""));

        cleanupWorlds(run);
        dataManager.saveActiveRuns(getStartTimesMap());
    }

    private void restoreInventory(Player p, SpeedrunInstance run) {
        p.getInventory().setContents(run.getSavedInventory());
    }

    private void cleanupWorlds(SpeedrunInstance run) {
        new BukkitRunnable() {
            public void run() {
                Bukkit.unloadWorld(run.getOverworld(), false);
                Bukkit.unloadWorld(run.getNether(), false);
                Bukkit.unloadWorld(run.getTheEnd(), false);
                deleteFolder(run.getOverworld().getWorldFolder());
                deleteFolder(run.getNether().getWorldFolder());
                deleteFolder(run.getTheEnd().getWorldFolder());
            }
        }.runTaskLater(plugin, 20L);
    }

    private void deleteFolder(File folder) {
        if (folder.exists()) {
            for (File f : folder.listFiles()) {
                if (f.isDirectory()) deleteFolder(f);
                else f.delete();
            }
            folder.delete();
        }
    }

    private void broadcastToNetwork(String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL");
        out.writeUTF("speedrunfinish");
        out.writeUTF(message);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            break;
        }
    }

    private Map<UUID, Long> getStartTimesMap() {
        Map<UUID, Long> map = new HashMap<>();
        activeRuns.forEach((k, v) -> map.put(k, v.getStartTime()));
        return map;
    }

    public void startScoreboard(Player p, SpeedrunInstance inst) {
        new ScoreboardTask(plugin, p, inst, dataManager).runTaskTimer(plugin, 0L, 20L);
    }

    private void start24HourAutoEnd(SpeedrunInstance inst) {
        new BukkitRunnable() {
            public void run() {
                Player p = Bukkit.getPlayer(inst.getPlayerUUID());
                if (p != null) finish(p);
            }
        }.runTaskLater(plugin, 20L * 60 * 60 * 24);
    }

    public void shutdown() {
        activeRuns.clear();
    }

    public Map<UUID, SpeedrunInstance> getActiveRuns() { return activeRuns; }
}
