package com.wilder0p.speedrun;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class DataManager {
    private final BrokenSpeedruns plugin;
    private final Gson gson = new Gson();
    private final File dataFolder;
    private final Map<UUID, Long> personalBests = new HashMap<>();
    private final Map<UUID, Long> activeStartTimes = new HashMap<>();

    public DataManager(BrokenSpeedruns plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    public void loadData() {
        loadPersonalBests();
        loadActiveRuns();
    }

    public void saveData() {
        savePersonalBests();
        saveActiveRunsInternal();
    }

    public long getPersonalBest(UUID uuid) {
        return personalBests.getOrDefault(uuid, -1L);
    }

    public void setPersonalBest(UUID uuid, long time) {
        personalBests.put(uuid, time);
        savePersonalBests();
    }

    public Map<UUID, Long> getPersonalBestsForDisplay() {
        return new HashMap<>(personalBests);
    }

    public Map<UUID, Long> getActiveStartTimes() {
        return new HashMap<>(activeStartTimes);
    }

    /** This is the exact method SpeedrunManager is calling */
    public void saveActiveRuns(Map<UUID, Long> runs) {
        activeStartTimes.clear();
        activeStartTimes.putAll(runs);
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

    private void loadActiveRuns() {
        File file = new File(dataFolder, "active.json");
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> map = gson.fromJson(reader, type);
            map.forEach((k, v) -> activeStartTimes.put(UUID.fromString(k), v));
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
    }
}
