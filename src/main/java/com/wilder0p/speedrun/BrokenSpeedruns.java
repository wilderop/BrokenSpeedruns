package com.wilder0p.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BrokenSpeedruns extends JavaPlugin {

    private static BrokenSpeedruns instance;
    private SpeedrunManager manager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        dataManager = new DataManager(this);
        manager = new SpeedrunManager(this, dataManager);

        getCommand("speedrun").setExecutor(new SpeedrunCommand(manager));
        Bukkit.getPluginManager().registerEvents(new EventListener(manager), this);

        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        dataManager.loadData();
        manager.loadActiveRuns();

        getLogger().info("Broken Speedruns v1.0 enabled - Solo speedrun system ready!");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
        if (dataManager != null) dataManager.saveData();
    }

    public static BrokenSpeedruns getInstance() { return instance; }
    public SpeedrunManager getManager() { return manager; }
    public DataManager getDataManager() { return dataManager; }
}
