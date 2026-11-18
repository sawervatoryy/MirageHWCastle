package ru.miragestudio.hwcastle.config;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import ru.miragestudio.hwcastle.util.Text;

import java.util.List;
import java.util.Map;

/**
 * Доступ к сообщениям из lang.yml. Все тексты плагина берутся только отсюда.
 * Плейсхолдеры: {prefix} и любые, переданные парами ключ-значение.
 */
public final class Lang {

    private volatile FileConfiguration cfg;

    public Lang(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    /** Подменяет конфигурацию (после reload). */
    public void update(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    public String raw(String key, Object... kv) {
        FileConfiguration c = cfg;
        String value = c.isList(key) ? String.join("\n", c.getStringList(key)) : c.getString(key);
        if (value == null) {
            return "&c[lang:" + key + "]";
        }
        Map<String, String> ph = Text.ph(kv);
        ph.put("prefix", c.getString("prefix", ""));
        return Text.apply(value, ph);
    }

    /** Все строки сообщения (список или одна строка). */
    public List<String> lines(String key, Object... kv) {
        FileConfiguration c = cfg;
        if (!c.isList(key)) {
            return List.of(raw(key, kv));
        }
        Map<String, String> ph = Text.ph(kv);
        ph.put("prefix", c.getString("prefix", ""));
        return c.getStringList(key).stream().map(s -> Text.apply(s, ph)).toList();
    }

    public Component comp(String key, Object... kv) {
        return Text.color(raw(key, kv));
    }

    public void send(CommandSender to, String key, Object... kv) {
        for (String line : lines(key, kv)) {
            to.sendMessage(Text.color(line));
        }
    }

    public void actionBar(Player to, String key, Object... kv) {
        to.sendActionBar(comp(key, kv));
    }

    public void broadcast(String key, Object... kv) {
        for (String line : lines(key, kv)) {
            Bukkit.broadcast(Text.color(line));
        }
    }
}
