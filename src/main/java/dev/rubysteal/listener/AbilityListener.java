package dev.rubysteal.listener;

import dev.rubysteal.RubyStealPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class AbilityListener implements Listener {
    private final RubyStealPlugin plugin;

    public AbilityListener(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (!plugin.ruby().isRuby(item)) return;
        Player p = e.getPlayer();
        e.setUseItemInHand(Event.Result.DENY);
        // Do not fire abilities while the player is just opening a chest, door, etc.
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) return;
        plugin.abilities().handleUse(p);
    
    }
}
