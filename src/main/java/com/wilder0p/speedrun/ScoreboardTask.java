package com.wilder0p.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

public class ScoreboardTask extends BukkitRunnable {
    private final Player player;
    private final SpeedrunInstance instance;
    private final DataManager data;
    private final Scoreboard board;
    private final Objective obj;

    public ScoreboardTask(BrokenSpeedruns plugin, Player p, SpeedrunInstance inst, DataManager d) {
        this.player = p;
        this.instance = inst;
        this.data = d;
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        obj = board.registerNewObjective("speedrun", Criteria.DUMMY, "§6§lBROKEN SPEEDRUN");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        obj.getScore("Time: §e" + instance.getFormattedTime()).setScore(2);
        long pb = data.getPersonalBest(player.getUniqueId());
        obj.getScore("PB: §a" + (pb == -1 ? "None" : formatPB(pb))).setScore(1);

        player.setScoreboard(board);
    }

    private String formatPB(long millis) {
        long s = millis / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
