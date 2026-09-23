package com.wilder0p.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SpeedrunCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("start", "quit", "restart", "list", "top", "help", "horror");
    private static final List<String> MODES = List.of("classic", "horror");

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
            manager.startSpeedrun(p, SpeedrunMode.CLASSIC);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                SpeedrunMode mode = SpeedrunMode.CLASSIC;
                if (args.length >= 2) {
                    SpeedrunMode parsed = SpeedrunMode.fromArg(args[1]);
                    if (parsed == null) {
                        p.sendMessage("§cUnknown mode. Try §eclassic §cor §ehorror§c.");
                        yield true;
                    }
                    mode = parsed;
                }
                manager.startSpeedrun(p, mode);
                yield true;
            }
            case "horror" -> {
                manager.startSpeedrun(p, SpeedrunMode.HORROR);
                yield true;
            }
            case "classic" -> {
                manager.startSpeedrun(p, SpeedrunMode.CLASSIC);
                yield true;
            }
            case "quit", "leave", "stop", "exit" -> {
                manager.quit(p);
                yield true;
            }
            case "restart", "reset" -> {
                manager.restart(p);
                yield true;
            }
            case "list", "running" -> {
                showActiveRuns(p);
                yield true;
            }
            case "top", "pb", "leaderboard" -> {
                SpeedrunMode board = SpeedrunMode.CLASSIC;
                if (args.length >= 2) {
                    SpeedrunMode parsed = SpeedrunMode.fromArg(args[1]);
                    if (parsed != null) board = parsed;
                }
                showLeaderboard(p, board);
                yield true;
            }
            case "help", "?" -> {
                manager.sendHelp(p);
                yield true;
            }
            default -> {
                p.sendMessage("§cUnknown subcommand.");
                manager.sendHelp(p);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("top"))) {
            return MODES.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
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
            p.sendMessage(" §7• " + name + (run.isHorror() ? " §8[horror]" : "") + " §8— " + run.getFormattedTime());
        });
    }

    private void showLeaderboard(Player p, SpeedrunMode mode) {
        p.sendMessage("§6§l=== Broken Speedruns " + mode.display() + " (Top 10) ===");

        Map<UUID, Long> bests = BrokenSpeedruns.getInstance().getDataManager().getPersonalBestsForDisplay(mode);
        if (bests.isEmpty()) {
            p.sendMessage("§7No completed speedruns yet. Be the first!");
            return;
        }

        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(bests.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

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
