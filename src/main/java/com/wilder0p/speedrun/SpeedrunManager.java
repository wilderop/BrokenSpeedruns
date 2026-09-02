package com.wilder0p.speedrun;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class SpeedrunManager {
    private final BrokenSpeedruns plugin;
    private final DataManager dataManager;
    private final Map<UUID, SpeedrunInstance> activeRuns = new HashMap<>();
    private final Set<UUID> preparing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> prepareTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> hintTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> boardTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> autoEndTasks = new HashMap<>();

    public SpeedrunManager(BrokenSpeedruns plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void loadActiveRuns() {
        dataManager.getActiveStartTimes().forEach((uuid, startTime) -> {
            String base = "speedrun-" + uuid;
            World ow = loadWorldIfPresent(base, World.Environment.NORMAL);
            World net = loadWorldIfPresent(base + "_nether", World.Environment.NETHER);
            World end = loadWorldIfPresent(base + "_the_end", World.Environment.THE_END);
            if (ow == null || net == null || end == null) return;

            Player p = Bukkit.getPlayer(uuid);
            ItemStack[] empty = new ItemStack[0];
            GameMode gm = p != null ? p.getGameMode() : GameMode.SURVIVAL;
            SpeedrunInstance inst = new SpeedrunInstance(uuid, ow, net, end, startTime, empty, gm);
            activeRuns.put(uuid, inst);
            if (p != null) {
                p.teleport(ow.getSpawnLocation());
                startScoreboard(p, inst);
            }
        });
        cleanupOrphanWorlds();
    }

    public boolean isBusy(UUID uuid) {
        return preparing.contains(uuid) || activeRuns.containsKey(uuid);
    }

    public void startSpeedrun(Player player) {
        UUID id = player.getUniqueId();
        if (isBusy(id)) {
            player.sendMessage("§cYou already have a speedrun going. §e/speedrun quit §cto leave.");
            return;
        }

        preparing.add(id);
        cancelHint(id);
        player.sendMessage("§eGenerating a fresh overworld, nether, and end. Hang tight — you can still walk around.");

        long seed = ThreadLocalRandom.current().nextLong();
        String base = "speedrun-" + id;
        wipeWorld(base);
        wipeWorld(base + "_nether");
        wipeWorld(base + "_the_end");

        BukkitTask task = new BukkitRunnable() {
            int step = 0;
            World ow, nether, end;

            @Override
            public void run() {
                if (!preparing.contains(id) || !player.isOnline()) {
                    abortPrepare(id, base);
                    cancel();
                    return;
                }
                try {
                    switch (step++) {
                        case 0 -> {
                            ow = createWorld(base, World.Environment.NORMAL, seed);
                            player.sendMessage("§7Overworld ready...");
                        }
                        case 1 -> {
                            nether = createWorld(base + "_nether", World.Environment.NETHER, seed);
                            player.sendMessage("§7Nether ready...");
                        }
                        case 2 -> {
                            end = createWorld(base + "_the_end", World.Environment.THE_END, seed);
                            player.sendMessage("§7The End ready...");
                        }
                        default -> {
                            if (ow == null || nether == null || end == null) {
                                player.sendMessage("§cWorld generation failed. Try /speedrun again.");
                                abortPrepare(id, base);
                                cancel();
                                return;
                            }
                            beginRun(player, ow, nether, end);
                            cancel();
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed generating speedrun worlds for " + player.getName(), ex);
                    if (player.isOnline()) {
                        player.sendMessage("§cWorld generation failed. Try /speedrun again.");
                    }
                    abortPrepare(id, base);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 10L);
        prepareTasks.put(id, task);
    }

    public void restart(Player player) {
        if (isBusy(player.getUniqueId())) {
            quit(player);
        }
        startSpeedrun(player);
    }

    private void beginRun(Player player, World ow, World nether, World end) {
        UUID id = player.getUniqueId();
        preparing.remove(id);
        prepareTasks.remove(id);
        if (!player.isOnline()) {
            unloadAndDelete(ow);
            unloadAndDelete(nether);
            unloadAndDelete(end);
            return;
        }

        ItemStack[] savedInv = player.getInventory().getContents().clone();
        GameMode savedGm = player.getGameMode();
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        try {
            player.setHealth(20);
        } catch (IllegalArgumentException ignored) {
            player.setHealth(player.getHealth());
        }
        player.setFoodLevel(20);
        player.setSaturation(20);

        SpeedrunInstance inst = new SpeedrunInstance(
                id, ow, nether, end, System.currentTimeMillis(), savedInv, savedGm);
        activeRuns.put(id, inst);
        dataManager.saveActiveRuns(getStartTimesMap());

        ow.setDifficulty(Difficulty.HARD);
        Location spawn = ow.getSpawnLocation();
        ow.getChunkAt(spawn).load(true);
        createStarterPortal(ow);
        player.teleport(spawn);

        startScoreboard(player, inst);
        start24HourAutoEnd(inst);

        player.sendMessage("§a§lSpeedrun started! Kill the dragon to finish.");
        player.sendMessage("§7Quit anytime with §e/speedrun quit§7. Clock is running.");
    }

    private World createWorld(String name, World.Environment env, long seed) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        WorldCreator wc = new WorldCreator(name);
        wc.environment(env);
        wc.seed(seed);
        wc.generateStructures(true);
        // Creating three dimensions on the command thread used to trip Paper's watchdog
        wc.keepSpawnLoaded(TriState.FALSE);
        return wc.createWorld();
    }

    private World loadWorldIfPresent(String name, World.Environment env) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (!folder.isDirectory()) return null;
        return createWorld(name, env, 0L);
    }

    private void createStarterPortal(World ow) {
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

        dest.getWorld().getChunkAt(dest).load(true);
        e.getPlayer().teleport(dest);
    }

    private Location calculateScaled(Location from, World to) {
        double scale = (to.getEnvironment() == World.Environment.NETHER) ? 1.0 / 8.0 : 8.0;
        return new Location(to, from.getX() * scale, from.getY(), from.getZ() * scale);
    }

    public void quit(Player p) {
        UUID id = p.getUniqueId();
        if (preparing.contains(id)) {
            abortPrepare(id, "speedrun-" + id);
            p.sendMessage("§cCancelled world generation.");
            return;
        }

        SpeedrunInstance run = activeRuns.remove(id);
        if (run == null) {
            p.sendMessage("§cYou're not in a speedrun. §e/speedrun §7to start one.");
            return;
        }

        cancelRunTasks(id);
        restorePlayer(p, run);
        p.teleport(lobbySpawn(p));
        cleanupWorlds(run);
        dataManager.saveActiveRuns(getStartTimesMap());
        p.sendMessage("§cSpeedrun quit.");
    }

    public void finish(Player p) {
        SpeedrunInstance run = activeRuns.remove(p.getUniqueId());
        if (run == null) return;

        cancelRunTasks(p.getUniqueId());

        long time = run.getTimeMillis();
        long pb = plugin.getDataManager().getPersonalBest(p.getUniqueId());
        boolean newPB = pb == -1 || time < pb;

        if (newPB) plugin.getDataManager().setPersonalBest(p.getUniqueId(), time);

        String formatted = run.getFormattedTime();
        restorePlayer(p, run);
        p.teleport(lobbySpawn(p));

        for (int i = 0; i < 5; i++) {
            Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(Color.RED, Color.GREEN).with(FireworkEffect.Type.BALL_LARGE).build());
            fw.setFireworkMeta(meta);
        }

        String msg = plugin.getConfig().getString("finish-broadcast", "")
                .replace("%player%", p.getName())
                .replace("%time%", formatted);
        if (!msg.isBlank()) broadcastToNetwork(msg);

        String cmd = plugin.getConfig().getString("finish-command", "give %player% diamond 1")
                .replace("%player%", p.getName());
        if (!cmd.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        p.sendMessage("§6§lSpeedrun finished in " + formatted + (newPB ? " §a§l(PB!)" : ""));

        cleanupWorlds(run);
        dataManager.saveActiveRuns(getStartTimesMap());
    }

    private void restorePlayer(Player p, SpeedrunInstance run) {
        p.getInventory().setContents(run.getSavedInventory());
        p.setGameMode(run.getSavedGameMode());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private Location lobbySpawn(Player p) {
        World w = Bukkit.getWorld("world");
        if (w == null || w.getName().startsWith("speedrun-")) {
            for (World candidate : Bukkit.getWorlds()) {
                if (!candidate.getName().startsWith("speedrun-")) {
                    w = candidate;
                    break;
                }
            }
        }
        if (w == null) w = p.getWorld();
        return w.getSpawnLocation();
    }

    private void cleanupWorlds(SpeedrunInstance run) {
        new BukkitRunnable() {
            public void run() {
                unloadAndDelete(run.getOverworld());
                unloadAndDelete(run.getNether());
                unloadAndDelete(run.getTheEnd());
            }
        }.runTaskLater(plugin, 20L);
    }

    private void unloadAndDelete(World world) {
        if (world == null) return;
        File folder = world.getWorldFolder();
        Bukkit.unloadWorld(world, false);
        deleteFolder(folder);
    }

    private void wipeWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) Bukkit.unloadWorld(w, false);
        deleteFolder(new File(Bukkit.getWorldContainer(), name));
    }

    private void abortPrepare(UUID id, String base) {
        preparing.remove(id);
        BukkitTask t = prepareTasks.remove(id);
        if (t != null) t.cancel();
        wipeWorld(base);
        wipeWorld(base + "_nether");
        wipeWorld(base + "_the_end");
    }

    public void handleQuit(Player p) {
        cancelHint(p.getUniqueId());
        if (preparing.contains(p.getUniqueId())) {
            abortPrepare(p.getUniqueId(), "speedrun-" + p.getUniqueId());
        }
    }

    private void deleteFolder(File folder) {
        if (folder == null || !folder.exists()) return;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteFolder(f);
                else f.delete();
            }
        }
        folder.delete();
    }

    private void cleanupOrphanWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] files = container.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isDirectory() || !f.getName().startsWith("speedrun-")) continue;
            String name = f.getName();
            String uuidPart = name
                    .replace("_nether", "")
                    .replace("_the_end", "")
                    .substring("speedrun-".length());
            try {
                UUID uuid = UUID.fromString(uuidPart);
                if (activeRuns.containsKey(uuid) || preparing.contains(uuid)) continue;
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            wipeWorld(name);
            plugin.getLogger().info("Removed leftover speedrun world " + name);
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
        cancelTask(boardTasks, p.getUniqueId());
        BukkitTask task = new ScoreboardTask(p, inst, this, dataManager).runTaskTimer(plugin, 0L, 20L);
        boardTasks.put(p.getUniqueId(), task);
    }

    private void start24HourAutoEnd(SpeedrunInstance inst) {
        cancelTask(autoEndTasks, inst.getPlayerUUID());
        BukkitTask task = new BukkitRunnable() {
            public void run() {
                Player p = Bukkit.getPlayer(inst.getPlayerUUID());
                if (p != null) finish(p);
            }
        }.runTaskLater(plugin, 20L * 60 * 60 * 24);
        autoEndTasks.put(inst.getPlayerUUID(), task);
    }

    private void cancelRunTasks(UUID id) {
        cancelTask(boardTasks, id);
        cancelTask(autoEndTasks, id);
    }

    private void cancelTask(Map<UUID, BukkitTask> map, UUID id) {
        BukkitTask t = map.remove(id);
        if (t != null) t.cancel();
    }

    public void scheduleJoinHint(Player p) {
        if (!plugin.getConfig().getBoolean("join-hint.enabled", true)) return;
        if (isBusy(p.getUniqueId())) return;
        cancelHint(p.getUniqueId());
        long delay = plugin.getConfig().getLong("join-hint.delay-ticks", 60L);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                hintTasks.remove(p.getUniqueId());
                if (!p.isOnline() || isBusy(p.getUniqueId())) return;
                sendJoinHint(p);
            }
        }.runTaskLater(plugin, Math.max(1L, delay));
        hintTasks.put(p.getUniqueId(), task);
    }

    public void sendJoinHint(Player p) {
        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("\u2726 BROKEN SPEEDRUNS \u2726")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        p.sendMessage(Component.text("Think you can Any% a fresh world? Isolated overworld, nether, and end \u2014 kill the dragon, beat the clock.")
                .color(NamedTextColor.YELLOW));

        Map.Entry<String, Long> record = currentRecord();
        if (record == null) {
            p.sendMessage(Component.text("No one has finished yet. First record is yours.")
                    .color(NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text("Record to beat: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(record.getKey()).color(NamedTextColor.AQUA))
                    .append(Component.text(" in ").color(NamedTextColor.GRAY))
                    .append(Component.text(formatTime(record.getValue())).color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD)));
        }

        Component start = Component.text("/speedrun")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/speedrun"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to start a solo Any% run")));
        p.sendMessage(Component.text("Click ")
                .color(NamedTextColor.GRAY)
                .append(start)
                .append(Component.text(" or type it to start.").color(NamedTextColor.GRAY)));
        sendHelp(p);
        p.sendMessage(Component.empty());
    }

    public void sendHelp(Player p) {
        p.sendMessage(Component.text("  /speedrun").color(NamedTextColor.YELLOW)
                .append(Component.text(" \u2014 start a solo Any%").color(NamedTextColor.GRAY)));
        p.sendMessage(Component.text("  /speedrun quit").color(NamedTextColor.YELLOW)
                .append(Component.text(" \u2014 leave and return to lobby").color(NamedTextColor.GRAY)));
        p.sendMessage(Component.text("  /speedrun restart").color(NamedTextColor.YELLOW)
                .append(Component.text(" \u2014 scrap this seed and roll a new one").color(NamedTextColor.GRAY)));
        p.sendMessage(Component.text("  /speedrun top").color(NamedTextColor.YELLOW)
                .append(Component.text(" \u2014 fastest times").color(NamedTextColor.GRAY)));
        p.sendMessage(Component.text("  /speedrun list").color(NamedTextColor.YELLOW)
                .append(Component.text(" \u2014 who is running right now").color(NamedTextColor.GRAY)));
    }

    private Map.Entry<String, Long> currentRecord() {
        Map<UUID, Long> bests = dataManager.getPersonalBestsForDisplay();
        UUID bestId = null;
        long best = Long.MAX_VALUE;
        for (Map.Entry<UUID, Long> e : bests.entrySet()) {
            if (e.getValue() < best) {
                best = e.getValue();
                bestId = e.getKey();
            }
        }
        if (bestId == null) return null;
        String name = Bukkit.getOfflinePlayer(bestId).getName();
        if (name == null) name = "Unknown";
        return Map.entry(name, best);
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private void cancelHint(UUID id) {
        cancelTask(hintTasks, id);
    }

    public void shutdown() {
        for (BukkitTask t : prepareTasks.values()) t.cancel();
        for (BukkitTask t : hintTasks.values()) t.cancel();
        for (BukkitTask t : boardTasks.values()) t.cancel();
        for (BukkitTask t : autoEndTasks.values()) t.cancel();
        prepareTasks.clear();
        hintTasks.clear();
        boardTasks.clear();
        autoEndTasks.clear();
        preparing.clear();
        activeRuns.clear();
    }

    public Map<UUID, SpeedrunInstance> getActiveRuns() { return activeRuns; }
}
