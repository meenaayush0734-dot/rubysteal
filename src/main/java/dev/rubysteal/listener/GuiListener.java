package dev.rubysteal.listener;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.gui.RubyGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public final class GuiListener implements Listener {
    private final RubyStealPlugin plugin;

    public GuiListener(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof RubyGui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof RubyGui)) return;
        if (e.getRawSlot() == RubyGui.SLOT_CLOSE) {
            p.closeInventory();
        } else if (e.getRawSlot() == RubyGui.SLOT_REFRESH) {
            plugin.openGui(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof RubyGui) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof RubyGui || e.getSource().getHolder() instanceof RubyGui) {
            e.setCancelled(true);
        }
  
    }
}
