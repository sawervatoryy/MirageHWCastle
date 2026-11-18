package ru.miragestudio.hwcastle.config;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Описание эффекта (звук + частицы) из config.yml.
 * Звук задаётся ключом ("minecraft:entity.generic.explode"), частица — именем enum (EXPLOSION, CRIT...).
 */
public record FxSpec(String sound, float volume, float pitch, String particle, int count, double spread) {

    public static final FxSpec NONE = new FxSpec("", 1f, 1f, "", 0, 0.3);

    public static FxSpec read(ConfigurationSection s) {
        if (s == null) {
            return NONE;
        }
        return new FxSpec(
                s.getString("sound", ""),
                (float) s.getDouble("volume", 1.0),
                (float) s.getDouble("pitch", 1.0),
                s.getString("particle", ""),
                s.getInt("count", 0),
                s.getDouble("spread", 0.3));
    }

    /** Проигрывает эффект в точке (только главный поток). */
    public void play(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        if (!sound.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(sound.toLowerCase(Locale.ROOT));
            Sound snd = key == null ? null : Registry.SOUNDS.get(key);
            if (snd != null) {
                world.playSound(loc, snd, volume, pitch);
            }
        }
        if (!particle.isBlank() && count > 0) {
            try {
                Particle p = Particle.valueOf(particle.toUpperCase(Locale.ROOT));
                world.spawnParticle(p, loc, count, spread, spread, spread, 0.0);
            } catch (IllegalArgumentException ignored) {
                // неизвестная частица или частица, требующая данных — пропускаем
            }
        }
    }
}
