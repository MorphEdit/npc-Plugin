package online.zeptra.npcplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NPCManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<String, TraderNPC> npcs;
    private BukkitRunnable lookTask;

    public NPCManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.npcs = new HashMap<>();
    }

    public void loadNPCs() {
        if (!config.isNPCSystemEnabled()) {
            plugin.getLogger().info("NPC system is disabled in config");
            return;
        }

        ConfigurationSection npcsSection = config.getNPCsConfig().getConfigurationSection("npcs");
        if (npcsSection == null) {
            plugin.getLogger().warning("No NPCs section found in npcs.yml");
            return;
        }

        int loaded = 0;
        for (String npcId : npcsSection.getKeys(false)) {
            if (loadNPC(npcId)) {
                loaded++;
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " NPCs");
        startLookTask();
    }

    public boolean loadNPC(String npcId) {
        try {
            if (!config.isNPCEnabled(npcId)) {
                config.debugLog("NPC " + npcId + " is disabled, skipping");
                return false;
            }

            ConfigurationSection locationSection = config.getNPCsConfig()
                    .getConfigurationSection("npcs." + npcId + ".location");

            if (locationSection == null) {
                plugin.getLogger().warning("No location found for NPC: " + npcId);
                return false;
            }

            String worldName = locationSection.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found for NPC: " + npcId);
                return false;
            }

            double x = locationSection.getDouble("x", 0);
            double y = locationSection.getDouble("y", 64);
            double z = locationSection.getDouble("z", 0);
            float yaw = (float) locationSection.getDouble("yaw", 0);
            float pitch = (float) locationSection.getDouble("pitch", 0);

            Location location = new Location(world, x, y, z, yaw, pitch);

            String name = config.getNPCName(npcId);
            TraderNPC npc = new TraderNPC(npcId, name, location, true, config);

            if (npc.spawn()) {
                npcs.put(npcId, npc);
                config.debugLog("Loaded NPC: " + npcId);
                return true;
            } else {
                plugin.getLogger().warning("Failed to spawn NPC: " + npcId);
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error loading NPC " + npcId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean createNPC(String npcId, String name, Player player) {
        if (npcs.containsKey(npcId)) {
            return false;
        }

        Location location = player.getLocation();

        String path = "npcs." + npcId;
        config.getNPCsConfig().set(path + ".enabled", true);
        config.getNPCsConfig().set(path + ".name", name);
        config.getNPCsConfig().set(path + ".entity-type", "VILLAGER");
        config.getNPCsConfig().set(path + ".profession", "FARMER");
        config.getNPCsConfig().set(path + ".location.world", location.getWorld().getName());
        config.getNPCsConfig().set(path + ".location.x", location.getX());
        config.getNPCsConfig().set(path + ".location.y", location.getY());
        config.getNPCsConfig().set(path + ".location.z", location.getZ());
        config.getNPCsConfig().set(path + ".location.yaw", location.getYaw());
        config.getNPCsConfig().set(path + ".location.pitch", location.getPitch());
        config.getNPCsConfig().set(path + ".settings.greeting", "&aHello! Right-click to sell your items!");
        config.getNPCsConfig().set(path + ".settings.custom-prices", false);

        config.saveNPCsConfig();
        return loadNPC(npcId);
    }

    public boolean removeNPC(String npcId) {
        TraderNPC npc = npcs.remove(npcId);
        if (npc != null) {
            npc.remove();

            config.getNPCsConfig().set("npcs." + npcId, null);
            config.saveNPCsConfig();

            config.debugLog("Removed NPC: " + npcId);
            return true;
        }
        return false;
    }

    public TraderNPC getNPC(String npcId) {
        return npcs.get(npcId);
    }

    public TraderNPC getNPCByEntity(LivingEntity entity) {
        for (TraderNPC npc : npcs.values()) {
            if (npc.isThisEntity(entity)) {
                return npc;
            }
        }
        return null;
    }

    public Set<String> getNPCIds() {
        return npcs.keySet();
    }

    public Map<String, TraderNPC> getAllNPCs() {
        return new HashMap<>(npcs);
    }

    public boolean toggleNPC(String npcId) {
        TraderNPC npc = npcs.get(npcId);
        if (npc != null) {
            boolean newState = !config.isNPCEnabled(npcId);
            config.getNPCsConfig().set("npcs." + npcId + ".enabled", newState);
            config.saveNPCsConfig();

            if (newState) {
                npc.spawn();
            } else {
                npc.remove();
            }

            npc.updateFromConfig();
            return true;
        }
        return false;
    }

    public void reloadNPCs() {
        removeAllNPCs();

        if (lookTask != null) {
            lookTask.cancel();
        }

        config.reloadConfigs();
        loadNPCs();
    }

    public void removeAllNPCs() {
        for (TraderNPC npc : npcs.values()) {
            npc.remove();
        }
        npcs.clear();

        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }

        config.debugLog("Removed all NPCs");
    }

    private void startLookTask() {
        if (lookTask != null) {
            lookTask.cancel();
        }

        lookTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (TraderNPC npc : npcs.values()) {
                    if (!npc.isValid() || !npc.isEnabled()) {
                        continue;
                    }

                    Player closest = findClosestPlayer(npc);
                    if (closest != null) {
                        npc.lookAt(closest.getLocation());
                    }
                }
            }
        };

        long interval = config.getNPCUpdateInterval();
        lookTask.runTaskTimer(plugin, 0L, interval);
        config.debugLog("Started NPC look task with interval: " + interval);
    }

    private Player findClosestPlayer(TraderNPC npc) {
        if (!npc.isValid()) return null;

        Location npcLocation = npc.getEntity().getLocation();
        double lookDistance = config.getNPCLookDistance();
        Player closestPlayer = null;
        double closestDistance = lookDistance;

        for (Player player : npcLocation.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(npcLocation);
            if (distance <= lookDistance && distance < closestDistance) {
                closestPlayer = player;
                closestDistance = distance;
            }
        }

        return closestPlayer;
    }

    public int getNPCCount() {
        return npcs.size();
    }

    public int getEnabledNPCCount() {
        int count = 0;
        for (TraderNPC npc : npcs.values()) {
            if (npc.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    public void validateNPCs() {
        int respawned = 0;
        for (TraderNPC npc : npcs.values()) {
            if (npc.isEnabled() && !npc.isValid()) {
                if (npc.spawn()) {
                    respawned++;
                }
            }
        }

        if (respawned > 0) {
            plugin.getLogger().info("Respawned " + respawned + " NPCs");
        }
    }
}