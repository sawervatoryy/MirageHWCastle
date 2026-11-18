package ru.miragestudio.hwcastle.model;

import org.bukkit.block.Block;

/**
 * Привязанный блок, на месте которого появляются шалкеры.
 *
 * @param levelId  фиксированный уровень или {@code null} — тогда редкость выбирается случайно
 * @param baseData BlockData исходного блока, который возвращается после исчезновения шалкера
 */
public record ShulkerPoint(String world, int x, int y, int z, String levelId, String baseData) {

    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ';' + x + ';' + y + ';' + z;
    }

    public static String key(Block b) {
        return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
