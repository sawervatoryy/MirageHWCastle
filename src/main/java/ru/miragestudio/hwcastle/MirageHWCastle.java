package ru.miragestudio.hwcastle;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.miragestudio.hwcastle.castle.SchematicPaster;
import ru.miragestudio.hwcastle.command.HwCastleCommand;
import ru.miragestudio.hwcastle.config.DataStore;
import ru.miragestudio.hwcastle.config.Lang;
import ru.miragestudio.hwcastle.config.Settings;
import ru.miragestudio.hwcastle.gui.LootGui;
import ru.miragestudio.hwcastle.listener.CastleListener;
import ru.miragestudio.hwcastle.listener.ShulkerListener;
import ru.miragestudio.hwcastle.manager.EventManager;
import ru.miragestudio.hwcastle.manager.LevelRegistry;
import ru.miragestudio.hwcastle.manager.LootService;
import ru.miragestudio.hwcastle.manager.PointRegistry;
import ru.miragestudio.hwcastle.manager.ShulkerManager;
import ru.miragestudio.hwcastle.util.Brand;
import ru.miragestudio.hwcastle.util.ConfigFiles;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * MirageHWCastle v0.1 Pre-Release — замок с шалкерами и ивент «Изумрудная волна».
 * MirageStudio | Автор: sawervatoryy | https://discord.gg/mQbZT3x3Q
 */
public final class MirageHWCastle extends JavaPlugin {

    private volatile Settings settings;
    private Lang lang;
    private DataStore store;
    private final LevelRegistry levels = new LevelRegistry();
    private final PointRegistry points = new PointRegistry();
    private final LootService loot = new LootService();
    private ShulkerManager shulkers;
    private EventManager events;
    private LootGui lootGui;
    private SchematicPaster paster;

    @Override
    public void onEnable() {
        // Старт сервера: небольшое синхронное чтение конфигов допустимо, тики ещё не идут.
        for (String name : List.of("config.yml", "lang.yml")) {
            if (!new File(getDataFolder(), name).exists()) {
                saveResource(name, false);
            }
        }
        settings = Settings.from(ConfigFiles.load(this, "config.yml"));
        lang = new Lang(ConfigFiles.load(this, "lang.yml"));

        store = new DataStore(this);
        store.loadLevels(levels);
        levels.applyEvent(settings.event());
        store.loadPoints().forEach(points::add);

        shulkers = new ShulkerManager(this);
        events = new EventManager(this);
        lootGui = new LootGui(this);
        paster = new SchematicPaster(this);

        Bukkit.getPluginManager().registerEvents(new ShulkerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CastleListener(this), this);
        Bukkit.getPluginManager().registerEvents(lootGui, this);

        PluginCommand command = getCommand("hwcastle");
        if (command != null) {
            HwCastleCommand executor = new HwCastleCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        shulkers.reconcileLoaded();
        shulkers.start();
        events.start();
        Brand.banner(this);
    }

    @Override
    public void onDisable() {
        if (lootGui != null) {
            lootGui.closeAll();
        }
        if (events != null) {
            events.shutdown();
        }
        if (shulkers != null) {
            shulkers.stop();
        }
        if (paster != null) {
            paster.shutdown();
        }
        if (store != null) {
            store.close();
        }
    }

    /**
     * Перечитывает config.yml и lang.yml асинхронно, применяет результат в главном потоке.
     * levels.yml и points.yml не перечитываются — ими управляют команды и GUI.
     */
    public void reloadAsync(Runnable onDone, Consumer<Throwable> onError) {
        CompletableFuture
                .supplyAsync(() -> new YamlConfiguration[]{
                        ConfigFiles.load(this, "config.yml"), ConfigFiles.load(this, "lang.yml")})
                .whenComplete((loaded, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        onError.accept(error);
                        return;
                    }
                    try {
                        settings = Settings.from(loaded[0]);
                        lang.update(loaded[1]);
                        levels.applyEvent(settings.event());
                        onDone.run();
                    } catch (RuntimeException e) {
                        onError.accept(e);
                    }
                }));
    }

    public Settings settings() {
        return settings;
    }

    public Lang lang() {
        return lang;
    }

    public DataStore store() {
        return store;
    }

    public LevelRegistry levels() {
        return levels;
    }

    public PointRegistry points() {
        return points;
    }

    public LootService loot() {
        return loot;
    }

    public ShulkerManager shulkers() {
        return shulkers;
    }

    public EventManager events() {
        return events;
    }

    public LootGui lootGui() {
        return lootGui;
    }

    public SchematicPaster paster() {
        return paster;
    }
}
