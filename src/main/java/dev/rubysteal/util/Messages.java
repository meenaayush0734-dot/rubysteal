package dev.rubysteal.util;

import dev.rubysteal.RubyStealPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public final class Messages {
    private final RubyStealPlugin plugin;

    public Messages(RubyStealPlugin plugin) {
        this.plugin = plugin;
    }

    public String raw(String key) {
        String s = plugin.getConfig().getString("messages." + key);
        return s == null ? "<red>Missing message key: " + key : s;
    }

    public String text(String key, Object... kv) {
        String s = Text.fill(raw(key), kv);
        return s.replace("{prefix}", plugin.getConfig().getString("messages.prefix", ""));
    }

    public Component get(String key, Object... kv) {
        return Text.raw(text(key, kv));
    }

    public void send(CommandSender to, String key, Object... kv) {
        String t = text(key, kv);
        if (!t.isBlank()) to.sendMessage(Text.raw(t));
 
    }
    }
