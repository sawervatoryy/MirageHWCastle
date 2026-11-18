package ru.miragestudio.hwcastle.util;

import ru.miragestudio.hwcastle.MirageHWCastle;

/**
 * Водяной знак студии. Используется в консоли, /hwcastle about, README и конфигах.
 */
public final class Brand {

    public static final String STUDIO = "MirageStudio";
    public static final String AUTHOR = "sawervatoryy";
    public static final String DISCORD = "https://discord.gg/mQbZT3x3Q";

    private Brand() {
    }

    /** Строка водяного знака в формате &-кодов (основной &f, выделение &#00ACFF). */
    public static String line() {
        return "&#00ACFF" + STUDIO + " &f| Автор: &#00ACFF" + AUTHOR + " &f| &#00ACFF" + DISCORD;
    }

    /** Баннер при запуске плагина. */
    public static void banner(MirageHWCastle plugin) {
        var log = plugin.getComponentLogger();
        log.info(Text.color("&#00ACFF⌈ &fMirageHWCastle &#00ACFFv" + plugin.getPluginMeta().getVersion() + " &f⌋"));
        log.info(Text.color(line()));
    }
}
