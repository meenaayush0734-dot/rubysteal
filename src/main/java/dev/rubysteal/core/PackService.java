package dev.rubysteal.core;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Optional server resource pack delivery. Never invents a URL: nothing is sent unless the admin configured one. */
public final class PackService {
    private final RubyStealPlugin plugin;

    public PackService(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("resource-pack");
    }

    public boolean enabled() {
        ConfigurationSection c = cfg();
        return c != null && c.getBoolean("enabled", false) && !c.getString("url", "").isBlank();
    }

    public boolean required() {
        ConfigurationSection c = cfg();
        return c != null && c.getBoolean("required", false);
    }

    public void validateConfig() {
        ConfigurationSection c = cfg();
        if (c == null || !c.getBoolean("enabled", false)) return;
        if (c.getString("url", "").isBlank()) {
            plugin.getLogger().warning("resource-pack.enabled is true but resource-pack.url is empty - no pack will be sent.");
            return;
        }
        String sha = c.getString("sha1", "").trim();
        if (sha.isEmpty()) {
            plugin.getLogger().warning("resource-pack.sha1 is empty - clients cannot verify or cache the pack. Set it to the SHA-1 of the ZIP.");
        } else if (!sha.matches("(?i)[0-9a-f]{40}")) {
            plugin.getLogger().warning("resource-pack.sha1 must be 40 hexadecimal characters.");
        }
    }

    public void send(Player p) {
        if (!enabled()) return;
        ConfigurationSection c = cfg();
        String url = c.getString("url", "").trim();
        String sha = c.getString("sha1", "").trim();
        byte[] hash = null;
        if (sha.matches("(?i)[0-9a-f]{40}")) {
            hash = new byte[20];
            for (int i = 0; i < 20; i++) hash[i] = (byte) Integer.parseInt(sha.substring(i * 2, i * 2 + 2), 16);
        }
        // Stable per URL+hash so clients re-download only when the pack actually changes.
        UUID id = UUID.nameUUIDFromBytes(("rubysteal:" + url + ":" + sha.toLowerCase()).getBytes(StandardCharsets.UTF_8));
        String prompt = c.getString("prompt", "");
        p.setResourcePack(id, url, hash, prompt.isBlank() ? null : Text.raw(prompt), required());
      
    }
          }
