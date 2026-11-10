package dev.rubysteal.model;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

public final class ParticleSpec {
    private final Particle type;
    private final Particle.DustOptions dust;
    private final int count;
    private final double offset;
    private final double speed;
    private final double yOffset;

    private ParticleSpec(Particle type, Particle.DustOptions dust, int count, double offset, double speed, double yOffset) {
        this.type = type;
        this.dust = dust;
        this.count = count;
        this.offset = offset;
        this.speed = speed;
        this.yOffset = yOffset;
    }

    public static ParticleSpec parse(ConfigurationSection s, Logger log, String where) {
        String name = s == null ? "DUST" : s.getString("type", "DUST");
        Particle p;
        try {
            p = Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warning("Unknown particle '" + name + "' at " + where + ", using DUST.");
            p = Particle.DUST;
        }
        Class<?> data = p.getDataType();
        if (p != Particle.DUST && data != Void.class) {
            log.warning("Particle " + p + " at " + where + " needs extra data and is unsupported, using DUST.");
            p = Particle.DUST;
        }
        Particle.DustOptions dust = null;
        if (p == Particle.DUST) {
            String hex = s == null ? "#B3162B" : s.getString("color", "#B3162B");
            Color c;
            try {
                c = Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
            } catch (Exception ex) {
                log.warning("Bad particle color '" + hex + "' at " + where);
                c = Color.fromRGB(0xB3162B);
            }
            dust = new Particle.DustOptions(c, (float) (s == null ? 1.0 : s.getDouble("size", 1.0)));
        }
        return new ParticleSpec(p, dust,
                s == null ? 3 : s.getInt("count", 3),
                s == null ? 0.35 : s.getDouble("offset", 0.35),
                s == null ? 0.0 : s.getDouble("speed", 0.0),
                s == null ? 1.0 : s.getDouble("y-offset", 1.0));
    }

    public void play(Location base, int multiplier) {
        if (base.getWorld() == null) return;
        Location l = base.clone().add(0, yOffset, 0);
        base.getWorld().spawnParticle(type, l, Math.max(1, count * multiplier), offset, offset, offset, speed, dust);
  
          }
  }
