# RubySteal - Test Plan

Run on a Paper 1.21.4 test server with two accounts (A, B) and an op.
Tip: temporarily set `anti-farm.enabled: false` or give A `rubysteal.bypass.antifarm` to speed up evolution tests.

## Startup
- [ ] Console prints "RubySteal enabled with 10 levels and 9 abilities", no stack traces.
- [ ] `plugins/RubySteal/rubysteal.db` exists.

## First join / persistence
- [ ] New player receives one Basic Ruby with name, lore, custom icon (with pack).
- [ ] Rejoin: no second Ruby. Restart server: level/XP/kills/cooldowns remain (`/ruby stats`).

## Evolution (use /ruby setlevel or real kills)
- [ ] Kill counts: 5, 12, 22, 35, 50, 70, 95, 125, 160 -> each triggers title, sound, particles, new item model.
- [ ] Broadcast appears from level 3. Unlock message lists the new ability.
- [ ] Progress bar and next-requirement update in `/ruby`.

## Abilities (level via setlevel)
- [ ] L2 Quickstep, L3 Miner's Touch, L4 Ruby Guard, L5 Ruby Pulse, L6 Vital Spark, L7 Fire Resistance,
      L8 Crimson Rush, L9 Ruby Aura, L10 Ruby Awakening: right-click applies effect, sound, particles.
- [ ] Sneak + right-click cycles abilities; action bar shows READY / remaining seconds.
- [ ] Using during cooldown shows remaining time; relog/restart does not reset the cooldown.
- [ ] Vital Spark: 4th use in one life is refused; full health refuses without spending a use.
- [ ] Ruby Pulse refused while combat-tagged. Ruby Aura only buffs scoreboard-team members in radius.
- [ ] Removing `rubysteal.ability.quickstep` blocks only that ability.

## Commands & permissions
- [ ] /ruby, stats, abilities, top work for normal players; inspect/setlevel/reset/reload refused without permission.
- [ ] Tab completion: sub-commands (permission-filtered), player names, levels 1-10.
- [ ] inspect/setlevel/reset work for an offline player name.

## Protection
- [ ] Q-drop, drag into chest/ender chest/anvil/crafting/furnace, shift-click, hotbar-swap, item frame: all blocked.
- [ ] Die: Ruby is not in drops; reappears after respawn.
- [ ] Creative middle-click clone is blocked; extra copies are removed within 5 s (check console + alert).
- [ ] Combat: A hits B, B logs out within 15 s -> B dies, A gets XP.
- [ ] Anti-farm: repeat kill of same victim within 10 min gives "No Ruby XP"; same-IP accounts give none.

## Pack
- [ ] With `resource-pack.enabled: true` + valid URL/SHA-1 the prompt appears; `required: true` kicks on decline.

## Backup
- [ ] After `backup.interval-minutes`, a file appears in `plugins/RubySteal/ba
ckups/`.
