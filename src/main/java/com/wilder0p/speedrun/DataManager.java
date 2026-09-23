package com.wilder0p.speedrun;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DataManager {
    private final BrokenSpeedruns plugin;
    private final Gson gson = new Gson();
    private final File dataFolder;
    private final Map<UUID, Long> personalBests = new HashMap<>();
    private final Map<UUID, Long> horrorBests = new HashMap<>();
    private final Map<UUID, Long> activeStartTimes = new HashMap<>();
    private final Map<UUID, SpeedrunMode> activeModes = new HashMap<>();

    public DataManager(BrokenSpeedruns plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    public void loadData() {
        loadPersonalBests();
        loadHorrorBests();
        loadActiveRuns();
    }

    public void saveData() {
        savePersonalBests();
        saveHorrorBests();
        saveActiveRunsInternal();
    }

    public long getPersonalBest(UUID uuid) {
        return getPersonalBest(uuid, SpeedrunMode.CLASSIC);
    }

    public long getPersonalBest(UUID uuid, SpeedrunMode mode) {
        if (mode == SpeedrunMode.HORROR) return horrorBests.getOrDefault(uuid, -1L);
        return personalBests.getOrDefault(uuid, -1L);
    }

    public void setPersonalBest(UUID uuid, long time) {
        setPersonalBest(uuid, time, SpeedrunMode.CLASSIC);
    }

    public void setPersonalBest(UUID uuid, long time, SpeedrunMode mode) {
        if (mode == SpeedrunMode.HORROR) {
            horrorBests.put(uuid, time);
            saveHorrorBests();
        } else {
            personalBests.put(uuid, time);
            savePersonalBests();
        }
    }

    public Map<UUID, Long> getPersonalBestsForDisplay() {
        return getPersonalBestsForDisplay(SpeedrunMode.CLASSIC);
    }

    public Map<UUID, Long> getPersonalBestsForDisplay(SpeedrunMode mode) {
        return new HashMap<>(mode == SpeedrunMode.HORROR ? horrorBests : personalBests);
    }

    public Map<UUID, Long> getActiveStartTimes() {
        return new HashMap<>(activeStartTimes);
    }

    public SpeedrunMode getActiveMode(UUID uuid) {
        return activeModes.getOrDefault(uuid, SpeedrunMode.CLASSIC);
    }

    /** This is the exact method SpeedrunManager is calling */
    public void saveActiveRuns(Map<UUID, Long> runs) {
        saveActiveRuns(runs, Map.of());
    }

    public void saveActiveRuns(Map<UUID, Long> runs, Map<UUID, SpeedrunMode> modes) {
        activeStartTimes.clear();
        activeStartTimes.putAll(runs);
        activeModes.clear();
        activeModes.putAll(modes);
        saveActiveRunsInternal();
    }

    private void loadPersonalBests() {
        File file = new File(dataFolder, "leaderboard.json");
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> map = gson.fromJson(reader, type);
            map.forEach((k, v) -> personalBests.put(UUID.fromString(k), v));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load leaderboard");
        }
    }

    private void savePersonalBests() {
        File file = new File(dataFolder, "leaderboard.json");
        try (Writer writer = new FileWriter(file)) {
            Map<String, Long> map = new HashMap<>();
            personalBests.forEach((k, v) -> map.put(k.toString(), v));
            gson.toJson(map, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadHorrorBests() {
        File file = new File(dataFolder, "leaderboard-horror.json");
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> map = gson.fromJson(reader, type);
            if (map != null) map.forEach((k, v) -> horrorBests.put(UUID.fromString(k), v));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load horror leaderboard");
        }
    }

    private void saveHorrorBests() {
        File file = new File(dataFolder, "leaderboard-horror.json");
        try (Writer writer = new FileWriter(file)) {
            Map<String, Long> map = new HashMap<>();
            horrorBests.forEach((k, v) -> map.put(k.toString(), v));
            gson.toJson(map, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadActiveRuns() {
        File file = new File(dataFolder, "active.json");
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> map = gson.fromJson(reader, type);
            if (map != null) map.forEach((k, v) -> activeStartTimes.put(UUID.fromString(k), v));
        } catch (Exception ignored) {}
        File modes = new File(dataFolder, "active-modes.json");
        if (!modes.exists()) return;
        try (Reader reader = new FileReader(modes)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> map = gson.fromJson(reader, type);
            if (map == null) return;
            map.forEach((k, v) -> {
                SpeedrunMode mode = SpeedrunMode.fromArg(v);
                activeModes.put(UUID.fromString(k), mode != null ? mode : SpeedrunMode.CLASSIC);
            });
        } catch (Exception ignored) {}
    }

    private void saveActiveRunsInternal() {
        File file = new File(dataFolder, "active.json");
        try (Writer writer = new FileWriter(file)) {
            Map<String, Long> map = new HashMap<>();
            activeStartTimes.forEach((k, v) -> map.put(k.toString(), v));
            gson.toJson(map, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        File modes = new File(dataFolder, "active-modes.json");
        try (Writer writer = new FileWriter(modes)) {
            Map<String, String> map = new HashMap<>();
            activeModes.forEach((k, v) -> map.put(k.toString(), v.id()));
            gson.toJson(map, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public record StashedLobby(ItemStack[] inventory, GameMode gameMode) {}

    private File stashFile(UUID uuid) {
        File dir = new File(dataFolder, "lobby-inv");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, uuid + ".yml");
    }

    public void saveLobbyStash(UUID uuid, ItemStack[] inventory, GameMode gameMode) {
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.set("gamemode", gameMode == null ? "ADVENTURE" : gameMode.name());
            y.set("inventory", inventory);
            y.save(stashFile(uuid));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist lobby inventory for " + uuid);
        }
    }

    public StashedLobby loadLobbyStash(UUID uuid) {
        File file = stashFile(uuid);
        if (!file.exists()) return null;
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            String gmName = y.getString("gamemode", "ADVENTURE");
            GameMode gm;
            try {
                gm = GameMode.valueOf(gmName);
            } catch (IllegalArgumentException ex) {
                gm = GameMode.ADVENTURE;
            }
            List<?> raw = y.getList("inventory");
            ItemStack[] inv;
            if (raw == null) {
                inv = new ItemStack[0];
            } else {
                inv = raw.toArray(new ItemStack[0]);
            }
            return new StashedLobby(inv, gm);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load lobby inventory for " + uuid);
            return null;
        }
    }

    public void deleteLobbyStash(UUID uuid) {
        File file = stashFile(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete lobby inventory stash for " + uuid);
        }
    }
}
