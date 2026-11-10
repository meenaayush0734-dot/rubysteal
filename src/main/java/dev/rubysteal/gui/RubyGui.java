package dev.rubysteal.gui;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.core.RubyManager;
import dev.rubysteal.db.Database.HistoryEntry;
import dev.rubysteal.model.AbilityDef;
import dev.rubysteal.model.LevelDef;
import dev.rubysteal.model.PlayerData;
import dev.rubysteal.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 54-slot dark/crimson Ruby menu. Read-only: every click is cancelled by GuiListener. */
public final class RubyGui implements InventoryHolder {
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_REFRESH = 51;

    private final Inventory inv;

    public RubyGui(RubyStealPlugin plugin, Player viewer, PlayerData d, List<HistoryEntry> history) {
        ConfigurationSection g = plugin.getConfig().getConfigurationSection("gui");
        RubyManager rm = plugin.ruby();
        LevelDef cur = rm.level(d.level);
        LevelDef nxt = rm.next(d.level);
        this.inv = Bukkit.createInventory(this, 54, Text.raw(g == null ? "<dark_red>Ruby" : g.getString("title", "<dark_red>Ruby")));

        Material bg = mat(g, "background-material", Material.BLACK_STAINED_GLASS_PANE);
        Material border = mat(g, "border-material", Material.RED_STAINED_GLASS_PANE);
        Material barOn = mat(g, "bar-filled-material", Material.RED_STAINED_GLASS_PANE);
        Material barOff = mat(g, "bar-empty-material", Material.GRAY_STAINED_GLASS_PANE);
        Material locked = mat(g, "locked-material", Material.GRAY_DYE);

        ItemStack filler = rm.icon(null, bg, " ", List.of());
        ItemStack edge = rm.icon(null, border, " ", List.of());
        for (int i = 0; i < 54; i++) {
            boolean isEdge = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            inv.setItem(i, isEdge ? edge : filler);
        }

        // Current ruby
        inv.setItem(13, rm.icon(cur.model(), Material.ECHO_SHARD, cur.coloredName(), List.of(
                "<gray>Level: <white>" + cur.level() + "<dark_gray>/<white>" + rm.maxLevel(),
                "<gray>Ruby XP: <red>" + d.xp,
                "<gray>Total kills: <red>" + d.kills,
                "",
                "<dark_gray>Right-click the Ruby to use an ability,",
                "<dark_gray>sneak + right-click to switch ability.")));

        // Stats
        inv.setItem(20, rm.icon("ruby_fragment", Material.ECHO_SHARD, "<red><bold>Ruby Stats", List.of(
                "<gray>Ruby XP: <white>" + d.xp,
                "<gray>Total kills: <white>" + d.kills,
                "<gray>Deaths: <white>" + d.deaths,
                "<gray>K/D: <white>" + String.format(java.util.Locale.ROOT, "%.2f", d.deaths == 0 ? d.kills : (double) d.kills / d.deaths))));

        // Next evolution
        List<String> next = new ArrayList<>();
        if (nxt == null) {
            next.add("<gold>You hold the final Ruby.");
            next.add("<gray>Every kill still counts for the leaderboard.");
        } else {
            next.add("<gray>Next: " + nxt.coloredName());
            next.add("<gray>Required kills: <white>" + nxt.requiredKills());
            next.add("<gray>Remaining: <red>" + Math.max(0, nxt.requiredKills() - d.xp));
            for (AbilityDef a : plugin.abilities().defs()) {
                if (a.unlockLevel() == nxt.level()) next.add("<gray>Unlocks: <light_purple>" + a.name());
            }
        }
        inv.setItem(22, rm.icon("ruby_heart", Material.ECHO_SHARD, "<red><bold>Next Evolution", next));

        // History
        List<String> hist = new ArrayList<>();
        if (history.isEmpty()) hist.add("<gray>No history yet.");
        for (HistoryEntry h : history) {
            hist.add("<dark_gray>" + Text.ago(h.timestamp()) + " <gray>" + h.event().toLowerCase().replace('_', ' ') + ": <white>" + h.detail());
        }
        inv.setItem(24, rm.icon(null, Material.BOOK, "<red><bold>Ruby History", hist));

        // Abilities (one row, up to 9)
        List<AbilityDef> defs = plugin.abilities().defs();
        for (int i = 0; i < Math.min(9, defs.size()); i++) {
            AbilityDef a = defs.get(i);
            boolean open = a.unlockLevel() <= d.level;
            List<String> lore = new ArrayList<>();
            if (open) {
                lore.add("<gray>" + a.description());
                lore.add("<gray>Cooldown: <white>" + a.cooldownSeconds() + "s");
                long rem = plugin.abilities().remaining(d, a);
                lore.add(rem > 0 ? "<red>Ready in " + Text.time(rem) : "<green>Ready");
                inv.setItem(27 + i, rm.icon("enchanted_ruby", Material.ECHO_SHARD, "<light_purple>" + a.name(), lore));
            } else {
                lore.add("<dark_gray>Unlocks at level " + a.unlockLevel() + " (" + rm.level(a.unlockLevel()).name() + ")");
                inv.setItem(27 + i, rm.icon(null, locked, "<dark_gray>" + a.name() + " <red>(locked)", lore));
            }
        }

        // Progress bar (row 5, slots 36-44)
        double prog = rm.progress(d);
        int filled = (int) Math.round(prog * 9);
        String pct = (int) Math.round(prog * 100) + "%";
        for (int i = 0; i < 9; i++) {
            Material m = i < filled ? barOn : barOff;
            inv.setItem(36 + i, rm.icon(null, m, "<red>Evolution Progress: <white>" + pct,
                    List.of(nxt == null ? "<gold>Maximum level reached" : "<gray>" + d.xp + " / " + nxt.requiredKills() + " kills")));
        }

        // Leaderboard
        List<String> lb = new ArrayList<>();
        List<PlayerData> top = rm.leaderboard();
        if (top.isEmpty()) lb.add("<gray>Leaderboard is loading...");
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            PlayerData t = top.get(i);
            String me = t.uuid.equals(d.uuid) ? "<yellow>" : "<white>";
            lb.add("<gold>#" + (i + 1) + " " + me + t.name + " <gray>- " + rm.level(t.level).coloredName() + " <dark_gray>(" + t.xp + ")");
        }
        lb.add("");
        lb.add("<dark_gray>Use /ruby top for the full list.");
        inv.setItem(47, rm.icon("ruby_level_10", Material.ECHO_SHARD, "<gold><bold>Leaderboard", lb));

        inv.setItem(SLOT_CLOSE, rm.icon(null, Material.BARRIER, "<red>Close", List.of()));
        inv.setItem(SLOT_REFRESH, rm.icon(null, Material.CLOCK, "<red>Refresh", List.of("<gray>Reload this menu")));
    }

    private static Material mat(ConfigurationSection g, String key, Material def) {
        if (g == null) return def;
        Material m = Material.matchMaterial(g.getString(key, def.name()));
        return m == null ? def : m;
    }

    @Override
    public Inventory getInventory() {
        ret
                  return inv;
    }
  }
