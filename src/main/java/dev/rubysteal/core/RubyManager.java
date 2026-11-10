package dev.rubysteal.core;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.model.AbilityDef;
import dev.rubysteal.model.LevelDef;
import dev.rubysteal.model.ParticleSpec;
import dev.rubysteal.model.PlayerData;
import dev.rubysteal.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player data cache, levels, Ruby items, evolution and item protection scans. */
public final class RubyManager {
    private final RubyStealPlugin plugin;
    private final NamespacedKey keyOwner;
    private final NamespacedKey keyLevel;
    private final NamespacedKey keySerial;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> firstJoin = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> fullInventoryNotice = new ConcurrentHashMap<>();
    private volatile List<LevelDef> levels = List.of();
    private volatile List<PlayerData> leaderboard = List.of();

    public RubyManager(RubyStealPlugin plugin) {
        this.plugin = plugin;
        this.keyOwner = new NamespacedKey(plugin, "ruby_owner");
        this.keyLevel = new NamespacedKey(plugin, "ruby_level");
        this.keySerial = new NamespacedKey(plugin, "ruby_serial");
    }

    // ------------------------------------------------------------------ config

    public void reloadLevels() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("levels");
        List<LevelDef> out = new ArrayList<>();
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection l = sec.getConfigurationSection(key);
                if (l == null) continue;
                int lvl;
                try {
                    lvl = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Ignoring level with non-numeric key '" + key + "'");
                    continue;
                }
                out.add(new LevelDef(lvl, l.getString("name", "Ruby " + lvl), l.getInt("required-kills", 0),
                        l.getString("color", "red"), l.getString("model", "ruby_level_" + lvl),
                        l.getStringList("lore"),
                        ParticleSpec.parse(l.getConfigurationSection("particle"), plugin.getLogger(), "levels." + key)));
            }
        }
        out.sort((a, b) -> Integer.compare(a.level(), b.level()));
        if (out.isEmpty()) throw new IllegalStateException("config.yml has no levels defined");
        levels = List.copyOf(out);
    }

    public List<LevelDef> levels() {
        return levels;
    }

    public int maxLevel() {
        return levels.get(levels.size() - 1).level();
    }

    public LevelDef level(int n) {
        List<LevelDef> ls = levels;
        LevelDef best = ls.get(0);
        for (LevelDef l : ls) if (l.level() <= n) best = l;
        return best;
    }

    public LevelDef next(int n) {
        for (LevelDef l : levels) if (l.level() > n) return l;
        return null;
    }

    public int levelFor(int xp) {
        int lv = levels.get(0).level();
        for (LevelDef l : levels) if (xp >= l.requiredKills()) lv = l.level();
        return lv;
    }

    public int startingLevel() {
        int s = plugin.getConfig().getInt("starting-level", 1);
        return Math.max(levels.get(0).level(), Math.min(maxLevel(), s));
    }

    public double progress(PlayerData d) {
        LevelDef cur = level(d.level);
        LevelDef nxt = next(d.level);
        if (nxt == null) return 1.0;
        double span = nxt.requiredKills() - cur.requiredKills();
        return span <= 0 ? 1.0 : Math.max(0, Math.min(1, (d.xp - cur.requiredKills()) / span));
    }

    // -------------------------------------------------------------- data cache

    public PlayerData cached(UUID id) {
        return cache.get(id);
    }

    public void put(PlayerData d) {
        cache.put(d.uuid, d);
    }

    public void remove(UUID id) {
        cache.remove(id);
        firstJoin.remove(id);
        fullInventoryNotice.remove(id);
    }

    public Iterable<PlayerData> allCached() {
        return cache.values();
    }

    public PlayerData get(Player p) {
        PlayerData d = cache.get(p.getUniqueId());
        if (d == null) {
            try {
                d = plugin.db().load(p.getUniqueId()).join();
            } catch (Exception e) {
                plugin.getLogger().severe("Could not load data for " + p.getName() + ": " + e.getMessage());
                return null;
            }
            if (d == null) d = createNew(p.getUniqueId(), p.getName());
            cache.put(d.uuid, d);
        }
        return d;
    }

    public PlayerData createNew(UUID id, String name) {
        PlayerData d = new PlayerData(id, name);
        d.level = startingLevel();
        d.xp = level(d.level).requiredKills();
        d.created = System.currentTimeMillis();
        d.lastSeen = d.created;
        firstJoin.add(id);
        return d;
    }

    public boolean consumeFirstJoin(UUID id) {
        return firstJoin.remove(id);
    }

    public void saveAsync(PlayerData d) {
        d.lastSeen = System.currentTimeMillis();
        plugin.db().save(d.copy());
    }

    public List<PlayerData> leaderboard() {
        return leaderboard;
    }

    public void refreshLeaderboard() {
        int size = Math.max(10, plugin.getConfig().getInt("leaderboard.size", 10));
        plugin.db().top(size).thenAccept(list -> leaderboard = List.copyOf(list));
    }

    // ------------------------------------------------------------------- items

    private Material baseMaterial() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("item.material", "ECHO_SHARD"));
        return m == null || !m.isItem() ? Material.ECHO_SHARD : m;
    }

    public NamespacedKey modelKey(String model) {
        return new NamespacedKey(plugin.getConfig().getString("item.model-namespace", "rubysteal"), model);
    }

    public ItemStack createRuby(PlayerData d) {
        LevelDef lv = level(d.level);
        ItemStack it = new ItemStack(baseMaterial());
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.mm(Text.fill(plugin.getConfig().getString("item.name-format", "{name}"),
                "name", lv.coloredName(), "level", lv.level())));
        List<Component> lore = new ArrayList<>();
        for (String line : lv.lore()) {
            lore.add(Text.mm(Text.fill(line, "level", lv.level(), "name", lv.name(), "owner", d.name,
                    "kills", d.kills, "xp", d.xp, "max", maxLevel())));
        }
        m.lore(lore);
        m.setItemModel(modelKey(lv.model()));
        m.setMaxStackSize(1);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        int glint = plugin.getConfig().getInt("item.glint-from-level", 8);
        m.setEnchantmentGlintOverride(lv.level() >= glint);
        var pdc = m.getPersistentDataContainer();
        pdc.set(keyOwner, PersistentDataType.STRING, d.uuid.toString());
        pdc.set(keyLevel, PersistentDataType.INTEGER, d.level);
        pdc.set(keySerial, PersistentDataType.STRING, d.serial == null ? "" : d.serial);
        it.setItemMeta(m);
        return it;
    }

    /** Pure display icon (GUI) using one of the pack models. */
    public ItemStack icon(String model, Material fallback, String name, List<String> lore) {
        ItemStack it = new ItemStack(model == null ? fallback : baseMaterial());
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.mm(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Text.mm(s));
        m.lore(l);
        if (model != null) m.setItemModel(modelKey(model));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    public boolean isRuby(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(keySerial, PersistentDataType.STRING);
    }

    private String owner(ItemStack it) {
        return it.getItemMeta().getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING);
    }

    private String serial(ItemStack it) {
        return it.getItemMeta().getPersistentDataContainer().get(keySerial, PersistentDataType.STRING);
    }

    private int itemLevel(ItemStack it) {
        Integer v = it.getItemMeta().getPersistentDataContainer().get(keyLevel, PersistentDataType.INTEGER);
        return v == null ? -1 : v;
    }

    public boolean holdsRuby(Player p) {
        return isRuby(p.getInventory().getItemInMainHand()) || isRuby(p.getInventory().getItemInOffHand());
    }

    // ------------------------------------------------------- protection scans

    private void flagRemoval(Player p, PlayerData d, String reason, ItemStack it) {
        String owner = owner(it);
        String detail = reason + " (owner=" + owner + ")";
        plugin.getLogger().warning("Removed Ruby item from " + p.getName() + ": " + detail);
        plugin.db().addHistory(d.uuid, "ITEM_REMOVED", detail);
        if (plugin.getConfig().getBoolean("protection.alert-admins", true)) {
            for (Player a : Bukkit.getOnlinePlayers()) {
                if (a.hasPermission("rubysteal.alerts")) {
                    plugin.messages().send(a, "protection-alert", "player", p.getName(), "reason", reason);
                }
            }
        }
    }

    /**
     * Makes sure the player carries exactly one valid Ruby of their current level:
     * foreign, stale-serial, duplicate and ender-chest copies are removed; a missing one is issued.
     */
    public void validate(Player p) {
        PlayerData d = get(p);
        if (d == null || !p.isOnline()) return;
        boolean bypass = p.hasPermission("rubysteal.bypass.dupe");
        boolean found = false;
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (!isRuby(s)) continue;
            String reason = verdict(p, d, s, found, bypass);
            if (reason == null) {
                found = true;
                if (s.getAmount() != 1 || itemLevel(s) != d.level) inv.setItem(i, createRuby(d));
            } else if (!reason.equals("KEEP")) {
                flagRemoval(p, d, reason, s);
                inv.setItem(i, null);
            }
        }
        ItemStack cursor = p.getItemOnCursor();
        if (isRuby(cursor)) {
            String reason = verdict(p, d, cursor, found, bypass);
            if (reason == null) found = true;
            else if (!reason.equals("KEEP")) {
                flagRemoval(p, d, reason, cursor);
                p.setItemOnCursor(null);
            }
        }
        Inventory ender = p.getEnderChest();
        for (int i = 0; i < ender.getSize(); i++) {
            ItemStack s = ender.getItem(i);
            if (isRuby(s) && !bypass) {
                flagRemoval(p, d, "found in ender chest", s);
                ender.setItem(i, null);
            }
        }
        if (!found) issue(p, d);
    }

    /** null = valid, "KEEP" = foreign but bypassed, otherwise the removal reason. */
    private String verdict(Player p, PlayerData d, ItemStack s, boolean alreadyFound, boolean bypass) {
        String owner = owner(s);
        if (owner == null || !owner.equals(d.uuid.toString())) return bypass ? "KEEP" : "foreign owner";
        String ser = serial(s);
        if (ser == null || d.serial == null || !ser.equals(d.serial)) return bypass ? "KEEP" : "stale serial";
        if (alreadyFound) return bypass ? "KEEP" : "duplicate copy";
        return null;
    }

    private void issue(Player p, PlayerData d) {
        String old = d.serial;
        d.serial = UUID.randomUUID().toString();
        ItemStack ruby = createRuby(d);
        if (!p.getInventory().addItem(ruby).isEmpty()) {
            d.serial = old;
            long now = System.currentTimeMillis();
            Long last = fullInventoryNotice.get(d.uuid);
            if (last == null || now - last > 30_000) {
                fullInventoryNotice.put(d.uuid, now);
                plugin.messages().send(p, "inventory-full");
            }
            return;
        }
        saveAsync(d);
        plugin.db().addHistory(d.uuid, "RUBY_ISSUED", level(d.level).name());
        plugin.messages().send(p, "ruby-given", "ruby", level(d.level).coloredName());
    }

    // --------------------------------------------------------------- evolution

    public void addKill(Player killer) {
        PlayerData d = get(killer);
        if (d == null) return;
        d.xp++;
        d.kills++;
        int newLevel = levelFor(d.xp);
        if (newLevel > d.level) {
            int old = d.level;
            d.level = newLevel;
            plugin.db().addHistory(d.uuid, "EVOLVED", level(old).name() + " -> " + level(newLevel).name());
            evolveEffects(killer, d, old);
            validate(killer);
        } else {
            LevelDef nxt = next(d.level);
            plugin.messages().send(killer, "xp-gain", "xp", d.xp,
                    "remaining", nxt == null ? 0 : nxt.requiredKills() - d.xp);
        }
        saveAsync(d);
        refreshLeaderboard();
    }

    private void evolveEffects(Player p, PlayerData d, int oldLevel) {
        LevelDef lv = level(d.level);
        var cfg = plugin.getConfig();
        if (cfg.getBoolean("evolution.title", true)) {
            p.showTitle(Title.title(
                    Text.raw(Text.fill(cfg.getString("messages.evolution-title", "{ruby}"), "ruby", lv.coloredName())),
                    Text.raw(Text.fill(cfg.getString("messages.evolution-subtitle", ""), "level", lv.level())),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        }
        String sound = cfg.getString("evolution.sound", "entity.player.levelup");
        if (!sound.isBlank()) p.playSound(p.getLocation(), sound, 1f, 1f);
        lv.particle().play(p.getLocation(), cfg.getInt("evolution.particle-multiplier", 12));
        plugin.messages().send(p, "evolved", "ruby", lv.coloredName(), "level", lv.level());
        List<String> unlocked = new ArrayList<>();
        for (AbilityDef a : plugin.abilities().defs()) {
            if (a.unlockLevel() > oldLevel && a.unlockLevel() <= lv.level()) unlocked.add(a.name());
        }
        if (!unlocked.isEmpty()) plugin.messages().send(p, "ability-unlocked", "abilities", String.join(", ", unlocked));
        if (cfg.getBoolean("evolution.broadcast", true) && lv.level() >= cfg.getInt("evolution.broadcast-min-level", 3)) {
            Component msg = plugin.messages().get("evolution-broadcast", "player", p.getName(), "ruby", lv.coloredName(),
                    "level", lv.level());
            for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.sendMessage(msg);
        }
    }

    // ------------------------------------------------------------- admin tools

    /** Sets level (and the XP threshold of that level). Kills/deaths stay untouched. */
    public void applyLevel(PlayerData d, int level) {
        LevelDef lv = level(level);
        d.level = lv.level();
        d.xp = lv.requiredKills();
        plugin.db().addHistory(d.uuid, "SET_LEVEL", lv.name());
    }

    public void applyReset(PlayerData d) {
        d.level = startingLevel();
        d.xp = level(d.level).requiredKills();
        d.kills = 0;
        d.deaths = 0;
        d.cooldowns.clear();
        d.serial = UUID.randomUUID().toString();
        plugin.db().addHistory(d.uuid, "RESET", "progress reset by admin");
    }

    public void shutdownSave() {
        for (PlayerData d : cache.values()) {
            d.lastSeen = System.currentTimeMillis();
            plugin.db().save(d.copy());
        }
    }
                                     }
                               
