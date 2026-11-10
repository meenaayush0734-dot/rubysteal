package dev.rubysteal.cmd;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.model.AbilityDef;
import dev.rubysteal.model.LevelDef;
import dev.rubysteal.model.PlayerData;
import dev.rubysteal.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public final class RubyCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("stats", "abilities", "top", "inspect", "reload", "setlevel", "reset", "help");
    private final RubyStealPlugin plugin;

    public RubyCommand(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean allowed(CommandSender s, String sub) {
        return switch (sub) {
            case "help" -> true;
            case "stats", "abilities", "top", "inspect", "reload", "setlevel", "reset" -> s.hasPermission("rubysteal.command." + sub);
            default -> false;
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                plugin.messages().send(sender, "only-players");
                return true;
            }
            if (!p.hasPermission("rubysteal.use")) {
                plugin.messages().send(p, "no-permission");
                return true;
            }
            plugin.openGui(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {
            plugin.messages().send(sender, "unknown-subcommand");
            return true;
        }
        if (!allowed(sender, sub)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "help" -> lines(sender, "help");
            case "stats" -> stats(sender);
            case "abilities" -> abilities(sender);
            case "top" -> top(sender);
            case "inspect" -> inspect(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                plugin.messages().send(sender, "reloaded");
            }
            case "setlevel" -> setLevel(sender, args);
            case "reset" -> reset(sender, args);
            default -> { }
        }
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private void lines(CommandSender s, String key, Object... kv) {
        for (String line : plugin.getConfig().getStringList("messages." + key)) {
            s.sendMessage(Text.raw(Text.fill(line, kv).replace("{prefix}", plugin.getConfig().getString("messages.prefix", ""))));
        }
    }

    private void withTarget(CommandSender s, String name, BiConsumer<PlayerData, Player> action) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerData d = plugin.ruby().get(online);
            if (d != null) action.accept(d, online);
            return;
        }
        plugin.db().findByName(name).whenComplete((d, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null || d == null) {
                plugin.messages().send(s, "player-not-found", "player", name);
                return;
            }
            Player nowOnline = Bukkit.getPlayer(d.uuid); // joined while we were querying: use live data
            if (nowOnline != null) {
                PlayerData live = plugin.ruby().get(nowOnline);
                if (live != null) action.accept(live, nowOnline);
                return;
            }
            action.accept(d, null);
        }));
    }

    private void persist(PlayerData d, Player online) {
        if (online != null) {
            plugin.ruby().saveAsync(d);
            plugin.ruby().validate(online);
        } else {
            plugin.db().save(d.copy());
        }
        plugin.ruby().refreshLeaderboard();
    }

    // --------------------------------------------------------------- commands

    private void stats(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "only-players");
            return;
        }
        PlayerData d = plugin.ruby().get(p);
        if (d != null) sendStats(sender, "stats", d);
    }

    private void sendStats(CommandSender to, String key, PlayerData d) {
        LevelDef cur = plugin.ruby().level(d.level);
        LevelDef nxt = plugin.ruby().next(d.level);
        double prog = plugin.ruby().progress(d);
        lines(to, key, "player", d.name, "ruby", cur.coloredName(), "level", cur.level(), "max", plugin.ruby().maxLevel(),
                "xp", d.xp, "kills", d.kills, "deaths", d.deaths,
                "next", nxt == null ? "<gold>Max level" : nxt.coloredName(),
                "needed", nxt == null ? "-" : String.valueOf(nxt.requiredKills()),
                "remaining", nxt == null ? "0" : String.valueOf(Math.max(0, nxt.requiredKills() - d.xp)),
                "percent", (int) Math.round(prog * 100),
                "bar", Text.bar(prog, 20, "<red>|", "<dark_gray>|"));
    }

    private void abilities(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "only-players");
            return;
        }
        PlayerData d = plugin.ruby().get(p);
        if (d == null) return;
        plugin.messages().send(p, "abilities-header");
        AbilityDef sel = plugin.abilities().selected(p, d);
        for (AbilityDef a : plugin.abilities().defs()) {
            if (a.unlockLevel() <= d.level) {
                long rem = plugin.abilities().remaining(d, a);
                plugin.messages().send(p, "abilities-line-unlocked", "ability", a.name(), "description", a.description(),
                        "cooldown", a.cooldownSeconds(),
                        "status", rem > 0 ? "<red>" + Text.time(rem) : "<green>ready",
                        "selected", sel != null && sel.id().equals(a.id()) ? "<gold>*" : "");
            } else {
                plugin.messages().send(p, "abilities-line-locked", "ability", a.name(), "level", a.unlockLevel(),
                        "ruby", plugin.ruby().level(a.unlockLevel()).coloredName());
            }
        }
    }

    private void top(CommandSender sender) {
        List<PlayerData> top = plugin.ruby().leaderboard();
        plugin.messages().send(sender, "top-header");
        if (top.isEmpty()) {
            plugin.messages().send(sender, "top-empty");
            return;
        }
        int n = Math.min(plugin.getConfig().getInt("leaderboard.size", 10), top.size());
        for (int i = 0; i < n; i++) {
            PlayerData t = top.get(i);
            plugin.messages().send(sender, "top-line", "rank", i + 1, "player", t.name,
                    "ruby", plugin.ruby().level(t.level).coloredName(), "xp", t.xp, "kills", t.kills);
        }
    }

    private void inspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-inspect");
            return;
        }
        withTarget(sender, args[1], (d, online) -> sendStats(sender, "inspect", d));
    }

    private void setLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "usage-setlevel");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "invalid-level", "max", plugin.ruby().maxLevel());
            return;
        }
        if (level < plugin.ruby().levels().get(0).level() || level > plugin.ruby().maxLevel()) {
            plugin.messages().send(sender, "invalid-level", "max", plugin.ruby().maxLevel());
            return;
        }
        final int target = level;
        withTarget(sender, args[1], (d, online) -> {
            plugin.ruby().applyLevel(d, target);
            persist(d, online);
            plugin.messages().send(sender, "setlevel-done", "player", d.name, "ruby", plugin.ruby().level(d.level).coloredName());
            if (online != null) plugin.messages().send(online, "setlevel-notify", "ruby", plugin.ruby().level(d.level).coloredName());
        });
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-reset");
            return;
        }
        withTarget(sender, args[1], (d, online) -> {
            plugin.ruby().applyReset(d);
            persist(d, online);
            plugin.messages().send(sender, "reset-done", "player", d.name);
            if (online != null) plugin.messages().send(online, "reset-notify");
        });
    }

    // -------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (allowed(sender, s) && s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!allowed(sender, sub)) return out;
        boolean playerArg = sub.equals("inspect") || sub.equals("setlevel") || sub.equals("reset");
        if (args.length == 2 && playerArg) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        } else if (args.length == 3 && sub.equals("setlevel")) {
            for (LevelDef l : plugin.ruby().levels()) {
                String n = String.valueOf(l.level());
                if (n.startsWith(args[2])) out.add(n);
            }
        }
        return out;
    }
        }
      
