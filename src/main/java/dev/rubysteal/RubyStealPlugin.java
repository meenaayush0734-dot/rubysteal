package dev.rubysteal;

import dev.rubysteal.cmd.RubyCommand;
import dev.rubysteal.core.AbilityManager;
import dev.rubysteal.core.AntiAbuse;
import dev.rubysteal.core.PackService;
import dev.rubysteal.core.RubyManager;
import dev.rubysteal.db.Database;
import dev.rubysteal.gui.RubyGui;
import dev.rubysteal.listener.AbilityListener;
import dev.rubysteal.listener.GuiListener;
import dev.rubysteal.listener.PackListener;
import dev.rubysteal.listener.PlayerListener;
import dev.rubysteal.listener.ProtectionListener;
import dev.rubysteal.model.AbilityDef;
import dev.rubysteal.model.PlayerData;
import dev.rubysteal.util.Messages;
import dev.rubysteal.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class RubyStealPlugin extends JavaPlugin {
    private Database db;
    private Messages messages;
    private RubyManager ruby;
    private AbilityManager abilities;
    private AntiAbuse abuse;
    private PackService pack;

    public Database db() { return db; }
    public Messages messages() { return messages; }
    public RubyManager ruby() { return ruby; }
    public AbilityManager abilities() { return abilities; }
    public AntiAbuse abuse() { return abuse; }
    public PackService pack() { return pack; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        ruby = new RubyManager(this);
        abilities = new AbilityManager(this);
        abuse = new AntiAbuse(this);
        pack = new PackService(this);
        try {
            ruby.reloadLevels();
            abilities.reload();
        } catch (RuntimeException ex) {
            getLogger().severe("Invalid config.yml: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        db = new Database(this);
        try {
            db.init();
        } catch (Exception ex) {
            getLogger().severe("Could not open the database: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pack.validateConfig();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new AbilityListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new PackListener(this), this);

        PluginCommand cmd = getCommand("ruby");
        if (cmd != null) {
            RubyCommand rc = new RubyCommand(this);
            cmd.setExecutor(rc);
            cmd.setTabCompleter(rc);
        }
        startTasks();
        ruby.refreshLeaderboard();
        for (Player p : Bukkit.getOnlinePlayers()) { // /reload or late enable
            if (ruby.get(p) != null) ruby.validate(p);
        }
        getLogger().info("RubySteal enabled with " + ruby.levels().size() + " levels and " + abilities.defs().size() + " abilities.");
    }

    @Override
    public void onDisable() {
        if (ruby != null && db != null) ruby.shutdownSave();
        if (db != null) db.close();
    }

    public void reloadAll() {
        reloadConfig();
        try {
            ruby.reloadLevels();
            abilities.reload();
        } catch (RuntimeException ex) {
            getLogger().severe("Reload failed, keeping previous levels/abilities: " + ex.getMessage());
        }
        pack.validateConfig();
        ruby.refreshLeaderboard();
        for (Player p : Bukkit.getOnlinePlayers()) ruby.validate(p);
    }

    public void openGui(Player p) {
        PlayerData d = ruby.get(p);
        if (d == null) return;
        db.history(d.uuid, getConfig().getInt("gui.history-entries", 5)).whenComplete((hist, ex) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!p.isOnline()) return;
                    new RubyGui(this, p, d, ex == null ? hist : java.util.List.of()).open(p);
                }));
    }

    // ------------------------------------------------------------------ tasks

    private void startTasks() {
        var cfg = getConfig();
        var sched = getServer().getScheduler();

        // Item protection scan.
        long scan = Math.max(20L, cfg.getLong("protection.scan-interval-ticks", 100L));
        sched.runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) ruby.validate(p);
        }, scan, scan);

        // Particles around players holding their Ruby.
        long pInt = Math.max(2L, cfg.getLong("particles.interval-ticks", 10L));
        sched.runTaskTimer(this, () -> {
            var c = getConfig();
            if (!c.getBoolean("particles.enabled", true)) return;
            boolean heldOnly = c.getBoolean("particles.only-when-held", true);
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData d = ruby.cached(p.getUniqueId());
                if (d == null || p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (heldOnly && !ruby.holdsRuby(p)) continue;
                ruby.level(d.level).particle().play(p.getLocation(), 1);
            }
        }, pInt, pInt);

        // Cooldown HUD (action bar) while holding the Ruby.
        long hudInt = Math.max(5L, cfg.getLong("hud.interval-ticks", 10L));
        sched.runTaskTimer(this, () -> {
            var c = getConfig();
            if (!c.getBoolean("hud.enabled", true)) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!ruby.holdsRuby(p)) continue;
                PlayerData d = ruby.cached(p.getUniqueId());
                if (d == null) continue;
                AbilityDef a = abilities.selected(p, d);
                if (a == null) continue;
                long rem = abilities.remaining(d, a);
                String status = rem > 0 ? c.getString("hud.cooldown-format", "<red>{time}") : c.getString("hud.ready-format", "<green>READY");
                p.sendActionBar(Text.raw(Text.fill(c.getString("hud.format", "{ability} {status}"),
                        "ability", a.name(), "status", Text.fill(status, "time", Text.time(rem)))));
            }
        }, hudInt, hudInt);

        // Autosave + housekeeping.
        long save = Math.max(20L, cfg.getLong("database.autosave-seconds", 120L) * 20L);
        sched.runTaskTimer(this, () -> {
            for (PlayerData d : ruby.allCached()) {
                Player p = Bukkit.getPlayer(d.uuid);
                if (p != null && p.isOnline()) ruby.saveAsync(d);
            }
            abuse.cleanup();
        }, save, save);

        // Leaderboard cache.
        long lb = Math.max(100L, cfg.getLong("leaderboard.refresh-seconds", 60L) * 20L);
        sched.runTaskTimer(this, ruby::refreshLeaderboard, lb, lb);

        // Backups.
        if (cfg.getBoolean("database.backup.enabled", true)) {
            long every = Math.max(1L, cfg.getLong("database.backup.interval-minutes", 60L)) * 60L * 20L;
            sched.runTaskTimerAsynchronously(this, () -> db.backup(
                    new File(getDataFolder(), "backups"), getConfig().getInt("database.backup.keep", 24)), every, every);

                         }
    }
  }
