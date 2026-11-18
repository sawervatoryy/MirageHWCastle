package ru.miragestudio.hwcastle.command;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import ru.miragestudio.hwcastle.MirageHWCastle;
import ru.miragestudio.hwcastle.config.Lang;
import ru.miragestudio.hwcastle.manager.LevelRegistry;
import ru.miragestudio.hwcastle.model.ShulkerLevel;
import ru.miragestudio.hwcastle.model.ShulkerPoint;
import ru.miragestudio.hwcastle.util.Brand;
import ru.miragestudio.hwcastle.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Команда /hwcastle (/hwc, /castle).
 */
public final class HwCastleCommand implements TabExecutor {

    private static final String ADMIN = "miragehwcastle.admin";

    private final MirageHWCastle plugin;

    public HwCastleCommand(MirageHWCastle plugin) {
        this.plugin = plugin;
    }

    private Lang lang() {
        return plugin.lang();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("about")) {
            about(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            lang().send(sender, "no-permission");
            return true;
        }
        if (a.length == 0) {
            lang().send(sender, "help");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "bind" -> bind(sender, a);
            case "unbind" -> unbind(sender);
            case "points" -> points(sender);
            case "spawn" -> spawn(sender, a);
            case "clear" -> lang().send(sender, "clear.done", "count", plugin.shulkers().clearAll());
            case "level" -> level(sender, a);
            case "loot" -> loot(sender, a);
            case "event" -> event(sender, a);
            case "paste" -> paste(sender, a);
            default -> lang().send(sender, "help");
        }
        return true;
    }

    // ---------------------------------------------------------------- простые команды

    private void about(CommandSender sender) {
        sender.sendMessage(Text.color("&#00ACFF⌈ &fMirageHWCastle &#00ACFFv" + plugin.getPluginMeta().getVersion() + " &f⌋"));
        sender.sendMessage(Text.color(Brand.line()));
    }

    private void reload(CommandSender sender) {
        plugin.reloadAsync(
                () -> lang().send(sender, "reload.ok"),
                err -> lang().send(sender, "reload.error", "error", String.valueOf(err.getMessage())));
    }

    // ---------------------------------------------------------------- привязка блоков

    private void bind(CommandSender sender, String[] a) {
        if (!(sender instanceof Player player)) {
            lang().send(sender, "player-only");
            return;
        }
        Block block = player.getTargetBlockExact(plugin.settings().spawn().bindReach());
        if (block == null || block.getType().isAir()) {
            lang().send(player, "bind.no-block");
            return;
        }
        if (Tag.SHULKER_BOXES.isTagged(block.getType())) {
            lang().send(player, "bind.is-shulker");
            return;
        }
        String levelId = null;
        if (a.length >= 2 && !a[1].equalsIgnoreCase("random")) {
            Optional<ShulkerLevel> level = plugin.levels().get(a[1]);
            if (level.isEmpty() || level.get().eventOnly()) {
                lang().send(player, "level.unknown", "id", a[1]);
                return;
            }
            levelId = level.get().id();
        }
        ShulkerPoint point = new ShulkerPoint(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                levelId, block.getBlockData().getAsString());
        if (!plugin.points().add(point)) {
            lang().send(player, "bind.already");
            return;
        }
        plugin.store().savePointsAsync(plugin.points().all());
        lang().send(player, "bind.ok", "x", point.x(), "y", point.y(), "z", point.z(),
                "level", levelId == null ? lang().raw("points.random") : levelId);
        if (plugin.settings().spawn().spawnOnBind()) {
            plugin.shulkers().spawnAt(point, plugin.shulkers().levelFor(point));
        }
    }

