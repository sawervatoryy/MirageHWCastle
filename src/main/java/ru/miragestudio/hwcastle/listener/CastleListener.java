package ru.miragestudio.hwcastle.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.miragestudio.hwcastle.MirageHWCastle;

import java.util.Locale;

/**
 * Защита замка: запрет строительства и приватов, постоянный PvP.
 * Обход — право {@code miragehwcastle.bypass}.
 */
public final class CastleListener implements Listener {

    public static final String BYPASS = "miragehwcastle.bypass";

    private final MirageHWCastle plugin;

    public CastleListener(MirageHWCastle plugin) {
        this.plugin = plugin;
    }

    private boolean denied(Player player, Location loc) {
        var castle = plugin.settings().castle();
        return castle.denyBuild() && castle.contains(loc) && !player.hasPermission(BYPASS);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            plugin.lang().actionBar(e.getPlayer(), "castle.no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            plugin.lang().actionBar(e.getPlayer(), "castle.no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            plugin.lang().actionBar(e.getPlayer(), "castle.no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            plugin.lang().actionBar(e.getPlayer(), "castle.no-build");
        }
    }

    /** PvP в замке включён всегда: отменённый другими плагинами урон между игроками возвращается. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPvp(EntityDamageByEntityEvent e) {
        var castle = plugin.settings().castle();
        if (!castle.forcePvp() || !e.isCancelled() || !(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker != null && !attacker.equals(victim)
                && castle.contains(victim.getLocation()) && castle.contains(attacker.getLocation())) {
            e.setCancelled(false);
        }
    }

    /** Блокирует команды приватов/регионов внутри замка. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        var castle = plugin.settings().castle();
        if (castle.blockedCommands().isEmpty() || player.hasPermission(BYPASS) || !castle.contains(player.getLocation())) {
            return;
        }
        String first = e.getMessage().toLowerCase(Locale.ROOT).split("\\s+")[0];
        int colon = first.indexOf(':');
        if (colon > 0) {
            first = "/" + first.substring(colon + 1); // /worldguard:rg -> /rg
        }
        for (String blocked : castle.blockedCommands()) {
            String normalized = blocked.startsWith("/") ? blocked : "/" + blocked;
            if (first.equals(normalized)) {
                e.setCancelled(true);
                plugin.lang().send(player, "castle.command-blocked");
                return;
            }
        }
    }
}
