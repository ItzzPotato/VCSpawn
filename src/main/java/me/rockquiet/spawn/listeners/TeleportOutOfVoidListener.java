package me.rockquiet.spawn.listeners;

import me.rockquiet.spawn.SpawnHandler;
import me.rockquiet.spawn.configuration.FileManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class TeleportOutOfVoidListener implements Listener {

    private final FileManager fileManager;
    private final SpawnHandler spawnHandler;

    private static final long RECENT_TELEPORT_GRACE_MILLIS = 5000L;
    private static final int SAFETY_CHECK_DEPTH = 5;

    public TeleportOutOfVoidListener(FileManager fileManager,
                                     SpawnHandler spawnHandler) {
        this.fileManager = fileManager;
        this.spawnHandler = spawnHandler;
    }

    @EventHandler
    public void playerInVoid(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;

        if (spawnHandler.wasTeleportedRecently(player, RECENT_TELEPORT_GRACE_MILLIS)) {
            return;
        }

        if (player.hasPermission("spawn.bypass.void-teleport")
                || (from.getWorld().equals(to.getWorld()) && from.distanceSquared(to) < 0.0001)
                || from.getY() == to.getY()) {
            return;
        }

        if (!player.hasPermission("spawn.bypass.world-list") && !spawnHandler.isEnabledInWorld(player.getWorld())) {
            return;
        }

        YamlConfiguration config = fileManager.getYamlConfig();

        if (!config.getBoolean("teleport-out-of-void.enabled")) {
            return;
        }

        int checkHeight = config.getInt("teleport-out-of-void.check-height", -64);
        if (!isDescendingIntoVoid(from, to, checkHeight)) {
            return;
        }

        if (!spawnHandler.spawnExists()) return;
        spawnHandler.teleportPlayer(player);
    }

    private boolean isDescendingIntoVoid(Location from, Location to, int checkHeight) {
        if (to.getWorld() == null) {
            return false;
        }

        if (to.getY() >= from.getY()) {
            return false;
        }

        if (to.getY() > checkHeight) {
            return false;
        }

        return !hasSafetyBelow(to);
    }

    private boolean hasSafetyBelow(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int minY = getWorldMinHeight(world);
        int startY = location.getBlockY();
        int lowestY = Math.max(minY, startY - SAFETY_CHECK_DEPTH);
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();

        for (int y = startY; y >= lowestY; y--) {
            Material type = world.getBlockAt(blockX, y, blockZ).getType();
            if (type.isSolid() || type == Material.WATER || type == Material.LAVA) {
                return true;
            }
        }

        return false;
    }

    private int getWorldMinHeight(World world) {
        try {
            return world.getMinHeight();
        } catch (NoSuchMethodError ignored) {
            return 0;
        }
    }
}