    private void unbind(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            lang().send(sender, "player-only");
            return;
        }
        Block block = player.getTargetBlockExact(plugin.settings().spawn().bindReach());
        if (block == null) {
            lang().send(player, "bind.no-block");
            return;
        }
        Optional<ShulkerPoint> removed = plugin.points().remove(block);
        if (removed.isEmpty()) {
            lang().send(player, "unbind.not-bound");
            return;
        }
        plugin.shulkers().removeAtPoint(removed.get());
        plugin.store().savePointsAsync(plugin.points().all());
        lang().send(player, "unbind.ok");
    }

    private void points(CommandSender sender) {
        List<ShulkerPoint> list = new ArrayList<>(plugin.points().all());
        lang().send(sender, "points.header", "count", list.size(), "active", plugin.shulkers().activeCount());
        int shown = Math.min(list.size(), 40);
        for (int i = 0; i < shown; i++) {
            ShulkerPoint p = list.get(i);
            lang().send(sender, "points.line", "n", i + 1, "world", p.world(), "x", p.x(), "y", p.y(), "z", p.z(),
                    "level", p.levelId() == null ? lang().raw("points.random") : p.levelId());
        }
        if (list.size() > shown) {
            lang().send(sender, "points.more", "count", list.size() - shown);
        }
    }

    // ---------------------------------------------------------------- спавн

    private void spawn(CommandSender sender, String[] a) {
        ShulkerLevel forced = null;
        if (a.length >= 2 && !a[1].equalsIgnoreCase("random")) {
            Optional<ShulkerLevel> level = plugin.levels().get(a[1]);
            if (level.isEmpty()) {
                lang().send(sender, "level.unknown", "id", a[1]);
                return;
            }
            forced = level.get();
        }
        int limit = 1;
        if (a.length >= 3) {
            if (a[2].equalsIgnoreCase("all")) {
                limit = Integer.MAX_VALUE;
            } else {
                try {
                    limit = Math.max(1, Integer.parseInt(a[2]));
                } catch (NumberFormatException e) {
                    lang().send(sender, "level.bad-value", "value", a[2]);
                    return;
                }
            }
        }
        int spawned = plugin.shulkers().spawn(limit, forced);
        lang().send(sender, spawned == 0 ? "spawn.none" : "spawn.ok", "count", spawned);
    }

    // ---------------------------------------------------------------- уровни

    private void level(CommandSender sender, String[] a) {
        if (a.length < 2) {
            lang().send(sender, "level.usage");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                lang().send(sender, "level.list-header");
                for (ShulkerLevel l : plugin.levels().all()) {
                    lang().send(sender, "level.list-line", "id", l.id(), "name", l.displayName(), "hits", l.hits(),
                            "weight", l.weight(), "fail", l.failChance(), "count", l.loot().size(),
                            "color", l.dye().name());
                }
            }
            case "info" -> levelInfo(sender, a);
            case "create" -> levelCreate(sender, a);
            case "delete" -> levelDelete(sender, a);
            case "set" -> levelSet(sender, a);
            default -> lang().send(sender, "level.usage");
        }
    }

    private void levelInfo(CommandSender sender, String[] a) {
        if (a.length < 3) {
            lang().send(sender, "level.usage");
            return;
        }
        Optional<ShulkerLevel> opt = plugin.levels().get(a[2]);
        if (opt.isEmpty()) {
            lang().send(sender, "level.unknown", "id", a[2]);
            return;
        }
        ShulkerLevel l = opt.get();
        lang().send(sender, "level.info", "id", l.id(), "name", l.displayName(), "hits", l.hits(),
                "weight", l.weight(), "fail", l.failChance(), "count", l.loot().size(), "color", l.dye().name(),
                "min", l.minItems(), "max", l.maxItems());
    }

    private void levelCreate(CommandSender sender, String[] a) {
        if (a.length < 5) {
            lang().send(sender, "level.usage");
            return;
        }
        String id = a[2].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,24}")) {
            lang().send(sender, "level.bad-id");
            return;
        }
        if (plugin.levels().get(id).isPresent()) {
            lang().send(sender, "level.exists", "id", id);
            return;
        }
        try {
            int hits = Integer.parseInt(a[3]);
            DyeColor dye = DyeColor.valueOf(a[4].toUpperCase(Locale.ROOT));
            int weight = a.length >= 6 ? Integer.parseInt(a[5]) : 10;
            String name = "&#00ACFF" + Character.toUpperCase(id.charAt(0)) + id.substring(1);
            ShulkerLevel level = new ShulkerLevel(id, name, dye, Math.max(1, hits), Math.max(0, weight), 0, 1, 4, false);
            plugin.levels().put(level);
            plugin.store().saveLevelsAsync(plugin.levels().all());
            lang().send(sender, "level.created", "id", id);
        } catch (IllegalArgumentException e) {
            lang().send(sender, "level.bad-value", "value", a[3] + " " + a[4]);
        }
    }

    private void levelDelete(CommandSender sender, String[] a) {
        if (a.length < 3) {
            lang().send(sender, "level.usage");
            return;
        }
        Optional<ShulkerLevel> opt = plugin.levels().get(a[2]);
        if (opt.isEmpty()) {
            lang().send(sender, "level.unknown", "id", a[2]);
            return;
        }
        if (opt.get().id().equals(LevelRegistry.EMERALD_ID)) {
            lang().send(sender, "level.cannot-delete");
            return;
        }
        plugin.shulkers().removeByLevel(opt.get().id());
        plugin.levels().remove(opt.get().id());
        plugin.store().saveLevelsAsync(plugin.levels().all());
        lang().send(sender, "level.deleted", "id", opt.get().id());
    }

    private void levelSet(CommandSender sender, String[] a) {
        if (a.length < 5) {
            lang().send(sender, "level.usage");
            return;
        }
        Optional<ShulkerLevel> opt = plugin.levels().get(a[2]);
        if (opt.isEmpty()) {
            lang().send(sender, "level.unknown", "id", a[2]);
            return;
        }
        ShulkerLevel level = opt.get();
        String field = a[3].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
        try {
            switch (field) {
                case "name" -> level.setDisplayName(value);
                case "color" -> level.setDye(DyeColor.valueOf(value.toUpperCase(Locale.ROOT)));
                case "weight" -> level.setWeight(Integer.parseInt(value));
                case "min" -> level.setMinItems(Integer.parseInt(value));
                case "max" -> level.setMaxItems(Integer.parseInt(value));
                case "hits", "fail" -> {
                    if (level.eventOnly()) {
                        lang().send(sender, "level.event-managed");
                        return;
                    }
                    if (field.equals("hits")) {
                        level.setHits(Integer.parseInt(value));
                    } else {
                        level.setFailChance(Double.parseDouble(value));
                    }
                }
                default -> {
                    lang().send(sender, "level.usage");
                    return;
                }
            }
        } catch (IllegalArgumentException e) {
            lang().send(sender, "level.bad-value", "value", value);
            return;
        }
        plugin.store().saveLevelsAsync(plugin.levels().all());
        lang().send(sender, "level.updated", "id", level.id(), "field", field, "value", value);
    }

    // ---------------------------------------------------------------- лут, ивент, замок

    private void loot(CommandSender sender, String[] a) {
        if (!(sender instanceof Player player)) {
            lang().send(sender, "player-only");
            return;
        }
        if (a.length >= 2) {
            Optional<ShulkerLevel> level = plugin.levels().get(a[1]);
            if (level.isEmpty()) {
                lang().send(player, "level.unknown", "id", a[1]);
                return;
            }
            plugin.lootGui().openEditor(player, level.get());
        } else {
            plugin.lootGui().openSelector(player);
        }
    }

    private void event(CommandSender sender, String[] a) {
        String sub = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "start" -> {
                if (!plugin.settings().event().enabled()) {
                    lang().send(sender, "event.disabled");
                    return;
                }
                int seconds = 0;
                if (a.length >= 3) {
                    try {
                        seconds = Math.max(10, Integer.parseInt(a[2]));
                    } catch (NumberFormatException e) {
                        lang().send(sender, "level.bad-value", "value", a[2]);
                        return;
                    }
                }
                if (!plugin.events().begin(seconds)) {
                    lang().send(sender, "event.already");
                }
            }
            case "stop" -> {
                if (!plugin.events().isActive()) {
                    lang().send(sender, "event.not-active");
                    return;
                }
                plugin.events().end(false);
            }
            default -> {
                int remaining = plugin.events().remainingSeconds();
                lang().send(sender, plugin.events().isActive() ? "event.status-active" : "event.status-idle",
                        "time", String.format("%02d:%02d", remaining / 60, remaining % 60),
                        "active", plugin.shulkers().activeCount());
            }
        }
    }

    private void paste(CommandSender sender, String[] a) {
        var castle = plugin.settings().castle();
        String worldName = a.length >= 2 ? a[1] : castle.world();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            lang().send(sender, "paste.no-world", "world", worldName);
            return;
        }
        int y = castle.pasteY();
        if (a.length >= 3) {
            try {
                y = Integer.parseInt(a[2]);
            } catch (NumberFormatException e) {
                lang().send(sender, "level.bad-value", "value", a[2]);
                return;
            }
        }
        plugin.paster().paste(sender, world, y);
    }

    // ---------------------------------------------------------------- автодополнение

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] a) {
        if (!sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (a.length == 1) {
            return filter(List.of("help", "about", "reload", "bind", "unbind", "points", "spawn", "clear",
                    "level", "loot", "event", "paste"), a[0]);
        }
        List<String> ids = plugin.levels().all().stream().map(ShulkerLevel::id).toList();
        List<String> dyes = Arrays.stream(DyeColor.values()).map(d -> d.name().toLowerCase(Locale.ROOT)).toList();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "bind" -> {
                if (a.length == 2) {
                    return filter(withRandom(ids), a[1]);
                }
            }
            case "spawn" -> {
                if (a.length == 2) {
                    return filter(withRandom(ids), a[1]);
                }
                if (a.length == 3) {
                    return filter(List.of("1", "5", "all"), a[2]);
                }
            }
            case "loot" -> {
                if (a.length == 2) {
                    return filter(ids, a[1]);
                }
            }
            case "event" -> {
                if (a.length == 2) {
                    return filter(List.of("start", "stop", "status"), a[1]);
                }
            }
            case "paste" -> {
                if (a.length == 2) {
                    return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), a[1]);
                }
            }
            case "level" -> {
                return levelTab(a, ids, dyes);
            }
            default -> {
            }
        }
        return List.of();
    }

    private List<String> levelTab(String[] a, List<String> ids, List<String> dyes) {
        if (a.length == 2) {
            return filter(List.of("list", "info", "create", "delete", "set"), a[1]);
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        if (a.length == 3 && (sub.equals("info") || sub.equals("delete") || sub.equals("set"))) {
            return filter(ids, a[2]);
        }
        if (sub.equals("create") && a.length == 5) {
            return filter(dyes, a[4]);
        }
        if (sub.equals("set") && a.length == 4) {
            return filter(List.of("name", "color", "hits", "weight", "fail", "min", "max"), a[3]);
        }
        if (sub.equals("set") && a.length == 5 && a[3].equalsIgnoreCase("color")) {
            return filter(dyes, a[4]);
        }
        return List.of();
    }

    private static List<String> withRandom(List<String> ids) {
        List<String> list = new ArrayList<>(ids);
        list.add("random");
        return list;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
