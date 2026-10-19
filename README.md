# Session Stats

A client-side Minecraft Fabric mod for 26.3. Press a key to start recording a play session, and when you
stop you get a clean summary of what you did: blocks mined and placed, ores, kills, deaths, distance and XP,
each with a per-hour rate. Past sessions are kept, so you can compare them over time.

Works in singleplayer and on any server, with nothing installed on the server.

## Keys

| Key | Action |
| --- | --- |
| F7 | Start or stop recording |
| F8 | Pause or resume recording |
| unbound | Open the session history |

All three can be changed in Controls → Key Binds → Session Stats. A line above the hotbar confirms each press.

## Features

- **Session totals**: active time, blocks mined, blocks placed, ores by type (stone and deepslate variants
  added up), mob kills, deaths, distance walked, flown and ridden, and XP gained.
- **Per-hour rates** for every total, for example "312 per hour".
- **Live HUD**: a small panel with a recording dot, the timer and up to 5 stats you pick. Drag it anywhere in
  the HUD editor (Mod Menu → Session Stats → Edit HUD). Can be turned off.
- **Summary screen** when you stop: big numbers, rates, the top 5 blocks mined and the ores, with the world
  name and date. "Copy as Text" puts the summary on the clipboard.
- **History**: every session of this world or server (or of all of them), sortable by date, length or any
  stat. Open one to see its summary again, or export the list as CSV.
- **Auto start** (off by default): start recording when joining a world or server. Recording always stops
  when you leave.
- Pausing the game in singleplayer does not count as active time.

## How the numbers are counted

The totals come from the game's own statistics, the same ones the vanilla Statistics screen shows. When a
recording starts, pauses, resumes or stops, the mod asks the server for your statistics (the same request the
Statistics screen makes) and stores the difference. That makes them accurate in singleplayer and on servers.

While recording, the HUD asks for fresh numbers every 30 seconds at most (configurable), so servers are not
spammed. Blocks you break and distance you move are also counted on the client in between, so the HUD stays
live. XP gained is counted on the client, since vanilla has no statistic for it.

## Files

Each session is saved as one small JSON file:

```
.minecraft/config/session-stats/<world or server>/<start time>.json
```

A running session is saved every minute, so a crash loses little; such sessions show up in the history with a
`*`. CSV exports are written to `.minecraft/config/session-stats/`, and the file's path is copied to the clipboard.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5 or newer for Minecraft 26.3.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3.
3. Put the mod jar in your `mods` folder.
4. (Optional) Install [Mod Menu](https://modrinth.com/mod/modmenu) for the settings screen.

## Configuration

Use the settings screen in Mod Menu, or edit `.minecraft/config/session-stats.json`:

```json
{
  "autoStart": false,
  "showHud": true,
  "hudStats": ["BLOCKS_MINED", "ORES_MINED", "DISTANCE"],
  "hudCorner": "TOP_LEFT",
  "hudOffsetX": 4,
  "hudOffsetY": 4,
  "showSummaryOnStop": true,
  "showMessages": true,
  "refreshSeconds": 30
}
```

HUD stats can be any of `BLOCKS_MINED`, `BLOCKS_PLACED`, `ORES_MINED`, `MOB_KILLS`, `DEATHS`, `DISTANCE` and `XP`.

## Building from source

Any JDK 17+ starts the Gradle wrapper. Minecraft 26.3 needs Java 25, which Gradle downloads automatically.

```bash
./gradlew build                     # jar in build/libs/, also runs the unit tests
./gradlew runClient                 # dev client with Mod Menu
./gradlew runClientGameTest         # in-game tests (src/gametest)
```

The recorder, totals, history and storage (`core` package) have no Minecraft dependencies and are covered by
plain unit tests in `src/test`. The client game test records a session in a real world: it awards stats on
the server, pauses and resumes, stops, and checks the summary and the saved file.
