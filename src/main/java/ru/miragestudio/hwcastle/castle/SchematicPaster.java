package ru.miragestudio.hwcastle.castle;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import ru.miragestudio.hwcastle.MirageHWCastle;
import ru.miragestudio.hwcastle.config.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Вставка замка из схематики Sponge (.schem v2/v3) без WorldEdit.
 * Файл читается и разбирается асинхронно (виртуальный поток), блоки ставятся порциями
 * по {@code blocks-per-tick} в главном потоке, чтобы не вешать сервер.
 */
public final class SchematicPaster {

    /** Разобранная схематика: размеры, палитра и индексы блоков (порядок YZX). */
    private record Schematic(int width, int height, int length, String[] palette, int[] blocks) {
    }

    private final MirageHWCastle plugin;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean busy;

    public SchematicPaster(MirageHWCastle plugin) {
        this.plugin = plugin;
    }

    public boolean isBusy() {
        return busy;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /** Вставляет замок в центр зоны замка (config: castle.center-x / center-z) на высоте {@code y}. */
    public void paste(CommandSender sender, World world, int y) {
        if (busy) {
            plugin.lang().send(sender, "paste.busy");
            return;
        }
        busy = true;
        Path file = plugin.getDataFolder().toPath().resolve(plugin.settings().castle().schematicFile());
        plugin.lang().send(sender, "paste.reading", "file", plugin.settings().castle().schematicFile());

        CompletableFuture.supplyAsync(() -> read(file), executor).whenComplete((schematic, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        busy = false;
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        plugin.getLogger().warning("Ошибка чтения схематики: " + cause);
                        plugin.lang().send(sender, "paste.error", "error", String.valueOf(cause.getMessage()));
                    } else if (schematic == null) {
                        busy = false;
                        plugin.lang().send(sender, "paste.no-file", "file", plugin.settings().castle().schematicFile());
                    } else {
                        place(sender, world, y, schematic);
                    }
                }));
    }

    // ---------------------------------------------------------------- чтение (асинхронно)

    @SuppressWarnings("unchecked")
    private Schematic read(Path file) {
        try {
            if (!Files.exists(file)) {
                return null;
            }
            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(file)) {
                root = Nbt.readRoot(in);
            }
            Map<String, Object> schem = root.get("Schematic") instanceof Map<?, ?> m ? (Map<String, Object>) m : root;
            int w = ((Number) schem.get("Width")).intValue();
            int h = ((Number) schem.get("Height")).intValue();
            int l = ((Number) schem.get("Length")).intValue();

            // v3: блоки лежат в "Blocks" (Palette + Data); v2: Palette + BlockData в корне
            Map<String, Object> blocks = schem.get("Blocks") instanceof Map<?, ?> m ? (Map<String, Object>) m : schem;
            Map<String, Object> paletteMap = (Map<String, Object>) blocks.get("Palette");
            byte[] data = (byte[]) (blocks.containsKey("Data") ? blocks.get("Data") : blocks.get("BlockData"));

            int paletteSize = paletteMap.values().stream().mapToInt(v -> ((Number) v).intValue()).max().orElse(-1) + 1;
            String[] palette = new String[paletteSize];
            paletteMap.forEach((state, index) -> palette[((Number) index).intValue()] = state);

            int[] indices = new int[w * h * l];
            int pos = 0;
            int idx = 0;
            while (pos < data.length && idx < indices.length) {
                int value = 0;
                int shift = 0;
                byte b;
                do {
                    b = data[pos++];
                    value |= (b & 0x7F) << shift;
                    shift += 7;
                } while ((b & 0x80) != 0 && pos < data.length);
                indices[idx++] = value;
            }
            return new Schematic(w, h, l, palette, indices);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- установка (главный поток, порциями)

    private void place(CommandSender sender, World world, int y, Schematic schem) {
        Settings.CastleSettings cs = plugin.settings().castle();
        BlockData[] palette = new BlockData[schem.palette().length];
        for (int i = 0; i < palette.length; i++) {
            try {
                palette[i] = schem.palette()[i] == null ? null : Bukkit.createBlockData(schem.palette()[i]);
            } catch (IllegalArgumentException e) {
                palette[i] = null; // неизвестный блок пропускаем
            }
        }
        int originX = cs.centerX() - schem.width() / 2;
        int originZ = cs.centerZ() - schem.length() / 2;
        int total = schem.blocks().length;
        int perTick = cs.blocksPerTick();
        boolean ignoreAir = cs.ignoreAir();

        plugin.lang().send(sender, "paste.started", "w", schem.width(), "h", schem.height(), "l", schem.length(),
                "x", originX, "y", y, "z", originZ);

        new BukkitRunnable() {
            private int cursor;
            private int placed;

            @Override
            public void run() {
                int end = Math.min(total, cursor + perTick);
                for (; cursor < end; cursor++) {
                    int state = schem.blocks()[cursor];
                    BlockData data = state >= 0 && state < palette.length ? palette[state] : null;
                    if (data == null || (ignoreAir && data.getMaterial().isAir())) {
                        continue;
                    }
                    int x = cursor % schem.width();
                    int z = (cursor / schem.width()) % schem.length();
                    int dy = cursor / (schem.width() * schem.length());
                    world.getBlockAt(originX + x, y + dy, originZ + z).setBlockData(data, false);
                    placed++;
                }
                if (cursor >= total) {
                    cancel();
                    busy = false;
                    plugin.lang().send(sender, "paste.done", "blocks", placed);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
