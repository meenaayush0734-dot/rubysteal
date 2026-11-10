# RubySteal - Admin Command Guide

| Command | Permission | Description |
|---|---|---|
| `/ruby` | rubysteal.use (default: all) | Opens the Ruby GUI |
| `/ruby stats` | rubysteal.command.stats | Own level, XP, kills, deaths, progress |
| `/ruby abilities` | rubysteal.command.abilities | Unlocked/locked abilities and cooldowns |
| `/ruby top` | rubysteal.command.top | Leaderboard |
| `/ruby inspect <player>` | rubysteal.command.inspect | Stats of any player (online or offline) |
| `/ruby setlevel <player> <1-10>` | rubysteal.command.setlevel | Sets level and XP to that level's requirement. Kills/deaths unchanged |
| `/ruby reset <player>` | rubysteal.command.reset | Resets level, XP, kills, deaths, cooldowns; issues a fresh item |
| `/ruby reload` | rubysteal.command.reload | Reloads config, levels, abilities, messages |

`rubysteal.admin` (default: op) grants all admin commands and bypasses.

Bypass / utility permissions: `rubysteal.bypass.antifarm`, `rubysteal.bypass.combat`,
`rubysteal.bypass.dupe` (never have Rubies removed by the scan), `rubysteal.bypass.pack`,
`rubysteal.alerts` (in-game notice when protection removes an invalid Ruby),
`rubysteal.ability.<id>` (per ability, default true).

## Playing
* Right-click the Ruby: use the selected ability. Sneak + right-click: switch ability.
* The action bar shows the selected ability and its cooldown while the Ruby is held.
* Rubies are soulbound: they cannot be dropped, stored, traded, crafted, placed or shown in frames.
  On death they are removed from drops and re-issued on respawn.

## Data
* `plugins/RubySteal/rubysteal.db` (SQLite) - tables `players`, `cooldowns`, `history`.
* Backups: `plugins/RubySteal/backups/` (hourly by default, newest 24 kept).
  Restore: stop the server, replace `rubysteal.db` with a backup file, start.

## Tuning
Everything is in `config.yml`: kill requirements per level, ability effects/cooldowns/limits, particles,
anti-farm rules, combat tag, GUI materials, database, resource pack and every message (MiniMessage).
Task intervals and database settings need a restart; the rest applies on `/ruby rel
oad`.
