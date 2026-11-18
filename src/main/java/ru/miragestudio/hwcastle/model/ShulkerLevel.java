package ru.miragestudio.hwcastle.model;

import org.bukkit.DyeColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Уровень (редкость) шалкера: цвет, число ударов, вес появления, шанс пустого шалкера и лут.
 */
public final class ShulkerLevel {

    private final String id;
    private final boolean eventOnly;
    private volatile String displayName;
    private volatile DyeColor dye;
    private volatile int hits;
    private volatile int weight;
    private volatile double failChance;
    private volatile int minItems;
    private volatile int maxItems;
    private final List<LootEntry> loot = new CopyOnWriteArrayList<>();

    public ShulkerLevel(String id, String displayName, DyeColor dye, int hits, int weight,
                        double failChance, int minItems, int maxItems, boolean eventOnly) {
        this.id = id;
        this.displayName = displayName;
        this.dye = dye;
        this.hits = hits;
        this.weight = weight;
        this.failChance = failChance;
        this.minItems = minItems;
        this.maxItems = maxItems;
        this.eventOnly = eventOnly;
    }

    public String id() {
        return id;
    }

    /** Уровень используется только во время ивента (не выбирается при обычном спавне). */
    public boolean eventOnly() {
        return eventOnly;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public DyeColor dye() {
        return dye;
    }

    public void setDye(DyeColor dye) {
        this.dye = dye;
    }

    /** Материал блока шалкера нужного цвета. */
    public Material boxMaterial() {
        return Material.valueOf(dye.name() + "_SHULKER_BOX");
    }

    public int hits() {
        return hits;
    }

    public void setHits(int hits) {
        this.hits = Math.max(1, hits);
    }

    public int weight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = Math.max(0, weight);
    }

    /** Шанс (в %), что шалкер окажется пустым и взорвётся. */
    public double failChance() {
        return failChance;
    }

    public void setFailChance(double failChance) {
        this.failChance = Math.max(0.0, Math.min(100.0, failChance));
    }

    public int minItems() {
        return minItems;
    }

    public void setMinItems(int minItems) {
        this.minItems = Math.max(0, minItems);
    }

    /** Максимум предметов за один шалкер (0 — без ограничения). */
    public int maxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = Math.max(0, maxItems);
    }

    public List<LootEntry> loot() {
        return new ArrayList<>(loot);
    }

    public void setLoot(List<LootEntry> entries) {
        loot.clear();
        loot.addAll(entries);
    }
}
