package dev.rubysteal.core;

import dev.rubysteal.RubyStealPlugin;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Combat tagging and kill-farming checks. */
public final class AntiAbuse {
    private final RubyStealPlugin plugin;
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> pairKills = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> killerXp = new ConcurrentHashMap<>();

    public AntiAbuse(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ combat

    public void tag(Player victim, Player attacker) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("combat");
        if (c == null || !c.getBoolean("enabled", true)) return;
        long until = System.currentTimeMillis() + c.getInt("tag-seconds", 15) * 1000L;
        boolean victimWasFree = !inCombat(victim);
        boolean attackerWasFree = !inCombat(attacker);
        combatUntil.put(victim.getUniqueId(), until);
        combatUntil.put(attacker.getUniqueId(), until);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());
        if (victimWasFree) plugin.messages().send(victim, "combat-tagged", "seconds", c.getInt("tag-seconds", 15));
        if (attackerWasFree) plugin.messages().send(attacker, "combat-tagged", "seconds", c.getInt("tag-seconds", 15));
    }

    public boolean inCombat(Player p) {
        Long u = combatUntil.get(p.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    /** The player who last hit this player while the tag is still active, else null. */
    public UUID activeAttacker(UUID victim) {
        Long u = combatUntil.get(victim);
        return (u != null && u > System.currentTimeMillis()) ? lastAttacker.get(victim) : null;
    }

    public void clearCombat(UUID id) {
        combatUntil.remove(id);
        lastAttacker.remove(id);
    }

    // ------------------------------------------------------------- kill checks

    private static void prune(Deque<Long> q, long window, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > window) q.pollFirst();
    }

    /** @return null when the kill counts, otherwise the message key explaining why not. */
    public String check(Player killer, Player victim) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("anti-farm");
        if (c == null || !c.getBoolean("enabled", true)) return null;
        if (killer.getUniqueId().equals(victim.getUniqueId())) return "farm-self";
        if (killer.hasPermission("rubysteal.bypass.antifarm")) return null;
        long now = System.currentTimeMillis();

        if (c.getBoolean("block-same-ip", true) && killer.getAddress() != null && victim.getAddress() != null) {
            InetAddress a = killer.getAddress().getAddress();
            InetAddress b = victim.getAddress().getAddress();
            if (a != null && a.equals(b)) return "farm-same-ip";
        }
        int minMinutes = c.getInt("min-victim-playtime-minutes", 5);
        if (minMinutes > 0) {
            long minutes = victim.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
            if (minutes < minMinutes) return "farm-new-player";
        }
        Deque<Long> pair = pairKills.get(killer.getUniqueId() + ":" + victim.getUniqueId());
        if (pair != null) {
            int cooldown = c.getInt("same-victim-cooldown-seconds", 600);
            int perHour = c.getInt("max-kills-per-victim-per-hour", 3);
            synchronized (pair) {
                prune(pair, 3_600_000L, now);
                if (cooldown > 0 && !pair.isEmpty() && now - pair.peekLast() < cooldown * 1000L) return "farm-cooldown";
                if (perHour > 0 && pair.size() >= perHour) return "farm-limit";
            }
        }
        int hourly = c.getInt("max-xp-per-hour", 40);
        if (hourly > 0) {
            Deque<Long> q = killerXp.get(killer.getUniqueId());
            if (q != null) {
                synchronized (q) {
                    prune(q, 3_600_000L, now);
                    if (q.size() >= hourly) return "farm-hourly-cap";
                }
            }
        }
        return null;
    }

    public void record(Player killer, Player victim) {
        long now = System.currentTimeMillis();
        Deque<Long> pair = pairKills.computeIfAbsent(killer.getUniqueId() + ":" + victim.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (pair) {
            pair.addLast(now);
        }
        Deque<Long> q = killerXp.computeIfAbsent(killer.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(now);
        }
    }

    public void forget(UUID id) {
        clearCombat(id);
        killerXp.remove(id);
    }

    /** Drops stale history so memory use stays bounded. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        pairKills.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), 3_600_000L, now);
                return e.getValue().isEmpty();
            }
        });
        killerXp.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), 3_600_000L, now);
                return e.getValue().isEmpty();
            }
        });
        combatUntil.entrySet().removeIf(e -> e.getValue() 
          < now);
    }
  }
