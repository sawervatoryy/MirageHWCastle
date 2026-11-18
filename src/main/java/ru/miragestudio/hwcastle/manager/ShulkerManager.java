package ru.miragestudio.hwcastle.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.miragestudio.hwcastle.MirageHWCastle;
import ru.miragestudio.hwcastle.config.Settings;
import ru.miragestudio.hwcastle.model.ActiveShulker;
import ru.miragestudio.hwcastle.model.ShulkerLevel;
import ru.miragestudio.hwcastle.model.ShulkerPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Жизненный цикл шалкеров: спавн на привязанных блоках, удары, лут/взрыв, голограммы.
 * Всё взаимодействие с миром выполняется в главном потоке.
 */
public final class ShulkerManager {

    private final MirageHWCastle plugin;
    private final Map<String, ActiveShulker> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHit = new ConcurrentHashMap<>();
    private long nextSpawnAt;
    private boolean initialFillPending;
    private BukkitTask task;

    public ShulkerManager(MirageHWCastle plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- запуск / остановка

    public void start() {
        initialFillPending = plugin.settings().spawn().initialFill();
        nextSpawnAt = System.currentTimeMillis() + 3000L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Останавливает цикл и возвращает все блоки в исходное состояние. */
    public void stop() {
        if (task != null) {
            task.cancel();
        }
        clearAll();
        lastHit.clear();
    }

    private void tick() {
        Settings s = plugin.settings();
        if (!s.spawn().enabled() || System.currentTimeMillis() < nextSpawnAt) {
            return;
        }
        boolean fillAll = initialFillPending || s.spawn().perCycle() == 0;
        initialFillPending = false;
        spawn(fillAll ? Integer.MAX_VALUE : s.spawn().perCycle(), null);
        scheduleNext();
    }

    private void scheduleNext() {
        Settings s = plugin.settings();
        long seconds = plugin.events().isActive()
                ? s.event().spawnInterval()
                : ThreadLocalRandom.current().nextInt(s.spawn().minInterval(), s.spawn().maxInterval() + 1);
        nextSpawnAt = System.currentTimeMillis() + seconds * 1000L;
    }

    // ---------------------------------------------------------------- спавн

    /** Уровень для точки: во время ивента — изумрудный, иначе фиксированный или случайный. */
    public ShulkerLevel levelFor(ShulkerPoint point) {
        LevelRegistry levels = plugin.levels();
        if (plugin.events().isActive()) {
            return levels.emerald();
        }
        Optional<ShulkerLevel> fixed = levels.get(point.levelId());
        if (fixed.isPresent() && !fixed.get().eventOnly()) {
            return fixed.get();
        }
        return levels.pickRandom();
    }

    /**
     * Спавнит шалкеров на свободных привязанных блоках (только в загруженных чанках).
     *
     * @param limit  максимум шалкеров ({@link Integer#MAX_VALUE} — на все свободные точки)
     * @param forced принудительный уровень или {@code null}
     * @return сколько шалкеров появилось
     */
    public int spawn(int limit, ShulkerLevel forced) {
        List<ShulkerPoint> free = new ArrayList<>();
        for (ShulkerPoint p : plugin.points().all()) {
            if (!active.containsKey(p.key()) && isLoaded(p)) {
                free.add(p);
            }
        }
        Collections.shuffle(free);
        int spawned = 0;
        for (ShulkerPoint p : free) {
            if (spawned >= limit) {
                break;
            }
            ShulkerLevel level = forced != null ? forced : levelFor(p);
            if (level != null && spawnAt(p, level)) {
                spawned++;
            }
        }
        return spawned;
    }

    public boolean spawnAt(ShulkerPoint point, ShulkerLevel level) {
        if (level == null || active.containsKey(point.key()) || !isLoaded(point)) {
            return false;
        }
        Block block = block(point);
        if (block == null) {
            return false;
        }
        block.setBlockData(level.boxMaterial().createBlockData(), false);
        ActiveShulker shulker = new ActiveShulker(point, level);
        active.put(point.key(), shulker);
        refreshHologram(shulker);
        plugin.settings().hit().spawnFx().play(center(block));
        return true;
    }

    // ---------------------------------------------------------------- удары

    public ActiveShulker activeAt(Block block) {
        return active.get(ShulkerPoint.key(block));
    }

    public Collection<ActiveShulker> activeAll() {
        return List.copyOf(active.values());
    }

    public int activeCount() {
        return active.size();
    }

    /** Один удар игрока по шалкеру. */
    public void hit(Player player, ActiveShulker shulker) {
        Settings s = plugin.settings();
        long now = System.currentTimeMillis();
        Long last = lastHit.get(player.getUniqueId());
        if (last != null && now - last < s.hit().cooldownMs()) {
            return;
        }
        lastHit.put(player.getUniqueId(), now);

        Block block = block(shulker.point());
        if (block == null) {
            return;
        }
        ShulkerLevel level = shulker.level();
        (level.eventOnly() ? s.event().hitFx() : s.hit().hitFx()).play(center(block));

        int left = shulker.hit();
        if (left > 0) {
            refreshHologram(shulker);
            plugin.lang().actionBar(player, "shulker.hit", "name", level.displayName(), "left", left, "max", level.hits());
            return;
        }
        finish(shulker, player, block);
    }

    private void finish(ActiveShulker shulker, Player player, Block block) {
        Settings s = plugin.settings();
        ShulkerLevel level = shulker.level();
        boolean eventLevel = level.eventOnly();
        removeActive(shulker, true);

        Location center = center(block);
        boolean success = ThreadLocalRandom.current().nextDouble() * 100.0 >= level.failChance();
        if (success) {
            double multiplier = eventLevel ? s.event().lootMultiplier() : 1.0;
            List<ItemStack> items = plugin.loot().roll(level, multiplier);
            for (ItemStack item : items) {
                block.getWorld().dropItemNaturally(center, item);
            }
            (eventLevel ? s.event().breakFx() : s.hit().breakFx()).play(center);
            plugin.lang().send(player, "shulker.success", "name", level.displayName(), "items", items.size());
        } else {
            explode(center, eventLevel ? s.event().explosion() : s.failExplosion());
            plugin.lang().send(player, "shulker.fail", "name", level.displayName());
        }
    }

    /** Взрыв пустого шалкера: урон по игрокам в радиусе, блоки не ломаются. */
    private void explode(Location center, Settings.ExplosionSettings ex) {
        ex.fx().play(center);
        if (ex.damage() <= 0) {
            return;
        }
        World world = center.getWorld();
        for (Player p : world.getNearbyPlayers(center, ex.radius())) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                p.damage(ex.damage());
                plugin.lang().send(p, "shulker.explosion-hit");
            }
        }
    }

