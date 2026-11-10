package dev.rubysteal.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    /** MiniMessage without the default italic (for item names/lore). */
    public static Component mm(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    /** Plain MiniMessage (chat/titles). */
    public static Component raw(String s) {
        return MM.deserialize(s);
    }

    /** Replace {key} placeholders; kv = key, value, key, value ... */
    public static String fill(String s, Object... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public static String bar(double fraction, int length, String filled, String empty) {
        fraction = Math.max(0, Math.min(1, fraction));
        int on = (int) Math.round(fraction * length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(i < on ? filled : empty);
        return sb.toString();
    }

    public static String time(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        if (s >= 3600) return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    public static String ago(long ts) {
        long d = Math.max(0, System.currentTimeMillis() - ts) / 1000;
        if (d < 60) return d + "s ago";
        if (d < 3600) return (d / 60) + "m ago";
        if (d < 86400) return (d / 3600) + "h ago";
        return (d / 86400) + "d ago";
    }
}
