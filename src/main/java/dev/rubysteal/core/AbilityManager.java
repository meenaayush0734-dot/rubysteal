package dev.rubysteal.core;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.model.AbilityDef;
import dev.rubysteal.model.AbilityDef.EffectSpec;
import dev.rubysteal.model.AbilityDef.Type;
import dev.rubysteal.model.PlayerData;
import dev.rubysteal.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityManager {
    private final RubyStealPlugin plugin;
    private volatile List<AbilityDef> defs = List.of();
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> usesThisLife = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public AbilityManager(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("abilities.list");
        List<AbilityDef> out = new ArrayList<>();
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) continue;
                Type type;
                try {
                    type = Type.valueOf(s.getString("type", "EFFECT").toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Ability '" + id + "' has unknown type, skipped.");
                    continue;
                }
                List<EffectSpec> effects = new ArrayList<>();
                for (Map<?, ?> m : s.getMapList("effects")) {
                    String name = String.valueOf(m.get("type"));
                    PotionEffectType pet = PotionEffectType.getByKey(NamespacedKey.minecraft(name.toLowerCase()));
                    if (pet == null) {
                        plugin.getLogger().warning("Ability '" + id + "': unknown effect '" + name + "'");
                        continue;
                    }
                    int amp = m.get("amplifier") instanceof Number n ? n.intValue() : 0;
                    double secs = m.get("seconds") instanceof Number n ? n.doubleValue() : 5;
                    effects.add(new EffectSpec(pet, Math.max(0, amp), (int) Math.max(1, secs * 20)));
                }
                out.add(new AbilityDef(id.toLowerCase(), s.getString("name", id), s.getString("description", ""), type,
                        s.getInt("unlock-level", 1), Math.max(0, s.getInt("cooldown-seconds", 30)),
                        s.getBoolean("block-in-combat", false), s.getInt("uses-per-life", 0),
                        s.getString("sound", ""), effects, s.getDouble("power", 1.5), s.getDouble("vertical", 0.3),
                        s.getDouble("amount", 4.0), s.getDouble("radius", 6.0), s.getBoolean("include-self", true)));
            }
        }
        out.sort((a, b) -> Integer.compare(a.unlockLevel(), b.unlockLevel()));
        defs = List.copyOf(out);
        // Bukkit denies unregistered nodes, so every configured ability gets a default-true permission.
        for (AbilityDef a : out) {
            String node = "rubysteal.ability." + a.id();
            if (plugin.getServer().getPluginManager().getPermission(node) == null) {
                plugin.getServer().getPluginManager().addPermission(
                        new Permission(node, "Use the " + a.name() + " ability", PermissionDefault.TRUE));
            }
        }
    }

    public List<AbilityDef> defs() {
        return defs;
    }

    public AbilityDef byId(String id) {
        for (AbilityDef a : defs) if (a.id().equalsIgnoreCase(id)) return a;
        return null;
    }

    public List<AbilityDef> unlocked(PlayerData d) {
        List<AbilityDef> out = new ArrayList<>();
        for (AbilityDef a : defs) if (a.unlockLevel() <= d.level) out.add(a);
        return out;
    }

    public AbilityDef selected(Player p, PlayerData d) {
        List<AbilityDef> list = unlocked(d);
        if (list.isEmpty()) return null;
        String id = selected.get(p.getUniqueId());
        for (AbilityDef a : list) if (a.id().equals(id)) return a;
        AbilityDef first = list.get(0);
        selected.put(p.getUniqueId(), first.id());
        return first;
    }

    public long remaining(PlayerData d, AbilityDef a) {
        Long exp = d.cooldowns.get(a.id());
        return exp == null ? 0 : Math.max(0, exp - System.currentTimeMillis());
    }

    public void clearLife(UUID id) {
        usesThisLife.remove(id);
    }

    public void forget(UUID id) {
        selected.remove(id);
        usesThisLife.remove(id);
        lastUse.remove(id);
    }

    /** Right-click handler: sneak cycles the selected ability, otherwise activates it. */
    public void handleUse(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < 250) return; // double-fire / auto-click guard
        lastUse.put(p.getUniqueId(), now);
        PlayerData d = plugin.ruby().get(p);
        if (d == null) return;
        List<AbilityDef> list = unlocked(d);
        if (list.isEmpty()) {
            plugin.messages().send(p, "no-abilities");
            return;
        }
        if (p.isSneaking()) {
            AbilityDef cur = selected(p, d);
            int idx = list.indexOf(cur);
            AbilityDef next = list.get((idx + 1) % list.size());
            selected.put(p.getUniqueId(), next.id());
            plugin.messages().send(p, "ability-selected", "ability", next.name());
            p.playSound(p.getLocation(), "ui.button.click", 0.6f, 1.4f);
            return;
        }
        activate(p, d, selected(p, d));
    }

    public boolean activate(Player p, PlayerData d, AbilityDef a) {
        if (a == null) return false;
        if (!p.hasPermission("rubysteal.use") || !p.hasPermission("rubysteal.ability." + a.id())) {
            plugin.messages().send(p, "no-permission");
            return false;
        }
        if (plugin.getConfig().getStringList("abilities.disabled-worlds").contains(p.getWorld().getName())) {
            plugin.messages().send(p, "ability-world-disabled");
            return false;
        }
        if (a.blockInCombat() && plugin.abuse().inCombat(p) && !p.hasPermission("rubysteal.bypass.combat")) {
            plugin.messages().send(p, "ability-blocked-combat", "ability", a.name());
            return false;
        }
        long rem = remaining(d, a);
        if (rem > 0) {
            plugin.messages().send(p, "ability-cooldown", "ability", a.name(), "time", Text.time(rem));
            p.playSound(p.getLocation(), "block.note_block.bass", 0.6f, 0.7f);
            return false;
        }
        Map<String, Integer> uses = usesThisLife.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        if (a.usesPerLife() > 0 && uses.getOrDefault(a.id(), 0) >= a.usesPerLife()) {
            plugin.messages().send(p, "ability-no-uses", "ability", a.name(), "max", a.usesPerLife());
            return false;
        }
        boolean ok = apply(p, a);
        if (!ok) return false;
        if (a.usesPerLife() > 0) uses.merge(a.id(), 1, Integer::sum);
        if (a.cooldownSeconds() > 0) d.cooldowns.put(a.id(), System.currentTimeMillis() + a.cooldownSeconds() * 1000L);
        plugin.messages().send(p, "ability-used", "ability", a.name(), "cooldown", a.cooldownSeconds());
        if (!a.sound().isBlank()) p.getWorld().playSound(p.getLocation(), a.sound(), 1f, 1f);
        plugin.ruby().level(d.level).particle().play(p.getLocation(), plugin.getConfig().getInt("abilities.burst-multiplier", 8));
        plugin.ruby().saveAsync(d); // persists the cooldown immediately
        return true;
    }

    private boolean apply(Player p, AbilityDef a) {
        switch (a.type()) {
            case EFFECT, AWAKENING -> {
                for (EffectSpec e : a.effects()) addEffect(p, e);
                return true;
            }
            case AURA -> {
                if (a.includeSelf()) for (EffectSpec e : a.effects()) addEffect(p, e);
                Team team = p.getScoreboard().getEntryTeam(p.getName());
                int affected = 0;
                int cap = plugin.getConfig().getInt("abilities.aura-max-targets", 8);
                for (Player o : p.getWorld().getPlayers()) {
                    if (o == p || affected >= cap) continue;
                    if (o.getLocation().distanceSquared(p.getLocation()) > a.radius() * a.radius()) continue;
                    Team ot = o.getScoreboard().getEntryTeam(o.getName());
                    if (team == null || ot == null || !team.getName().equals(ot.getName())) continue;
                    for (EffectSpec e : a.effects()) addEffect(o, e);
                    plugin.messages().send(o, "aura-received", "player", p.getName(), "ability", a.name());
                    affected++;
                }
                return true;
            }
            case PULSE -> {
                Vector dir = p.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0, 0, 0);
                else dir.normalize().multiply(a.power());
                double max = plugin.getConfig().getDouble("abilities.max-pulse-velocity", 2.5);
                if (dir.length() > max) dir.normalize().multiply(max);
                dir.setY(Math.max(0, a.vertical()));
                p.setVelocity(dir);
                p.setFallDistance(0f);
                return true;
            }
            case HEAL -> {
                AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
                double max = attr == null ? 20.0 : attr.getValue();
                if (p.getHealth() >= max) {
                    plugin.messages().send(p, "heal-full");
                    return false;
                }
                p.setHealth(Math.min(max, p.getHealth() + Math.max(0, a.amount())));
                return true;
            }
        }
        return false;
    }

    private void addEffect(Player p, EffectSpec e) {
        p.addPotionEffect(new PotionEffect(e.type(), e.ticks(), e.amplifier(), true, true, true));
    }
                          }
                  
