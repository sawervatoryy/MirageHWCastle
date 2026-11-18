package ru.miragestudio.hwcastle.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Утилиты текста: &-коды + HEX (&#RRGGBB) и плейсхолдеры вида {name}.
 */
public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component color(String raw) {
        return SERIALIZER.deserialize(raw == null ? "" : raw);
    }

    /** Компонент для предметов: курсив по умолчанию выключен. */
    public static Component item(String raw) {
        return color(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Подставляет плейсхолдеры {key} -> value. */
    public static String apply(String raw, Map<String, String> placeholders) {
        String result = raw;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    /** ph("a", 1, "b", "x") -> {a=1, b=x}. */
    public static Map<String, String> ph(Object... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        return map;
    }
}
