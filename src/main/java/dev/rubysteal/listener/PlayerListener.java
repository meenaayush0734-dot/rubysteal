package dev.rubysteal.listener;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public final class PlayerListener implements Listener {
    private final RubyStealPlugin plugin;

    public PlayerListener(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            PlayerData d = plugin.db().load(e.getUniqueId()).join();
            if (d == null) {
                d = plugin.ruby().createNew(e.getUniqueId(), e.getName());
                plugin.db().save(d.copy());
            }
            d.name = e.getName();
            plugin.ruby().put(d);
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not load Ruby data for " + e.getName() + ": " + ex.getMessage());
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.messages().get("db-error-kick"));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.ruby().get(p);
        if (d == null) {
            p.kick(plugin.messages().get("db-error-kick"));
            return;
        }
        d.name = p.getName();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.ruby().validate(p);
            if (plugin.ruby().consumeFirstJoin(p.getUniqueId())) {
                plugin.db().addHistory(p.getUniqueId(), "FIRST_JOIN", plugin.ruby().level(d.level).name());
                plugin.messages().send(p, "welcome-first-join", "ruby", plugin.ruby().level(d.level).coloredName());
            }
        }, 5L);
        long delay = Math.max(1, plugin.getConfig().getLong("resource-pack.send-delay-ticks", 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.pack().send(p);
        }, delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        var cfg = plugin.getConfig();
        if (cfg.getBoolean("combat.enabled", true) && cfg.getBoolean("combat.kill-on-logout", true)
                && plugin.abuse().inCombat(p) && !p.hasPermission("rubysteal.bypass.combat") && p.getHealth() > 0) {
            Bukkit.broadcast(plugin.messages().get("combat-logout-broadcast", "player", p.getName()));
            p.setHealth(0.0); // fires PlayerDeathEvent: drops, death count and killer credit are handled there
        }
        PlayerData d = plugin.ruby().cached(id);
        if (d != null) plugin.ruby().saveAsync(d);
        plugin.ruby().remove(id);
        plugin.abilities().forget(id);
        plugin.abuse().forget(id);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        Entity damager = e.getDamager();
        if (damager instanceof Player p) attacker = p;
        else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) attacker = shooter;
        if (attacker == null || attacker.equals(victim)) return;
        plugin.abuse().tag(victim, attacker);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        // Ruby items never drop: they are re-issued on respawn (new serial invalidates stray copies).
        e.getDrops().removeIf(plugin.ruby()::isRuby);

        PlayerData vd = plugin.ruby().cached(victim.getUniqueId());
        if (vd != null) {
            vd.deaths++;
            if (plugin.getConfig().getBoolean("abilities.reset-cooldowns-on-death", false)) vd.cooldowns.clear();
            plugin.ruby().saveAsync(vd);
        }
        plugin.abilities().clearLife(victim.getUniqueId());

        Player killer = victim.getKiller();
        if (killer == null) {
            UUID attacker = plugin.abuse().activeAttacker(victim.getUniqueId());
            if (attacker != null) killer = Bukkit.getPlayer(attacker);
        }
        plugin.abuse().clearCombat(victim.getUniqueId());
        if (killer == null || killer.equals(victim)) return;

        String reason = plugin.abuse().check(killer, victim);
        if (reason != null) {
            plugin.messages().send(killer, reason, "victim", victim.getName());
            return;
        }
        plugin.abuse().record(killer, victim);
        plugin.ruby().addKill(killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.ruby().validate(p);
        }, 
                2L);
    }
      }
