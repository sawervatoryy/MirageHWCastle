package ru.miragestudio.hwcastle.model;

import org.bukkit.inventory.ItemStack;

/**
 * Одна позиция лута: предмет и шанс выпадения в процентах (1..100).
 */
public record LootEntry(ItemStack item, int chance) {

    public LootEntry {
        item = item.clone();
        chance = Math.max(1, Math.min(100, chance));
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
