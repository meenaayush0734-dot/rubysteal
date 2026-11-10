package dev.rubysteal.model;

import org.bukkit.potion.PotionEffectType;

import java.util.List;

public record AbilityDef(String id, String name, String description, Type type, int unlockLevel,
                         int cooldownSeconds, boolean blockInCombat, int usesPerLife, String sound,
                         List<EffectSpec> effects, double power, double vertical, double amount,
                         double radius, boolean includeSelf) {

    public enum Type { EFFECT, PULSE, HEAL, AURA, AWAKENING }

    public record EffectSpec(PotionEffectType type, int amplifier, int ticks) 
    {}
      }
