<img src="https://github.com/matejstastny/better-carpet-bots/blob/main/src/main/resources/icon.png?raw=true" alt="Modpack icon" width="25%" align="right">

# Better Carpet Bots

A [Carpet mod](https://github.com/gnembon/fabric-carpet) extension that adds powerful bot management commands to your Minecraft server.

### Requirements

- [Fabric Loader](https://fabricmc.net/) ≥ 0.15.0
- [Carpet Mod](https://modrinth.com/mod/carpet)

## Commands

### `/bot spawn [<name>]`

Spawns a bot at your position in survival mode. If no name is given, one is auto-generated (`Bot1`, `Bot2`, …). Names must be 1–16 characters, letters/digits/underscores only.

Names that belong to real players who have ever joined the server cannot be used.

### `/bot <name> <action>`

Controls a bot (or yourself - targeting your own name works too).

| Action | Description |
|---|---|
| `attack` | Attack continuously |
| `attack continuous` | Same as above |
| `attack interval <ticks>` | Attack every N ticks |
| `attack stop` | Stop attacking |
| `use` | Right-click / use continuously |
| `use continuous` | Same as above |
| `use interval <ticks>` | Use every N ticks |
| `use stop` | Stop using |
| `jump` / `jump stop` | Start / stop jumping |
| `sneak` / `sneak stop` | Start / stop sneaking |
| `sprint` / `sprint stop` | Start / stop sprinting |
| `stop` | Stop all actions |
| `look <north\|south\|east\|west\|up\|down>` | Look in a cardinal direction |
| `look <yaw> <pitch>` | Look at an exact angle |
| `drop` | Drop current hotbar item (single) |
| `dropstack` | Drop current hotbar item (full stack) |
| `swaphands` | Swap main hand and off hand |
| `inventory` | Open bot inventory (must be within 10 blocks) |
| `kill` | Remove the bot from the server |

### `/bots <action>`

Requires game-master permission (level 2).

| Action | Description |
|---|---|
| `list` | List all active bots |
| `stop` | Stop all bots |
| `kill` | Kill all bots |
| `permissionLevel <0–4>` | Set who can use `/bot` (0 = everyone, 4 = operators only) |
| `skin all <url>` | Apply a skin to every bot (persists across respawns) |
| `skin <name> <url>` | Apply a skin to one specific bot |

## Bot Inventory

`/bot <name> inventory` opens a 5-row screen showing the bot's full inventory:

- Rows 0–3: main inventory and hotbar
- Row 4: armor slots (only accept the correct armor type), off-hand, and locked filler slots
- Empty armor/off-hand slots show colored glass pane labels

The screen stays live - items the bot picks up appear immediately. The screen closes automatically if you move more than 10 blocks away.

## Real Player Protection

When a player authenticated via Mojang joins for the first time, their name is permanently locked. `/bot spawn <thatName>` will be rejected from that point on. Any existing offline-UUID bot data for that name is backed up to `world/bot-backup/<name>.bak` before being cleaned up, so nothing is lost.

## Releasing

Add your notes under `## [Current]` in `CHANGELOG.md`, then run:

```sh
./release.sh
```

The script reads your entries from `## [Current]`, prompts for the new version, stamps a date and version header on that section, and leaves a fresh empty `## [Current]` for next time. It then commits, tags, and pushes — the release CI publishes to Modrinth and creates a GitHub Release using those notes.
