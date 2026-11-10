package dev.rubysteal.listener;

import dev.rubysteal.RubyStealPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Kicks players who refuse or fail a pack when resource-pack.required is true. */
public final class PackListener implements Listener {
    private final RubyStealPlugin plugin;

    public PackListener(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (!plugin.pack().enabled() || !plugin.pack().required()) return;
        if (e.getPlayer().hasPermission("rubysteal.bypass.pack")) return;
        switch (e.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD ->
                    e.getPlayer().kick(plugin.messages().get("pack-required-kick"));
            default -> { }
        }
    
    }
          }
