package ru.miragestudio.hwcastle.manager;

import org.bukkit.block.Block;
import ru.miragestudio.hwcastle.model.ShulkerPoint;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр привязанных блоков. Потокобезопасен.
 */
public final class PointRegistry {

    private final Map<String, ShulkerPoint> points = new ConcurrentHashMap<>();

    /** @return false, если такой блок уже привязан */
    public boolean add(ShulkerPoint point) {
        return points.putIfAbsent(point.key(), point) == null;
    }

    public Optional<ShulkerPoint> remove(Block block) {
        return Optional.ofNullable(points.remove(ShulkerPoint.key(block)));
    }

    public Optional<ShulkerPoint> get(Block block) {
        return Optional.ofNullable(points.get(ShulkerPoint.key(block)));
    }

    public void clear() {
        points.clear();
    }

    public Collection<ShulkerPoint> all() {
        return List.copyOf(points.values());
    }

    public int size() {
        return points.size();
    }
}
