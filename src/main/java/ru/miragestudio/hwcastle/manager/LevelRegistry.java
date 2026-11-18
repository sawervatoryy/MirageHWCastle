package ru.miragestudio.hwcastle.manager;

import ru.miragestudio.hwcastle.config.Settings;
import ru.miragestudio.hwcastle.model.ShulkerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Реестр уровней шалкеров. Потокобезопасен.
 */
public final class LevelRegistry {

    /** Встроенный уровень ивента «Изумрудная волна». */
    public static final String EMERALD_ID = "emerald";

    private final Map<String, ShulkerLevel> levels = new ConcurrentHashMap<>();

    public Optional<ShulkerLevel> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(levels.get(id.toLowerCase(Locale.ROOT)));
    }

    public void put(ShulkerLevel level) {
        levels.put(level.id().toLowerCase(Locale.ROOT), level);
    }

    public boolean remove(String id) {
        return levels.remove(id.toLowerCase(Locale.ROOT)) != null;
    }

    public void clear() {
        levels.clear();
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }

    /** Все уровни: сначала обычные по числу ударов, затем ивентовые. */
    public List<ShulkerLevel> all() {
        return levels.values().stream()
                .sorted(Comparator.comparing(ShulkerLevel::eventOnly)
                        .thenComparingInt(ShulkerLevel::hits)
                        .thenComparing(ShulkerLevel::id))
                .toList();
    }

    public ShulkerLevel emerald() {
        return levels.get(EMERALD_ID);
    }

    /** Взвешенный случайный выбор среди обычных уровней; {@code null}, если выбирать не из чего. */
    public ShulkerLevel pickRandom() {
        List<ShulkerLevel> pool = levels.values().stream()
                .filter(l -> !l.eventOnly() && l.weight() > 0)
                .toList();
        int total = pool.stream().mapToInt(ShulkerLevel::weight).sum();
        if (total <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (ShulkerLevel l : pool) {
            roll -= l.weight();
            if (roll < 0) {
                return l;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Применяет настройки ивента (число ударов и шанс пустого) к уровню «Изумрудный». */
    public void applyEvent(Settings.EventSettings ev) {
        ShulkerLevel e = emerald();
        if (e != null) {
            e.setHits(ev.hits());
            e.setFailChance(ev.failChance());
        }
    }
}
