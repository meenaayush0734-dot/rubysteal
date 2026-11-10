package dev.rubysteal.listener;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.gui.RubyGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Keeps Ruby items soulbound: no dropping, storing, trading, crafting, placing or duplicating. */
public final class ProtectionListener implements Listener {
    private final RubyStealPlugin plugin;

    public ProtectionListener(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ruby(ItemStack it) {
        return plugin.ruby().isRuby(it);
    }

    private boolean bypass(Player p) {
        return p.hasPermission("rubysteal.bypass.dupe");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (ruby(e.getItemDrop().getItemStack()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            plugin.messages().send(e.getPlayer(), "ruby-locked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (ruby(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (ruby(e.getEntity().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (ruby(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (ruby(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack held = e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        if (ruby(held) && !bypass(p)) e.setCancelled(true); // item frames, armor stands, trading, name tags...
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof RubyGui) return; // GuiListener handles the menu
        if (!(e.getWhoClicked() instanceof Player p) || bypass(p)) return;

        ItemStack current = e.getCurrentItem();
        boolean involved = ruby(current) || ruby(e.getCursor());
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
            involved |= ruby(p.getInventory().getItem(e.getHotbarButton()));
        }
        if (e.getClick() == ClickType.SWAP_OFFHAND) involved |= ruby(p.getInventory().getItemInOffHand());
        if (!involved) return;

        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        boolean shiftToTop = e.isShiftClick() && top.getType() != InventoryType.CRAFTING;
        if (clickedTop || shiftToTop) {
            e.setCancelled(true);
            plugin.messages().send(p, "ruby-locked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof RubyGui) return;
        if (!(e.getWhoClicked() instanceof Player p) || bypass(p)) return;
        if (!ruby(e.getOldCursor())) return;
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                e.setCancelled(true);
                plugin.messages().send(p, "ruby-locked");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || bypass(p)) return;
        if (ruby(e.getCursor())) e.setCancelled(true); // blocks middle-click cloning
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) plugin.ruby().validate(p);
   
  });
    }
  }
