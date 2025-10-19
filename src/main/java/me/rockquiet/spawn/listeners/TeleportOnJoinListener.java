package me.rockquiet.spawn.listeners;

import me.rockquiet.spawn.SpawnHandler;
import me.rockquiet.spawn.configuration.FileManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class TeleportOnJoinListener implements Listener {

    private final FileManager fileManager;
    private final SpawnHandler spawnHandler;

    public TeleportOnJoinListener(FileManager fileManager,
                                  SpawnHandler spawnHandler) {
        this.fileManager = fileManager;
        this.spawnHandler = spawnHandler;
    }

    @EventHandler
public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
    Player player = event.getPlayer();
    YamlConfiguration config = fileManager.getYamlConfig();

    boolean allJoins = config.getBoolean("teleport-on-join.enabled", false);
    boolean firstJoin = config.getBoolean("teleport-on-first-join.enabled", false);

    if (!allJoins && !(firstJoin && !player.hasPlayedBefore())) return;
    if (player.hasPermission("spawn.bypass.join-teleport")) return;
    if (!player.hasPermission("spawn.bypass.world-list") && !spawnHandler.isEnabledInWorld(player.getWorld())) return;
    if (!spawnHandler.spawnExists()) return;

    org.bukkit.Bukkit.getScheduler().runTask(me.rockquiet.spawn.Spawn.getPlugin(me.rockquiet.spawn.Spawn.class), () -> {
        if (!player.isOnline()) return;
        spawnHandler.teleportPlayer(player);
    });
}
