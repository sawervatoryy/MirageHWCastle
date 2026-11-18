package ru.miragestudio.hwcastle.manager;

import org.bukkit.inventory.ItemStack;
import ru.miragestudio.hwcastle.model.LootEntry;
import ru.miragestudio.hwcastle.model.ShulkerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Генерация лута шалкера по настройкам уровня.
 */
public final class LootService {

    /**
     * @param multiplier множитель количества предметов (для ивента &gt; 1)
     * @return готовые стаки (уже разбитые по максимальному размеру стака)
     */
    public List<ItemStack> roll(ShulkerLevel level, double multiplier) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<LootEntry> entries = level.loot();
        Collections.shuffle(entries, rnd);

        int cap = level.maxItems() > 0 ? level.maxItems() : Integer.MAX_VALUE;
        List<LootEntry> chosen = new ArrayList<>();
        for (LootEntry e : entries) {
            if (chosen.size() >= cap) {
                break;
            }
            if (rnd.nextDouble() * 100.0 < e.chance()) {
                chosen.add(e);
            }
        }
        // добираем до минимума случайными позициями, которые ещё не выпали
        List<LootEntry> rest = new ArrayList<>(entries);
        rest.removeAll(chosen);
        while (chosen.size() < Math.min(level.minItems(), cap) && !rest.isEmpty()) {
            chosen.add(rest.remove(rnd.nextInt(rest.size())));
        }

        List<ItemStack> result = new ArrayList<>();
        for (LootEntry e : chosen) {
            ItemStack base = e.item();
            int total = Math.max(1, (int) Math.round(base.getAmount() * multiplier));
            int stackSize = Math.max(1, base.getMaxStackSize());
            while (total > 0) {
                ItemStack part = base.clone();
                part.setAmount(Math.min(total, stackSize));
                result.add(part);
                total -= part.getAmount();
            }
        }
        return result;
    }
}