    // ---------------------------------------------------------------- удаление

    /** Убирает шалкер; при {@code restore} возвращает исходный блок. */
    public void removeActive(ActiveShulker shulker, boolean restore) {
        active.remove(shulker.point().key());
        removeHologram(shulker);
        if (restore) {
            restoreBase(shulker.point());
        }
    }

    public int clearAll() {
        int count = 0;
        for (ActiveShulker a : List.copyOf(active.values())) {
            removeActive(a, true);
            count++;
        }
        return count;
    }

    public int removeByLevel(String levelId) {
        int count = 0;
        for (ActiveShulker a : List.copyOf(active.values())) {
            if (a.level().id().equalsIgnoreCase(levelId)) {
                removeActive(a, true);
                count++;
            }
        }
        return count;
    }

    public void removeAtPoint(ShulkerPoint point) {
        ActiveShulker a = active.get(point.key());
        if (a != null) {
            removeActive(a, true);
        } else {
            restoreBase(point);
        }
    }

    // ---------------------------------------------------------------- ивент

    /** Все стоящие шалкеры становятся изумрудными, свободные точки заполняются. */
    public void beginEvent() {
        ShulkerLevel emerald = plugin.levels().emerald();
        for (ActiveShulker a : active.values()) {
            if (!a.level().eventOnly()) {
                a.setLevel(emerald);
                Block block = block(a.point());
                if (block != null) {
                    block.setBlockData(emerald.boxMaterial().createBlockData(), false);
                }
                refreshHologram(a);
            }
        }
        Settings.EventSettings ev = plugin.settings().event();
        spawn(ev.fillAll() ? Integer.MAX_VALUE : Math.max(1, plugin.settings().spawn().perCycle()), emerald);
        nextSpawnAt = System.currentTimeMillis() + ev.spawnInterval() * 1000L;
    }

