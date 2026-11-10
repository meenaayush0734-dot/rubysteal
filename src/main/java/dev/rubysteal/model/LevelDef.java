package dev.rubysteal.model;

import java.util.List;

public record LevelDef(int level, String name, int requiredKills, String color, String model,
                       List<String> lore, ParticleSpec particle) {
    /** MiniMessage-coloured name. */
    public String coloredName() {
        return "<" + color + ">" + name + "</" + color + ">";
    }

}
