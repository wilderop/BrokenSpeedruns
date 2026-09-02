# Broken Speedruns

Paper plugin that gives each player a **solo Any%** in isolated overworld/nether/end worlds. Kill the dragon to finish.

Built for the lobby on [A Zombie Pigman Broke My Door](https://dontplaythisserver.com) (semi-anarchy).

## Player flow

1. On lobby join, a delayed hint challenges the player and lists the commands (clickable `/speedrun`)
2. `/speedrun` generates a fresh world trio (staggered, spawn not kept loaded) then teleports them in
3. Sidebar shows live time and personal best
4. Dragon death (or 24 hours elapsed) ends the run
5. Inventory and gamemode are restored, worlds are deleted, personal best is saved if faster
6. `/speedrun quit` abandons the run

## Commands

| Command | What it does |
|---------|----------------|
| `/speedrun` | Start a run |
| `/speedrun quit` | Leave and restore inventory (`stop` / `leave` also work) |
| `/speedrun restart` | Scrap the seed and roll a new one |
| `/speedrun list` | Who is running right now |
| `/speedrun top` | Fastest 10 personal bests |
| `/speedrun help` | Command list |

## Configuration (`config.yml`)

```yaml
finish-command: "give %player% diamond 1"
finish-broadcast: ""
join-hint:
  enabled: true
  delay-ticks: 60
```

`finish-broadcast` placeholders: `%player%`, `%time%`. Sent as a BungeeCord plugin message (`speedrunfinish`) to the whole network. Empty string disables it.

## Building

```bash
mvn clean package
```

The jar will be at `target/brokenspeedruns-1.1.0.jar`. Live lobby name is `bsr.jar`.
