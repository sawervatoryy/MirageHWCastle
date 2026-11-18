package ru.miragestudio.hwcastle.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.miragestudio.hwcastle.MirageHWCastle;
import ru.miragestudio.hwcastle.model.ActiveShulker;

/**
 * События, связанные с шалкерами: удары, защита блоков, чистка после сбоя.
 */
public final class ShulkerListener implements Listener {

    private final MirageHWCastle plugin;

    public ShulkerListener(MirageHWCastle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = e.getClickedBlock();
        if (block == null) {
            return;
        }
        ActiveShulker shulker = plugin.shulkers().activeAt(block);
        if (shulker == null) {
            return;
        }
        Action action = e.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true); // шалкер нельзя открыть
            return;
        }
        // ЛКМ: в режиме CLICK удар считается по клику. В режиме BREAK клик НЕ отменяем —
        // иначе сервер запретит начать ломание и BlockBreakEvent никогда не сработает.
        if (plugin.settings().hit().clickMode()) {
            e.setCancelled(true);
            plugin.shulkers().hit(e.getPlayer(), shulker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        ActiveShulker shulker = plugin.shulkers().activeAt(e.getBlock());
        if (shulker == null) {
            return;
        }
        e.setCancelled(true);
        e.setDropItems(false);
        if (!plugin.settings().hit().clickMode()) {
            plugin.shulkers().hit(e.getPlayer(), shulker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.shulkers().activeAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.shulkers().activeAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> plugin.shulkers().activeAt(b) != null)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> plugin.shulkers().activeAt(b) != null)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) {
            plugin.shulkers().reconcileChunk(e.getChunk());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.events().onJoin(e.getPlayer());
    }
}
