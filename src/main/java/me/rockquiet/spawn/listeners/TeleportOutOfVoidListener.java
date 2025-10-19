package me.rockquiet.spawn.listeners;

import me.rockquiet.spawn.SpawnHandler;
import me.rockquiet.spawn.configuration.FileManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class TeleportOutOfVoidListener implements Listener {

    private final FileManager fileManager;
    private final SpawnHandler spawnHandler;

    public TeleportOutOfVoidListener(FileManager fileManager,
                                     SpawnHandler spawnHandler) {
        this.fileManager = fileManager;
        this.spawnHandler = spawnHandler;
    }

    @EventHandler
    public void playerInVoid(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    var to = event.getTo();
    var from = event.getFrom();
    if (to == null) return;

    if (player.hasPermission("spawn.bypass.void-teleport") 
            || (from.getWorld().equals(to.getWorld()) && from.distanceSquared(to) < 0.0001)
            || from.getY() == to.getY()) {
        return;
    }

    if (!player.hasPermission("spawn.bypass.world-list") && !spawnHandler.isEnabledInWorld(player.getWorld())) {
        return;
    }

    YamlConfiguration config = fileManager.getYamlConfig();

    if (config.getBoolean("teleport-out-of-void.enabled") && (to.getBlockY() <= config.getInt("teleport-out-of-void.check-height", -64))) {
        if (!spawnHandler.spawnExists()) return;
        spawnHandler.teleportPlayer(player);
    }
}
