package com.wilder0p.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class ScoreboardTask extends BukkitRunnable {
    private final Player player;
    private final SpeedrunInstance instance;
    private final SpeedrunManager manager;
    private final DataManager data;
    private final Scoreboard board;
    private final Team timeTeam;
    private final Team pbTeam;

    public ScoreboardTask(Player p, SpeedrunInstance inst, SpeedrunManager manager, DataManager d) {
        this.player = p;
        this.instance = inst;
        this.manager = manager;
        this.data = d;
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("speedrun", Criteria.DUMMY, "§6§lBROKEN SPEEDRUN");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Teams so the time string can change without leaking a new score line every second
        timeTeam = board.registerNewTeam("sr_time");
        timeTeam.addEntry("§e");
        obj.getScore("§e").setScore(2);

        pbTeam = board.registerNewTeam("sr_pb");
        pbTeam.addEntry("§a");
        obj.getScore("§a").setScore(1);

        player.setScoreboard(board);
    }

    @Override
    public void run() {
        if (!player.isOnline() || !manager.getActiveRuns().containsKey(player.getUniqueId())) {
            if (player.isOnline()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            cancel();
            return;
        }

        timeTeam.setPrefix("Time: " + instance.getFormattedTime());
        long pb = data.getPersonalBest(player.getUniqueId());
        pbTeam.setPrefix("PB: " + (pb == -1 ? "None" : formatPB(pb)));
    }

    private String formatPB(long millis) {
        long s = millis / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
