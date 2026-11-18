package ru.miragestudio.hwcastle.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Чтение YAML-файлов плагина с подстановкой значений по умолчанию из jar.
 * Метод не трогает Bukkit API, поэтому его безопасно вызывать из асинхронного потока.
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    public static YamlConfiguration load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // значения по умолчанию просто не подставятся
        }
        return yaml;
    }
}
