package dev.rubysteal.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerData {
    public final UUID uuid;
    public volatile String name;
    public int level;
    public int xp;
    public int kills;
    public int deaths;
    public String serial;
    public long created;
    public long lastSeen;
    public final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    /** Independent copy that is safe to hand to the database thread. */
    public PlayerData copy() {
        PlayerData c = new PlayerData(uuid, name);
        c.level = level;
        c.xp = xp;
        c.kills = kills;
        c.deaths = deaths;
        c.serial = serial;
        c.created = created;
        c.lastSeen = lastSeen;
        c.cooldowns.putAll(cooldowns);
        return c;
    
    }
  }
