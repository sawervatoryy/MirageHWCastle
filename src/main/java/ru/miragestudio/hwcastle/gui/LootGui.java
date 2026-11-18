package ru.miragestudio.hwcastle.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.miragestudio.hwcastle.MirageHWCastle;
import ru.miragestudio.hwcastle.model.LootEntry;
import ru.miragestudio.hwcastle.model.ShulkerLevel;
import ru.miragestudio.hwcastle.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GUI лута: выбор уровня шалкера и редактор лута.
 * <ul>
 *   <li>Положите предмет в верхние 45 слотов — он станет лутом уровня.</li>
 *   <li>Shift+ЛКМ по предмету — шанс +5%, Shift+ПКМ — шанс −5%.</li>
 *   <li>Закрытие окна сохраняет лут (запись на диск — асинхронно).</li>
 * </ul>
 */
public final class LootGui implements Listener {

    private static final int ITEM_SLOTS = 45;
    private static final int INFO_SLOT = 49;
    private static final int BACK_SLOT = 53;
    private static final int SIZE = 54;

    private final MirageHWCastle plugin;
    private final NamespacedKey chanceKey;
    private final NamespacedKey loreKey;

    public LootGui(MirageHWCastle plugin) {
        this.plugin = plugin;
        this.chanceKey = new NamespacedKey(plugin, "loot_chance");
        this.loreKey = new NamespacedKey(plugin, "loot_lore_added");
    }

    private static final class SelectorHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class EditorHolder implements InventoryHolder {
        private final String levelId;
        private Inventory inventory;

        private EditorHolder(String levelId) {
            this.levelId = levelId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------------------------------------------------------- открытие

    public void openSelector(Player player) {
        SelectorHolder holder = new SelectorHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, plugin.lang().comp("gui.selector.title"));
        holder.inventory = inv;
        int slot = 0;
        for (ShulkerLevel level : plugin.levels().all()) {
            if (slot >= SIZE) {
                break;
            }
            ItemStack item = new ItemStack(level.boxMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.item(plugin.lang().raw("gui.selector.name", "name", level.displayName())));
            meta.lore(plugin.lang().lines("gui.selector.lore",
                    "hits", level.hits(), "weight", level.weight(), "fail", level.failChance(),
                    "count", level.loot().size(), "min", level.minItems(), "max", level.maxItems(),
                    "type", plugin.lang().raw(level.eventOnly() ? "gui.type-event" : "gui.type-normal"))
                    .stream().map(Text::item).toList());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    public void openEditor(Player player, ShulkerLevel level) {
        EditorHolder holder = new EditorHolder(level.id());
        Inventory inv = Bukkit.createInventory(holder, SIZE, plugin.lang().comp("gui.editor.title", "name", level.displayName()));
        holder.inventory = inv;

        int slot = 0;
        for (LootEntry entry : level.loot()) {
            if (slot >= ITEM_SLOTS) {
                break;
            }
            inv.setItem(slot++, decorate(entry.item(), entry.chance()));
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = ITEM_SLOTS; i < BACK_SLOT; i++) {
            inv.setItem(i, filler);
        }
        ItemStack info = named(Material.PAPER, plugin.lang().raw("gui.editor.info-name"));
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.lore(plugin.lang().lines("gui.editor.help").stream().map(Text::item).toList());
        info.setItemMeta(infoMeta);
        inv.setItem(INFO_SLOT, info);
        inv.setItem(BACK_SLOT, named(Material.ARROW, plugin.lang().raw("gui.back")));
        player.openInventory(inv);
    }

    /** Закрывает открытые редакторы (при выключении плагина) — лут при этом сохраняется. */
    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof EditorHolder) {
                p.closeInventory();
            }
        }
    }

    // ---------------------------------------------------------------- события

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof SelectorHolder) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top && e.getWhoClicked() instanceof Player player) {
                List<ShulkerLevel> levels = plugin.levels().all();
                int slot = e.getRawSlot();
                if (slot >= 0 && slot < levels.size()) {
                    ShulkerLevel level = levels.get(slot);
                    Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, level));
                }
            }
        } else if (holder instanceof EditorHolder) {
            handleEditorClick(e, top);
        }
    }

    private void handleEditorClick(InventoryClickEvent e, Inventory top) {
        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        int raw = e.getRawSlot();
        if (raw >= ITEM_SLOTS && raw < SIZE) {
            e.setCancelled(true);
            if (raw == BACK_SLOT && e.getWhoClicked() instanceof Player player) {
                Bukkit.getScheduler().runTask(plugin, () -> openSelector(player));
            }
            return;
        }
        if (raw >= 0 && raw < ITEM_SLOTS
                && (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT)) {
            e.setCancelled(true);
            ItemStack item = top.getItem(raw);
            if (item == null || item.getType().isAir()) {
                return;
            }
            int current = readChance(item).orElse(plugin.settings().loot().defaultChance());
            int next = Math.max(1, Math.min(100, current + (e.getClick() == ClickType.SHIFT_LEFT ? 5 : -5)));
            top.setItem(raw, decorate(strip(item), next));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof SelectorHolder) {
            e.setCancelled(true);
        } else if (holder instanceof EditorHolder) {
            for (int slot : e.getRawSlots()) {
                if (slot >= ITEM_SLOTS && slot < SIZE) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof EditorHolder holder)) {
            return;
        }
        Optional<ShulkerLevel> found = plugin.levels().get(holder.levelId);
        if (found.isEmpty()) {
            return;
        }
        ShulkerLevel level = found.get();
        List<LootEntry> entries = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack item = e.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int chance = readChance(item).orElse(plugin.settings().loot().defaultChance());
            entries.add(new LootEntry(strip(item), chance));
        }
        level.setLoot(entries);
        plugin.store().saveLevelsAsync(plugin.levels().all());
        if (e.getPlayer() instanceof Player player) {
            plugin.lang().send(player, "gui.saved", "name", level.displayName(), "count", entries.size());
        }
    }

    // ---------------------------------------------------------------- предметы-обёртки

    private Optional<Integer> readChance(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(meta.getPersistentDataContainer().get(chanceKey, PersistentDataType.INTEGER));
    }

    /** Добавляет в лор строку с шансом и сохраняет шанс в PDC предмета. */
    private ItemStack decorate(ItemStack item, int chance) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Text.item(plugin.lang().raw("gui.chance-line", "chance", chance)));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(chanceKey, PersistentDataType.INTEGER, chance);
        meta.getPersistentDataContainer().set(loreKey, PersistentDataType.INTEGER, 2);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Убирает служебный лор и PDC, возвращая «чистый» предмет. */
    private ItemStack strip(ItemStack item) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        Integer added = meta.getPersistentDataContainer().get(loreKey, PersistentDataType.INTEGER);
        if (added != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            for (int i = 0; i < added && !lore.isEmpty(); i++) {
                lore.remove(lore.size() - 1);
            }
            meta.lore(lore.isEmpty() ? null : lore);
            meta.getPersistentDataContainer().remove(chanceKey);
            meta.getPersistentDataContainer().remove(loreKey);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private static ItemStack named(Material material, String rawName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(rawName));
        item.setItemMeta(meta);
        return item;
    }
}
