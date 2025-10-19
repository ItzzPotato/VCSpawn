package me.rockquiet.spawn;

import me.rockquiet.spawn.configuration.ConfigFile;
import me.rockquiet.spawn.configuration.FileManager;
import me.rockquiet.spawn.configuration.Messages;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

public class SpawnHandler {

    private static final String WORLD_KEY = "spawn.world";
    private static final String X_KEY = "spawn.x";
    private static final String Y_KEY = "spawn.y";
    private static final String Z_KEY = "spawn.z";
    private static final String YAW_KEY = "spawn.yaw";
    private static final String PITCH_KEY = "spawn.pitch";

    private final Spawn plugin;
    private final FileManager fileManager;
    private final Messages messageManager;

    private Location spawnLocation;

    public SpawnHandler(Spawn plugin, FileManager fileManager, Messages messageManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        this.messageManager = messageManager;

        this.spawnLocation = loadSpawn();
    }

    public Location getSpawn() {
        return spawnLocation;
    }

    public void setSpawn(Location newSpawnLocation, boolean saveToFile) {
        spawnLocation = newSpawnLocation;
        if (saveToFile) {
            saveSpawn(newSpawnLocation);
        }
    }

    private void saveSpawn(Location newSpawnLocation) {
        final ConfigFile location = fileManager.getLocation();
        final YamlConfiguration locationYaml = location.get();

        locationYaml.set(WORLD_KEY, newSpawnLocation.getWorld().getName());
        locationYaml.set(X_KEY, newSpawnLocation.getX());
        locationYaml.set(Y_KEY, newSpawnLocation.getY());
        locationYaml.set(Z_KEY, newSpawnLocation.getZ());
        locationYaml.set(YAW_KEY, newSpawnLocation.getYaw());
        locationYaml.set(PITCH_KEY, newSpawnLocation.getPitch());

        location.save();
        location.reload();
    }

    public Location loadSpawn() {
        if (!isLocationConfigValid()) {
            return null;
        }

        final YamlConfiguration location = fileManager.getYamlLocation();
        World world = Bukkit.getWorld(location.getString(WORLD_KEY));
        if (world == null) {
            plugin.getLogger().warning("Spawn world '" + location.getString(WORLD_KEY) + "' is not loaded. Teleports will be skipped.");
            return null;
        }
        return new Location(
                world,
                location.getDouble(X_KEY),
                location.getDouble(Y_KEY),
                location.getDouble(Z_KEY),
                (float) location.getDouble(YAW_KEY),
                (float) location.getDouble(PITCH_KEY)
        );
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isEnabledInWorld(World world) {
        final YamlConfiguration config = fileManager.getYamlConfig();
        final List<String> worldList = config.getStringList("plugin.world-list");
        final String worldName = world.getName();

        switch (config.getString("plugin.list-type", "disabled").toLowerCase(Locale.ROOT)) {
            case "whitelist":
                return worldList.contains(worldName);
            case "blacklist":
                return !worldList.contains(worldName);
            default:
                return true;
        }
    }

    public boolean isAllowedGameMode(Player player) {
        final YamlConfiguration config = fileManager.getYamlConfig();

        if (player.hasPermission("spawn.bypass.gamemode-restriction") || !config.getBoolean("plugin.gamemode-restricted")) {
            return true;
        }

        return config.getStringList("plugin.gamemode-list").stream().anyMatch(s -> s.equalsIgnoreCase(player.getGameMode().toString()));
    }

    public void teleportPlayer(Player player) {
        if (!spawnExists()) {
            messageManager.sendMessage(player, "no-spawn");
            return;
        }

        final YamlConfiguration config = fileManager.getYamlConfig();

        if (!config.getBoolean("fall-damage.enabled")) {
            player.setFallDistance(0F);
        }

        if (config.getBoolean("use-player-head-rotation.enabled")) {
            Location location = spawnLocation.clone();
            location.setDirection(player.getLocation().getDirection());
            player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else {
            player.teleport(spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        spawnParticles(player);
        playSound(player);

        messageManager.sendMessage(player, "teleport");
    }

    public void spawnParticles(Player player) {
        final YamlConfiguration config = fileManager.getYamlConfig();

        if (config.getBoolean("particles.enabled")) {
            String particleName = config.getString("particles.particle");
            int particleAmount = config.getInt("particles.amount");
            try {
                if (Spawn.getServerVersion().getMinor() > 8) {
                    Particle particle = Particle.valueOf(particleName);
                    // display particles for player that teleported
                    player.spawnParticle(particle, spawnLocation, particleAmount);
                    // display particles for other players
                    player.getNearbyEntities(16, 16, 16).stream()
                            .filter(entity -> entity instanceof Player && ((Player) entity).canSee(player))
                            .forEach(entity -> ((Player) entity).spawnParticle(particle, spawnLocation, particleAmount));
                } else {
                    // workaround for 1.8
                    Effect effect = Effect.valueOf(particleName);
                    // you probably do not want to accidentally crash your server
                    int particleAmountLegacy = Math.min(particleAmount, 2000);
                    // display particles for player that teleported
                    for (int p = 0; p < particleAmountLegacy; p++) {
                        player.playEffect(spawnLocation, effect, null);
                    }
                    // display particles for other players
                    player.getNearbyEntities(16, 16, 16).stream()
                            .filter(entity -> entity instanceof Player && ((Player) entity).canSee(player))
                            .forEach(entity -> {
                                for (int p = 0; p < particleAmountLegacy; p++) {
                                    ((Player) entity).playEffect(spawnLocation, effect, null);
                                }
                            });
                }
            } catch (Exception e) {
                plugin.getLogger().warning("The particle " + particleName + " does not exist in this Minecraft version!");
            }
        }
    }

    public void playSound(Player player) {
        final YamlConfiguration config = fileManager.getYamlConfig();

        if (config.getBoolean("sounds.enabled")) {
            String sound = config.getString("sounds.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
            float volume = (float) config.getDouble("sounds.volume", 1.0);
            float pitch = (float) config.getDouble("sounds.pitch", 1.0);
            try {
                player.playSound(player.getLocation(), Sound.valueOf(sound.toUpperCase(Locale.ROOT)), volume, pitch);
            } catch (Exception e) {
                plugin.getLogger().warning("The sound " + sound + " does not exist in this Minecraft version!");
            }
        }
    }

    public boolean spawnExists() {
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            spawnLocation = loadSpawn();
        }

        return spawnLocation != null && spawnLocation.getWorld() != null;
    }

    private boolean isLocationConfigValid() {
        final ConfigFile locationFile = fileManager.getLocation();

        return locationFile.containsIgnoreDefault(WORLD_KEY)
                && locationFile.containsIgnoreDefault(X_KEY)
                && locationFile.containsIgnoreDefault(Y_KEY)
                && locationFile.containsIgnoreDefault(Z_KEY)
                && locationFile.containsIgnoreDefault(YAW_KEY)
                && locationFile.containsIgnoreDefault(PITCH_KEY);
    }
}
