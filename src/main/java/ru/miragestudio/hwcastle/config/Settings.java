package ru.miragestudio.hwcastle.config;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Неизменяемый снимок config.yml. Создаётся при старте и при /hwcastle reload,
 * после чего подменяется целиком (volatile-ссылка в плагине) — потокобезопасно.
 */
public record Settings(CastleSettings castle,
                       SpawnSettings spawn,
                       HitSettings hit,
                       HologramSettings hologram,
                       ExplosionSettings failExplosion,
                       EventSettings event,
                       LootSettings loot) {

    public record CastleSettings(String world, int centerX, int centerZ, int radius, int minY, int maxY,
                                 boolean denyBuild, boolean forcePvp, List<String> blockedCommands,
                                 String schematicFile, int pasteY, boolean ignoreAir, int blocksPerTick) {

        /** Находится ли точка в зоне замка. */
        public boolean contains(Location l) {
            World w = l.getWorld();
            if (w == null || !w.getName().equalsIgnoreCase(world)) {
                return false;
            }
            return Math.abs(l.getBlockX() - centerX) <= radius
                    && Math.abs(l.getBlockZ() - centerZ) <= radius
                    && l.getBlockY() >= minY && l.getBlockY() <= maxY;
        }
    }

    public record SpawnSettings(boolean enabled, int minInterval, int maxInterval, int perCycle,
                                boolean initialFill, boolean spawnOnBind, int bindReach) {
    }

    public record HitSettings(boolean clickMode, long cooldownMs, FxSpec hitFx, FxSpec breakFx, FxSpec spawnFx) {
    }

    public record HologramSettings(boolean enabled, double yOffset) {
    }

    public record ExplosionSettings(double radius, double damage, FxSpec fx) {
    }

    public record BarSettings(boolean enabled, BossBar.Color color, BossBar.Overlay overlay) {
    }

    public record TitleSettings(boolean enabled, int fadeIn, int stay, int fadeOut) {
    }

    public record EventSettings(boolean enabled, int durationSeconds, int hits, double failChance,
                                double lootMultiplier, int spawnInterval, boolean fillAll,
                                ExplosionSettings explosion, BarSettings bar, TitleSettings title,
                                FxSpec startFx, FxSpec endFx, FxSpec hitFx, FxSpec breakFx,
                                List<Integer> warnAt, boolean scheduleEnabled, List<LocalTime> times,
                                List<DayOfWeek> days, ZoneId zone,
                                List<String> startCommands, List<String> endCommands) {
    }

    public record LootSettings(int defaultChance) {
    }

    public static Settings from(ConfigurationSection c) {
        ConfigurationSection castle = sec(c, "castle");
        ConfigurationSection prot = sec(castle, "protection");
        ConfigurationSection schem = sec(castle, "schematic");
        CastleSettings cs = new CastleSettings(
                castle.getString("world", "world"),
                castle.getInt("center-x", 1000),
                castle.getInt("center-z", 1000),
                Math.max(1, castle.getInt("radius", 70)),
                castle.getInt("min-y", -64),
                castle.getInt("max-y", 320),
                prot.getBoolean("deny-build", true),
                prot.getBoolean("force-pvp", true),
                prot.getStringList("blocked-commands").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList(),
                schem.getString("file", "zamok.schem"),
                schem.getInt("paste-y", 64),
                schem.getBoolean("ignore-air", true),
                Math.max(100, schem.getInt("blocks-per-tick", 4000)));

        ConfigurationSection sp = sec(c, "spawn");
        int min = Math.max(1, sp.getInt("min-interval-seconds", 60));
        int max = Math.max(min, sp.getInt("max-interval-seconds", 90));
        SpawnSettings ss = new SpawnSettings(
                sp.getBoolean("enabled", true), min, max,
                Math.max(0, sp.getInt("per-cycle", 3)),
                sp.getBoolean("initial-fill", true),
                sp.getBoolean("spawn-on-bind", true),
                Math.max(2, sp.getInt("bind-reach", 8)));

        ConfigurationSection h = sec(c, "hits");
        HitSettings hs = new HitSettings(
                !"BREAK".equalsIgnoreCase(h.getString("mode", "CLICK")),
                Math.max(0, h.getLong("cooldown-ms", 350)),
                FxSpec.read(sec(h, "hit-fx")),
                FxSpec.read(sec(h, "break-fx")),
                FxSpec.read(sec(h, "spawn-fx")));

        ConfigurationSection hg = sec(c, "hologram");
        HologramSettings hos = new HologramSettings(hg.getBoolean("enabled", true), hg.getDouble("y-offset", 0.4));

        ExplosionSettings fail = explosion(sec(c, "fail-explosion"));

        ConfigurationSection ev = sec(c, "event");
        ConfigurationSection bar = sec(ev, "bossbar");
        ConfigurationSection title = sec(ev, "title");
        ConfigurationSection sched = sec(ev, "schedule");
        List<Integer> warn = new ArrayList<>(ev.getIntegerList("warn-at-seconds"));
        List<LocalTime> times = new ArrayList<>();
        for (String t : sched.getStringList("times")) {
            try {
                times.add(LocalTime.parse(t.trim()));
            } catch (DateTimeParseException ignored) {
                // некорректное время расписания пропускаем
            }
        }
        List<DayOfWeek> days = new ArrayList<>();
        for (String d : sched.getStringList("days")) {
            try {
                days.add(DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // некорректный день пропускаем
            }
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(sched.getString("timezone", "Europe/Moscow"));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        EventSettings es = new EventSettings(
                ev.getBoolean("enabled", true),
                Math.max(10, ev.getInt("duration-seconds", 900)),
                Math.max(1, ev.getInt("hits-to-break", 16)),
                clamp(ev.getDouble("fail-chance", 40.0)),
                Math.max(0.1, ev.getDouble("loot-multiplier", 2.0)),
                Math.max(1, ev.getInt("spawn-interval-seconds", 15)),
                ev.getBoolean("fill-all-points", true),
                explosion(sec(ev, "explosion")),
                new BarSettings(bar.getBoolean("enabled", true),
                        enumOf(BossBar.Color.class, bar.getString("color", "GREEN"), BossBar.Color.GREEN),
                        enumOf(BossBar.Overlay.class, bar.getString("style", "PROGRESS"), BossBar.Overlay.PROGRESS)),
                new TitleSettings(title.getBoolean("enabled", true), title.getInt("fade-in", 10),
                        title.getInt("stay", 60), title.getInt("fade-out", 20)),
                FxSpec.read(sec(ev, "start-fx")),
                FxSpec.read(sec(ev, "end-fx")),
                FxSpec.read(sec(ev, "hit-fx")),
                FxSpec.read(sec(ev, "break-fx")),
                warn,
                sched.getBoolean("enabled", false),
                times, days, zone,
                ev.getStringList("commands-on-start"),
                ev.getStringList("commands-on-end"));

        LootSettings ls = new LootSettings(Math.max(1, Math.min(100, sec(c, "loot").getInt("default-chance", 50))));
        return new Settings(cs, ss, hs, hos, fail, es, ls);
    }

    private static ExplosionSettings explosion(ConfigurationSection s) {
        return new ExplosionSettings(Math.max(0.5, s.getDouble("radius", 4.0)),
                Math.max(0.0, s.getDouble("damage", 6.0)), FxSpec.read(sec(s, "fx")));
    }

    private static ConfigurationSection sec(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        return s != null ? s : new MemoryConfiguration();
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
