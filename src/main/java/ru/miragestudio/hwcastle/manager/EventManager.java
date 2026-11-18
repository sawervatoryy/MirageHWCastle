package ru.miragestudio.hwcastle.manager;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.miragestudio.hwcastle.MirageHWCastle;
import ru.miragestudio.hwcastle.config.Settings;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Ивент «Изумрудная волна»: запуск/остановка, таймер, босс-бар, титры, расписание.
 */
public final class EventManager {

    private final MirageHWCastle plugin;
    private volatile boolean active;
    private long endAtMs;
    private int totalSeconds;
    private BossBar bar;
    private BukkitTask task;
    private final Set<Integer> warned = new HashSet<>();
    private String lastScheduleKey = "";

    public EventManager(MirageHWCastle plugin) {
        this.plugin = plugin;
    }

    /** Запускает секундный цикл (таймер ивента и расписание). */
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        if (active) {
            end(false, false);
        }
    }

    public boolean isActive() {
        return active;
    }

    public int remainingSeconds() {
        return active ? (int) Math.max(0, Math.ceil((endAtMs - System.currentTimeMillis()) / 1000.0)) : 0;
    }

    /**
     * Запускает ивент.
     *
     * @param secondsOverride длительность в секундах или 0 — взять из config.yml
     * @return false, если ивент уже идёт
     */
    public boolean begin(int secondsOverride) {
        if (active) {
            return false;
        }
        Settings s = plugin.settings();
        Settings.EventSettings ev = s.event();
        int seconds = secondsOverride > 0 ? secondsOverride : ev.durationSeconds();

        active = true;
        totalSeconds = seconds;
        endAtMs = System.currentTimeMillis() + seconds * 1000L;
        warned.clear();

        plugin.levels().applyEvent(ev);
        plugin.shulkers().beginEvent();

        Object[] ph = placeholders(s, seconds);
        plugin.lang().broadcast("event.start", ph);

        if (ev.bar().enabled()) {
            bar = BossBar.bossBar(plugin.lang().comp("event.bossbar", ph), 1.0f, ev.bar().color(), ev.bar().overlay());
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(bar);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ev.title().enabled()) {
                p.showTitle(title("event.title", ev.title(), ph));
            }
            ev.startFx().play(p.getLocation());
        }
        runCommands(ev.startCommands(), s);
        return true;
    }

    /**
     * Завершает ивент.
     *
     * @param natural true — время вышло, false — остановлен командой
     */
    public void end(boolean natural) {
        end(natural, true);
    }

    private void end(boolean natural, boolean announce) {
        if (!active) {
            return;
        }
        active = false;
        Settings s = plugin.settings();
        Settings.EventSettings ev = s.event();

        plugin.shulkers().endEvent();
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
            bar = null;
        }
        if (announce) {
            plugin.lang().broadcast(natural ? "event.end" : "event.stopped");
            for (Player p : Bukkit.getOnlinePlayers()) {
                ev.endFx().play(p.getLocation());
            }
            runCommands(ev.endCommands(), s);
        }
    }

    /** Показывает босс-бар зашедшему во время ивента игроку. */
    public void onJoin(Player player) {
        if (active && bar != null) {
            player.showBossBar(bar);
        }
    }

    private void tick() {
        Settings s = plugin.settings();
        if (active) {
            long left = endAtMs - System.currentTimeMillis();
            if (left <= 0) {
                end(true);
                return;
            }
            int sec = (int) Math.ceil(left / 1000.0);
            if (bar != null) {
                bar.progress(Math.max(0f, Math.min(1f, left / (totalSeconds * 1000f))));
                bar.name(plugin.lang().comp("event.bossbar", placeholders(s, sec)));
            }
            if (s.event().warnAt().contains(sec) && warned.add(sec)) {
                plugin.lang().broadcast("event.warn", placeholders(s, sec));
            }
        } else if (s.event().enabled() && s.event().scheduleEnabled()) {
            checkSchedule(s.event());
        }
    }

    private void checkSchedule(Settings.EventSettings ev) {
        ZonedDateTime now = ZonedDateTime.now(ev.zone());
        if (!ev.days().isEmpty() && !ev.days().contains(now.getDayOfWeek())) {
            return;
        }
        LocalTime time = now.toLocalTime().withSecond(0).withNano(0);
        if (ev.times().contains(time)) {
            String key = now.toLocalDate() + " " + time;
            if (!key.equals(lastScheduleKey)) {
                lastScheduleKey = key;
                begin(0);
            }
        }
    }

    private Object[] placeholders(Settings s, int seconds) {
        return new Object[]{
                "seconds", seconds,
                "time", String.format("%02d:%02d", seconds / 60, seconds % 60),
                "x", s.castle().centerX(),
                "z", s.castle().centerZ(),
                "hits", s.event().hits()};
    }

    private Title title(String key, Settings.TitleSettings cfg, Object[] ph) {
        return Title.title(plugin.lang().comp(key + ".title", ph), plugin.lang().comp(key + ".subtitle", ph),
                Title.Times.times(Duration.ofMillis(cfg.fadeIn() * 50L), Duration.ofMillis(cfg.stay() * 50L),
                        Duration.ofMillis(cfg.fadeOut() * 50L)));
    }

    private void runCommands(Iterable<String> commands, Settings s) {
        for (String raw : commands) {
            String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
            cmd = cmd.replace("{x}", String.valueOf(s.castle().centerX()))
                    .replace("{z}", String.valueOf(s.castle().centerZ()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }
}
