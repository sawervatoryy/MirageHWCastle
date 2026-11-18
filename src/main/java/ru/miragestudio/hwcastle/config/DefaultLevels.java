package ru.miragestudio.hwcastle.config;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.miragestudio.hwcastle.manager.LevelRegistry;
import ru.miragestudio.hwcastle.model.LootEntry;
import ru.miragestudio.hwcastle.model.ShulkerLevel;

import java.util.List;

/**
 * Стартовые уровни шалкеров (создаются, если levels.yml ещё нет).
 */
public final class DefaultLevels {

    private DefaultLevels() {
    }

    public static ShulkerLevel common() {
        ShulkerLevel l = new ShulkerLevel("common", "&#AAAAAAОбычный", DyeColor.GRAY, 5, 60, 0, 1, 4, false);
        l.setLoot(List.of(
                e(Material.IRON_INGOT, 8, 60), e(Material.GOLD_INGOT, 4, 40), e(Material.COAL, 16, 50),
                e(Material.BREAD, 8, 40), e(Material.EXPERIENCE_BOTTLE, 3, 30)));
        return l;
    }

    public static ShulkerLevel rare() {
        ShulkerLevel l = new ShulkerLevel("rare", "&#55CCFFРедкий", DyeColor.LIGHT_BLUE, 7, 30, 0, 1, 4, false);
        l.setLoot(List.of(
                e(Material.DIAMOND, 2, 50), e(Material.IRON_BLOCK, 2, 40), e(Material.GOLDEN_APPLE, 1, 30),
                e(Material.EMERALD, 6, 50), e(Material.ENDER_PEARL, 2, 40)));
        return l;
    }

    public static ShulkerLevel epic() {
        ShulkerLevel l = new ShulkerLevel("epic", "&#B14CFFЭпический", DyeColor.PURPLE, 12, 10, 0, 1, 5, false);
        l.setLoot(List.of(
                e(Material.DIAMOND, 5, 60), e(Material.NETHERITE_SCRAP, 2, 40),
                e(Material.ENCHANTED_GOLDEN_APPLE, 1, 20), e(Material.TOTEM_OF_UNDYING, 1, 25),
                e(Material.NETHERITE_INGOT, 1, 15), e(Material.EXPERIENCE_BOTTLE, 16, 50)));
        return l;
    }

    public static ShulkerLevel emerald() {
        ShulkerLevel l = new ShulkerLevel(LevelRegistry.EMERALD_ID, "&#2ECC71Изумрудный", DyeColor.GREEN, 16, 0, 40, 2, 6, true);
        l.setLoot(List.of(
                e(Material.EMERALD_BLOCK, 3, 80), e(Material.EMERALD, 32, 90), e(Material.DIAMOND_BLOCK, 1, 40),
                e(Material.NETHERITE_INGOT, 1, 30), e(Material.ENCHANTED_GOLDEN_APPLE, 2, 40),
                e(Material.TOTEM_OF_UNDYING, 1, 35)));
        return l;
    }

    private static LootEntry e(Material material, int amount, int chance) {
        return new LootEntry(new ItemStack(material, amount), chance);
    }
}