    /** Изумрудные шалкеры исчезают, обычный спавн возобновляется. */
    public void endEvent() {
        for (ActiveShulker a : List.copyOf(active.values())) {
            if (a.level().eventOnly()) {
                removeActive(a, true);
            }
        }
        nextSpawnAt = System.currentTimeMillis() + 3000L;
    }

    // ---------------------------------------------------------------- чистка после сбоя

    public void reconcileLoaded() {
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                reconcileChunk(c);
            }
        }
    }

    /** Если после сбоя на точке остался «чужой» шалкер — возвращает исходный блок. */
    public void reconcileChunk(Chunk chunk) {
        for (ShulkerPoint p : plugin.points().all()) {
            if (p.chunkX() != chunk.getX() || p.chunkZ() != chunk.getZ()
                    || !p.world().equals(chunk.getWorld().getName()) || active.containsKey(p.key())) {
                continue;
            }
            Block b = chunk.getBlock(p.x() & 15, p.y(), p.z() & 15);
            if (Tag.SHULKER_BOXES.isTagged(b.getType())) {
                b.setBlockData(parseBase(p), false);
            }
        }
    }

    // ---------------------------------------------------------------- вспомогательное

    private void restoreBase(ShulkerPoint point) {
        Block block = block(point);
        if (block != null && Tag.SHULKER_BOXES.isTagged(block.getType())) {
            block.setBlockData(parseBase(point), false);
        }
    }

    private static BlockData parseBase(ShulkerPoint point) {
        try {
            return Bukkit.createBlockData(point.baseData());
        } catch (IllegalArgumentException e) {
            return Material.CHISELED_STONE_BRICKS.createBlockData();
        }
    }

    private boolean isLoaded(ShulkerPoint p) {
        World w = Bukkit.getWorld(p.world());
        return w != null && w.isChunkLoaded(p.chunkX(), p.chunkZ());
    }

    private Block block(ShulkerPoint p) {
        World w = Bukkit.getWorld(p.world());
        return w == null ? null : w.getBlockAt(p.x(), p.y(), p.z());
    }

    private static Location center(Block b) {
        return b.getLocation().add(0.5, 0.5, 0.5);
    }

    private void refreshHologram(ActiveShulker a) {
        Settings.HologramSettings cfg = plugin.settings().hologram();
        if (!cfg.enabled()) {
            return;
        }
        Block block = block(a.point());
        if (block == null) {
            return;
        }
        Component text = plugin.lang().comp("hologram.format", "name", a.level().displayName(),
                "left", a.remaining(), "max", a.level().hits());
        Entity existing = a.hologram() == null ? null : Bukkit.getEntity(a.hologram());
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.text(text);
            return;
        }
        Location loc = block.getLocation().add(0.5, 1.0 + cfg.yOffset(), 0.5);
        TextDisplay display = block.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setPersistent(false);
        });
        a.setHologram(display.getUniqueId());
    }

    private void removeHologram(ActiveShulker a) {
        if (a.hologram() == null) {
            return;
        }
        Entity e = Bukkit.getEntity(a.hologram());
        if (e != null) {
            e.remove();
        }
        a.setHologram(null);
    }
}
