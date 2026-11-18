package ru.miragestudio.hwcastle.config;

import org.bukkit.DyeColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.miragestudio.hwcastle.MirageHWCastle;
import ru.miragestudio.hwcastle.manager.LevelRegistry;
import ru.miragestudio.hwcastle.model.LootEntry;
import ru.miragestudio.hwcastle.model.ShulkerLevel;
import ru.miragestudio.hwcastle.model.ShulkerPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Хранилище уровней (levels.yml) и привязанных точек (points.yml).
 * Чтение выполняется один раз при старте, запись — всегда асинхронно в отдельном потоке:
 * снимок YAML строится в главном потоке (быстро), на диск пишет IO-поток.
 */
public final class DataStore {

    private final MirageHWCastle plugin;
    private final Path levelsFile;
    private final Path pointsFile;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MirageHWCastle-IO");
        t.setDaemon(true);
        return t;
    });

    public DataStore(MirageHWCastle plugin) {
        this.plugin = plugin;
        this.levelsFile = plugin.getDataFolder().toPath().resolve("levels.yml");
        this.pointsFile = plugin.getDataFolder().toPath().resolve("points.yml");
    }

    // ---------------------------------------------------------------- загрузка (старт сервера)

    public void loadLevels(LevelRegistry registry) {
        registry.clear();
        boolean fresh = !Files.exists(levelsFile);
        if (!fresh) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(levelsFile.toFile());
            var root = y.getConfigurationSection("levels");
            if (root != null) {
                for (String id : root.getKeys(false)) {
                    var s = root.getConfigurationSection(id);
                    if (s == null) {
                        continue;
                    }
                    DyeColor dye;
                    try {
                        dye = DyeColor.valueOf(s.getString("dye", "GRAY").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        dye = DyeColor.GRAY;
                    }
                    ShulkerLevel level = new ShulkerLevel(id.toLowerCase(Locale.ROOT), s.getString("name", id), dye,
                            Math.max(1, s.getInt("hits", 5)), Math.max(0, s.getInt("weight", 10)),
                            s.getDouble("fail-chance", 0), s.getInt("min-items", 0), s.getInt("max-items", 0),
                            s.getBoolean("event-only", false));
                    level.setLoot(readLoot(s.getMapList("loot")));
                    registry.put(level);
                }
            }
        }
        if (fresh) {
            registry.put(DefaultLevels.common());
            registry.put(DefaultLevels.rare());
            registry.put(DefaultLevels.epic());
        }
        if (registry.emerald() == null) {
            registry.put(DefaultLevels.emerald());
        }
        if (fresh) {
            saveLevelsAsync(registry.all());
        }
    }

    public List<ShulkerPoint> loadPoints() {
        List<ShulkerPoint> result = new ArrayList<>();
        if (!Files.exists(pointsFile)) {
            return result;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(pointsFile.toFile());
        for (Map<?, ?> m : y.getMapList("points")) {
            try {
                String level = m.get("level") == null ? null : String.valueOf(m.get("level"));
                result.add(new ShulkerPoint(String.valueOf(m.get("world")), num(m.get("x")), num(m.get("y")),
                        num(m.get("z")), level, String.valueOf(m.get("base"))));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("points.yml: пропущена повреждённая запись " + m);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- сохранение (асинхронно)

    public void saveLevelsAsync(Collection<ShulkerLevel> levels) {
        YamlConfiguration y = new YamlConfiguration();
        for (ShulkerLevel l : levels) {
            String p = "levels." + l.id() + ".";
            y.set(p + "name", l.displayName());
            y.set(p + "dye", l.dye().name());
            y.set(p + "hits", l.hits());
            y.set(p + "weight", l.weight());
            y.set(p + "fail-chance", l.failChance());
            y.set(p + "min-items", l.minItems());
            y.set(p + "max-items", l.maxItems());
            y.set(p + "event-only", l.eventOnly());
            List<Map<String, Object>> loot = new ArrayList<>();
            for (LootEntry e : l.loot()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("chance", e.chance());
                m.put("item", Base64.getEncoder().encodeToString(e.item().serializeAsBytes()));
                loot.add(m);
            }
            y.set(p + "loot", loot);
        }
        writeAsync(levelsFile, "# MirageHWCastle — уровни шалкеров и лут. Предметы хранятся в base64, редактируйте лут через /hwcastle loot.\n"
                + y.saveToString());
    }

    public void savePointsAsync(Collection<ShulkerPoint> points) {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShulkerPoint p : points) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", p.world());
            m.put("x", p.x());
            m.put("y", p.y());
            m.put("z", p.z());
            if (p.levelId() != null) {
                m.put("level", p.levelId());
            }
            m.put("base", p.baseData());
            list.add(m);
        }
        y.set("points", list);
        writeAsync(pointsFile, "# MirageHWCastle — привязанные блоки. Меняйте командами /hwcastle bind и /hwcastle unbind.\n"
                + y.saveToString());
    }

    /** Останавливает IO-поток, дожидаясь завершения записей. */
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Не все данные успели записаться на диск за 10 секунд.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- внутреннее

    private void writeAsync(Path target, String content) {
        if (io.isShutdown()) {
            return;
        }
        io.execute(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                Files.writeString(tmp, content, StandardCharsets.UTF_8);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось записать " + target.getFileName(), e);
            }
        });
    }

    private List<LootEntry> readLoot(List<Map<?, ?>> raw) {
        List<LootEntry> list = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            try {
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(m.get("item"))));
                list.add(new LootEntry(item, num(m.get("chance"))));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("levels.yml: не удалось прочитать предмет лута, пропущен.");
            }
        }
        return list;
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o));
    }
}
