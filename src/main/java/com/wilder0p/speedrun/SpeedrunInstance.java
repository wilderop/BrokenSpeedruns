package com.wilder0p.speedrun;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class SpeedrunInstance {
    private final UUID playerUUID;
    private final World overworld, nether, theEnd;
    private final long startTime;
    private final ItemStack[] savedInventory;
    private final GameMode savedGameMode;
    private final SpeedrunMode mode;

    public SpeedrunInstance(UUID playerUUID, World overworld, World nether, World theEnd,
                            long startTime, ItemStack[] savedInventory, GameMode savedGameMode) {
        this(playerUUID, overworld, nether, theEnd, startTime, savedInventory, savedGameMode, SpeedrunMode.CLASSIC);
    }

    public SpeedrunInstance(UUID playerUUID, World overworld, World nether, World theEnd,
                            long startTime, ItemStack[] savedInventory, GameMode savedGameMode,
                            SpeedrunMode mode) {
        this.playerUUID = playerUUID;
        this.overworld = overworld;
        this.nether = nether;
        this.theEnd = theEnd;
        this.startTime = startTime;
        this.savedInventory = savedInventory;
        this.savedGameMode = savedGameMode;
        this.mode = mode == null ? SpeedrunMode.CLASSIC : mode;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public World getOverworld() { return overworld; }
    public World getNether() { return nether; }
    public World getTheEnd() { return theEnd; }
    public long getStartTime() { return startTime; }
    public ItemStack[] getSavedInventory() { return savedInventory; }
    public GameMode getSavedGameMode() { return savedGameMode; }
    public SpeedrunMode getMode() { return mode; }
    public boolean isHorror() { return mode == SpeedrunMode.HORROR; }

    public String getFormattedTime() {
        long seconds = (System.currentTimeMillis() - startTime) / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    public long getTimeMillis() {
        return System.currentTimeMillis() - startTime;
    }
}
