# Broken Speedruns

Paper plugin that gives each player a **solo Any%** in isolated overworld/nether/end worlds. Kill the dragon to finish.

Built for the lobby on [A Zombie Pigman Broke My Door](https://dontplaythisserver.com) (semi-anarchy).

## Player flow

1. `/speedrun` creates a fresh world trio with a shared seed, saves inventory, and teleports the player in
2. Sidebar shows live time and personal best
3. Dragon death (or 24 hours elapsed) ends the run
4. Inventory is restored, worlds are deleted, personal best is saved if faster
5. `/speedrun quit` abandons the run

## Commands

| Command | What it does |
|---------|----------------|
| `/speedrun` | Start a run |
| `/speedrun quit` | Leave and restore inventory |
| `/speedrun list` | Who is running right now |
| `/speedrun top` | Fastest 10 personal bests |

## Configuration (`config.yml`)

```yaml
# Command executed when a player finishes (dragon kill)
# Placeholders: %player%
finish-command: "give %player% diamond 1"
```

`finish-broadcast` is also read at runtime (not in the default config). Placeholders: `%player%`, `%time%`. Sent as a BungeeCord plugin message (`speedrunfinish`) to the whole network.

## Building

```bash
mvn clean package
```

The jar will be at `target/brokenspeedruns-1.0.jar`.
