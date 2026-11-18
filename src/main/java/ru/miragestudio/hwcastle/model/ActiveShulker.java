package ru.miragestudio.hwcastle.model;

import java.util.UUID;

/**
 * Шалкер, который сейчас стоит в мире. Изменяется только из главного потока.
 */
public final class ActiveShulker {

    private final ShulkerPoint point;
    private volatile ShulkerLevel level;
    private volatile int remaining;
    private volatile UUID hologram;

    public ActiveShulker(ShulkerPoint point, ShulkerLevel level) {
        this.point = point;
        this.level = level;
        this.remaining = level.hits();
    }

    public ShulkerPoint point() {
        return point;
    }

    public ShulkerLevel level() {
        return level;
    }

    /** Меняет уровень (при старте ивента) и сбрасывает счётчик ударов. */
    public void setLevel(ShulkerLevel level) {
        this.level = level;
        this.remaining = level.hits();
    }

    public int remaining() {
        return remaining;
    }

    /** Уменьшает счётчик на 1 и возвращает остаток. */
    public int hit() {
        return --remaining;
    }

    public UUID hologram() {
        return hologram;
    }

    public void setHologram(UUID hologram) {
        this.hologram = hologram;
    }
}
