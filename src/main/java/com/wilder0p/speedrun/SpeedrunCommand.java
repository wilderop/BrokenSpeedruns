package com.wilder0p.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;

public class SpeedrunCommand implements CommandExecutor {
    private final SpeedrunManager manager;

    public SpeedrunCommand(SpeedrunManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            manager.startSpeedrun(p);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "quit", "leave" -> {
                manager.quit(p);
                yield true;
            }
            case "list" -> {
                showActiveRuns(p);
                yield true;
            }
            case "top" -> {
                showLeaderboard(p);
                yield true;
            }
            default -> {
                p.sendMessage("§cUsage: /speedrun [quit|list|top]");
                yield true;
            }
        };
    }

    private void showActiveRuns(Player p) {
        p.sendMessage("§6§lActive Speedruns (" + manager.getActiveRuns().size() + "):");
        if (manager.getActiveRuns().isEmpty()) {
            p.sendMessage("§7None running right now.");
            return;
        }
        manager.getActiveRuns().forEach((uuid, run) -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() != null ? op.getName() : "Unknown";
            p.sendMessage(" §7• " + name + " §8— " + run.getFormattedTime());
        });
    }

    private void showLeaderboard(Player p) {
        p.sendMessage("§6§l=== Broken Speedruns Leaderboard (Top 10) ===");

        Map<UUID, Long> bests = BrokenSpeedruns.getInstance().getDataManager().getPersonalBestsForDisplay();
        if (bests.isEmpty()) {
            p.sendMessage("§7No completed speedruns yet. Be the first!");
            return;
        }

        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(bests.entrySet());
        sorted.sort(Comparator.comparingLong(Map.Entry::getValue));

        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<UUID, Long> entry = sorted.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            String name = op.getName() != null ? op.getName() : entry.getKey().toString().substring(0, 8) + "...";
            String timeStr = formatTime(entry.getValue());

            String rank = switch (i) {
                case 0 -> "§6§l1. ";
                case 1 -> "§7§l2. ";
                case 2 -> "§e3. ";
                default -> "§f" + (i + 1) + ". ";
            };

            p.sendMessage(rank + name + " §7— §b" + timeStr);
        }
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
