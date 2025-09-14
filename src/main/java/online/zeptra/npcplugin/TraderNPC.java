package online.zeptra.npcplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;

import java.util.List;
import java.util.UUID;

public class TraderNPC {
    private final String id;
    private final String name;
    private final Location location;
    private LivingEntity entity;
    private boolean enabled;
    private final ConfigManager config;

    public TraderNPC(String id, String name, Location location, boolean enabled, ConfigManager config) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.enabled = enabled;
        this.config = config;
    }

    public boolean spawn() {
        try {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }

            String entityTypeStr = config.getNPCEntityType(id);
            EntityType entityType = EntityType.valueOf(entityTypeStr.toUpperCase());

            entity = (LivingEntity) location.getWorld().spawnEntity(location, entityType);

            entity.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
            entity.setCustomNameVisible(true);
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setSilent(true);

            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;

                String professionStr = config.getNPCProfession(id);
                try {
                    Villager.Profession profession = Villager.Profession.valueOf(professionStr.toUpperCase());
                    villager.setProfession(profession);
                } catch (IllegalArgumentException e) {
                    villager.setProfession(Villager.Profession.FARMER);
                }

                villager.setVillagerType(Villager.Type.PLAINS);
            }

            config.debugLog("Spawned NPC: " + id + " at " + location);
            return true;

        } catch (Exception e) {
            config.debugLog("Failed to spawn NPC " + id + ": " + e.getMessage());
            return false;
        }
    }

    public void remove() {
        if (entity != null && entity.isValid()) {
            entity.remove();
            config.debugLog("Removed NPC: " + id);
        }
        entity = null;
    }

    public void lookAt(Location target) {
        if (entity == null || !entity.isValid()) return;

        Location npcLoc = entity.getLocation();
        double dx = target.getX() - npcLoc.getX();
        double dz = target.getZ() - npcLoc.getZ();

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        npcLoc.setYaw(yaw);
        entity.teleport(npcLoc);
    }

    public double getItemPrice(String itemType) {
        if (config.hasCustomPrices(id)) {
            double customPrice = config.getNPCItemPrice(id, itemType);
            if (customPrice > 0) {
                return customPrice;
            }
        }
        return config.getItemPrice(itemType);
    }

    public List<String> getCustomCommands() {
        return config.getNPCCustomCommands(id);
    }

    public boolean hasCustomCommands() {
        List<String> commands = getCustomCommands();
        return commands != null && !commands.isEmpty();
    }

    public String getGreeting() {
        return ChatColor.translateAlternateColorCodes('&', config.getNPCGreeting(id));
    }

    public void updateFromConfig() {
        this.enabled = config.isNPCEnabled(id);

        if (entity != null && entity.isValid()) {
            entity.setCustomName(ChatColor.translateAlternateColorCodes('&', config.getNPCName(id)));
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public boolean isEnabled() {
        return enabled && config.isNPCEnabled(id);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isValid() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public UUID getEntityUUID() {
        return entity != null ? entity.getUniqueId() : null;
    }

    public boolean isThisEntity(LivingEntity checkEntity) {
        return entity != null && entity.equals(checkEntity);
    }

    @Override
    public String toString() {
        return "TraderNPC{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", valid=" + isValid() +
                '}';
    }
}