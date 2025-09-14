package online.zeptra.npcplugin;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TraderNPC {
    private final String id;
    private final String name;
    private final Location location;
    private LivingEntity entity;
    private boolean enabled;
    private final ConfigManager config;

    // **แก้ไข: เพิ่มการตรวจสอบ UUID เพื่อป้องกัน spawn ซ้ำ**
    private UUID entityUUID;
    private boolean isSpawning = false;

    // Hologram support
    private List<ArmorStand> hologramLines = new ArrayList<>();
    private BukkitTask hologramUpdateTask;

    // Particle effects
    private BukkitTask particleTask;

    // Player interaction tracking
    private int dailyInteractionCount = 0;
    private long lastInteractionTime = 0;

    // NPC Skin data
    private String skinTexture;
    private String skinSignature;

    public TraderNPC(String id, String name, Location location, boolean enabled, ConfigManager config) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.enabled = enabled;
        this.config = config;
    }

    public boolean spawn() {
        // **แก้ไข: ป้องกัน spawn ซ้ำ**
        if (isSpawning) {
            config.debugLog("NPC " + id + " is already spawning, skipping...");
            return false;
        }

        if (entity != null && entity.isValid()) {
            config.debugLog("NPC " + id + " already exists and is valid, skipping spawn");
            return true;
        }

        try {
            isSpawning = true;

            // ลบ entity เก่าถ้ามี
            if (entity != null) {
                entity.remove();
                entity = null;
                entityUUID = null;
            }

            String entityTypeStr = config.getNPCEntityType(id);
            EntityType entityType;

            try {
                entityType = EntityType.valueOf(entityTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                config.debugLog("Invalid entity type " + entityTypeStr + " for NPC " + id + ", using VILLAGER");
                entityType = EntityType.VILLAGER;
            }

            // **แก้ไข: ตรวจสอบให้แน่ใจว่า location ถูกต้อง**
            if (location.getWorld() == null) {
                config.debugLog("World is null for NPC " + id);
                return false;
            }

            entity = (LivingEntity) location.getWorld().spawnEntity(location, entityType);
            entityUUID = entity.getUniqueId();

            setupNPCProperties();
            applySkin();

            if (config.areHologramsEnabled()) {
                createHologram();
                startHologramUpdateTask();
            }

            if (config.areParticlesEnabled()) {
                startParticleEffects();
            }

            config.debugLog("Successfully spawned NPC: " + id + " with UUID: " + entityUUID);
            return true;

        } catch (Exception e) {
            config.debugLog("Failed to spawn NPC " + id + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            isSpawning = false;
        }
    }

    private void setupNPCProperties() {
        if (entity == null) return;

        entity.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
        entity.setCustomNameVisible(true);
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setCollidable(false);
        entity.setSilent(true);
        entity.setGravity(false);

        // **แก้ไข: เพิ่ม persistent เพื่อไม่ให้หายเวลา chunk unload**
        entity.setPersistent(true);

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
            villager.setVillagerLevel(5); // Master level
        }

        // Remove AI from other entity types
        if (entity instanceof Mob) {
            Mob mob = (Mob) entity;
            mob.setTarget(null);
        }
    }

    private void applySkin() {
        if (!config.areSkinsEnabled()) {
            return;
        }

        String skinName = config.getNPCSkin(id);

        // For player-type entities, apply skin
        if (entity instanceof Player) {
            // This would require additional skin API implementation
            config.debugLog("Player skin application not implemented for " + id);
        }

        // For other entities, we can change equipment or properties
        if (entity instanceof Villager && !skinName.equals("MHF_Villager")) {
            // Could add custom equipment or modify appearance
            applyCustomAppearance(skinName);
        }
    }

    private void applyCustomAppearance(String skinName) {
        // Custom appearance based on skin name
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;

            // Example: Give different equipment based on skin
            switch (skinName.toLowerCase()) {
                case "mhf_steve":
                    living.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                    break;
                case "mhf_alex":
                    living.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                    break;
                default:
                    // Default appearance
                    break;
            }
        }
    }

    private void createHologram() {
        removeHologram();

        List<String> lines = config.getHologramLines();
        if (lines.isEmpty()) {
            return;
        }

        World world = location.getWorld();
        if (world == null) return;

        double baseY = location.getY() + config.getHologramHeightOffset();

        for (int i = 0; i < lines.size(); i++) {
            double y = baseY + (lines.size() - 1 - i) * 0.25; // 0.25 blocks between lines
            Location holoLoc = new Location(world, location.getX(), y, location.getZ());

            ArmorStand hologram = world.spawn(holoLoc, ArmorStand.class);
            setupHologramProperties(hologram, lines.get(i));
            hologramLines.add(hologram);
        }

        // Add price information if enabled
        if (config.showHologramPrices()) {
            addPriceLines(baseY - 0.5);
        }
    }

    private void setupHologramProperties(ArmorStand hologram, String text) {
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setCustomNameVisible(true);
        hologram.setInvulnerable(true);
        hologram.setCollidable(false);
        hologram.setSilent(true);
        // **แก้ไข: เพิ่ม persistent สำหรับ hologram ด้วย**
        hologram.setPersistent(true);

        String processedText = processHologramText(text);
        hologram.setCustomName(ChatColor.translateAlternateColorCodes('&', processedText));
    }

    private void addPriceLines(double startY) {
        List<String> sampleItems = getSampleItems();
        String format = config.getHologramPriceFormat();

        for (int i = 0; i < Math.min(sampleItems.size(), config.getHologramMaxItems()); i++) {
            String itemType = sampleItems.get(i);
            double price = getItemPrice(itemType);

            if (price > 0) {
                String itemName = formatItemName(itemType);
                String priceText = format
                        .replace("{item}", itemName)
                        .replace("{price}", String.format("%.2f", price));

                double y = startY - (i * 0.25);
                Location priceLoc = new Location(location.getWorld(), location.getX(), y, location.getZ());

                ArmorStand priceHolo = location.getWorld().spawn(priceLoc, ArmorStand.class);
                setupHologramProperties(priceHolo, priceText);
                hologramLines.add(priceHolo);
            }
        }
    }

    private List<String> getSampleItems() {
        // Get sample items based on NPC's specialty or common items
        List<String> items = new ArrayList<>();

        if (hasCustomPrices()) {
            // Get items from custom prices
            // This would need to be implemented based on your config structure
            items.add("IRON_ORE");
            items.add("DIAMOND");
            items.add("GOLD_INGOT");
        } else {
            // Default sample items
            items.add("IRON_ORE");
            items.add("WHEAT");
            items.add("COBBLESTONE");
        }

        return items;
    }

    private String processHologramText(String text) {
        return text
                .replace("{npc_name}", ChatColor.stripColor(name))
                .replace("{npc_id}", id)
                .replace("{daily_count}", String.valueOf(dailyInteractionCount))
                .replace("{world}", location.getWorld().getName());
    }

    private void startHologramUpdateTask() {
        if (hologramUpdateTask != null) {
            hologramUpdateTask.cancel();
        }

        int interval = config.getHologramUpdateInterval();
        hologramUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateHologramContent();
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("NPCPlugin"), 0L, interval * 20L);
    }

    private void updateHologramContent() {
        if (hologramLines.isEmpty()) {
            return;
        }

        List<String> lines = config.getHologramLines();

        for (int i = 0; i < Math.min(lines.size(), hologramLines.size()); i++) {
            ArmorStand hologram = hologramLines.get(i);
            if (hologram != null && hologram.isValid()) {
                String processedText = processHologramText(lines.get(i));
                hologram.setCustomName(ChatColor.translateAlternateColorCodes('&', processedText));
            }
        }
    }

    private void startParticleEffects() {
        if (particleTask != null) {
            particleTask.cancel();
        }

        int interval = config.getParticleInterval("npc-idle");

        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()) {
                    this.cancel();
                    return;
                }

                playIdleParticles();
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("NPCPlugin"), 0L, interval);
    }

    private void playIdleParticles() {
        if (!config.areParticlesEnabled() || entity == null) {
            return;
        }

        try {
            Location particleLoc = entity.getLocation().add(0, 1, 0);
            World world = particleLoc.getWorld();

            Particle particleType = Particle.valueOf(config.getParticleType("npc-idle"));
            int count = config.getParticleCount("npc-idle");
            double offset = config.getParticleOffset("npc-idle");

            world.spawnParticle(particleType, particleLoc, count, offset, offset, offset, 0);

        } catch (Exception e) {
            config.debugLog("Error playing idle particles for " + id + ": " + e.getMessage());
        }
    }

    public void playParticleEffect(String effectType) {
        if (!config.areParticlesEnabled() || entity == null) {
            return;
        }

        try {
            Location particleLoc = entity.getLocation().add(0, 1, 0);
            World world = particleLoc.getWorld();

            Particle particleType = Particle.valueOf(config.getParticleType(effectType));
            int count = config.getParticleCount(effectType);
            double offset = config.getParticleOffset(effectType);

            world.spawnParticle(particleType, particleLoc, count, offset, offset, offset, 0);

        } catch (Exception e) {
            config.debugLog("Error playing " + effectType + " particles for " + id + ": " + e.getMessage());
        }
    }

    public void playSound(String soundType) {
        if (!config.areSoundsEnabled() || entity == null) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(config.getSound(soundType));
            entity.getWorld().playSound(entity.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception e) {
            config.debugLog("Error playing " + soundType + " sound for " + id + ": " + e.getMessage());
        }
    }

    public void onPlayerInteract(Player player) {
        dailyInteractionCount++;
        lastInteractionTime = System.currentTimeMillis();

        // Play interaction sound
        playSound("gui-open");

        // Play interaction particles
        playParticleEffect("sell-success");

        config.debugLog("Player " + player.getName() + " interacted with NPC " + id);
    }

    public void remove() {
        config.debugLog("Removing NPC: " + id);

        if (entity != null && entity.isValid()) {
            entity.remove();
            config.debugLog("Removed NPC entity: " + id);
        }

        removeHologram();
        stopTasks();
        entity = null;
        entityUUID = null;
    }

    private void removeHologram() {
        for (ArmorStand hologram : hologramLines) {
            if (hologram != null && hologram.isValid()) {
                hologram.remove();
            }
        }
        hologramLines.clear();

        if (hologramUpdateTask != null) {
            hologramUpdateTask.cancel();
            hologramUpdateTask = null;
        }
    }

    private void stopTasks() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }

        if (hologramUpdateTask != null) {
            hologramUpdateTask.cancel();
            hologramUpdateTask = null;
        }
    }

    public void lookAt(Location target) {
        if (entity == null || !entity.isValid()) return;

        Location npcLoc = entity.getLocation();
        double dx = target.getX() - npcLoc.getX();
        double dz = target.getZ() - npcLoc.getZ();

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        npcLoc.setYaw(yaw);
        entity.teleport(npcLoc);

        // Update hologram positions
        updateHologramPositions();
    }

    private void updateHologramPositions() {
        if (hologramLines.isEmpty() || entity == null) {
            return;
        }

        Location entityLoc = entity.getLocation();
        double baseY = entityLoc.getY() + config.getHologramHeightOffset();

        for (int i = 0; i < hologramLines.size(); i++) {
            ArmorStand hologram = hologramLines.get(i);
            if (hologram != null && hologram.isValid()) {
                double y = baseY + ((hologramLines.size() - 1 - i) * 0.25);
                Location newLoc = new Location(entityLoc.getWorld(), entityLoc.getX(), y, entityLoc.getZ());
                hologram.teleport(newLoc);
            }
        }
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

    public boolean hasCustomPrices() {
        return config.hasCustomPrices(id);
    }

    public String getGreeting() {
        return ChatColor.translateAlternateColorCodes('&', config.getNPCGreeting(id));
    }

    public void updateFromConfig() {
        this.enabled = config.isNPCEnabled(id);

        if (entity != null && entity.isValid()) {
            entity.setCustomName(ChatColor.translateAlternateColorCodes('&', config.getNPCName(id)));

            // Update hologram if enabled
            if (config.areHologramsEnabled()) {
                createHologram();
            } else {
                removeHologram();
            }

            // Update particle effects
            if (config.areParticlesEnabled()) {
                startParticleEffects();
            } else if (particleTask != null) {
                particleTask.cancel();
                particleTask = null;
            }
        }
    }

    // Statistics methods
    public int getDailyInteractionCount() {
        return dailyInteractionCount;
    }

    public long getLastInteractionTime() {
        return lastInteractionTime;
    }

    public void resetDailyStats() {
        dailyInteractionCount = 0;
    }

    // Utility methods
    private String formatItemName(String itemType) {
        String[] words = itemType.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            result.append(Character.toUpperCase(words[i].charAt(0)))
                    .append(words[i].substring(1));
        }
        return result.toString();
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

    // **แก้ไข: ปรับปรุงการตรวจสอบ valid**
    public boolean isValid() {
        if (entity == null) return false;
        if (!entity.isValid()) return false;
        if (entity.isDead()) return false;

        // ตรวจสอบ UUID ด้วย
        if (entityUUID != null && !entityUUID.equals(entity.getUniqueId())) {
            config.debugLog("UUID mismatch for NPC " + id + ", entity may be corrupted");
            return false;
        }

        return true;
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }

    // **แก้ไข: ปรับปรุงการเปรียบเทียบ entity**
    public boolean isThisEntity(LivingEntity checkEntity) {
        if (entity == null || checkEntity == null) return false;

        // ตรวจสอบทั้ง reference และ UUID
        if (entity.equals(checkEntity)) return true;
        if (entityUUID != null && entityUUID.equals(checkEntity.getUniqueId())) return true;

        return false;
    }

    public List<ArmorStand> getHologramLines() {
        return new ArrayList<>(hologramLines);
    }

    @Override
    public String toString() {
        return "TraderNPC{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", valid=" + isValid() +
                ", interactions=" + dailyInteractionCount +
                ", uuid=" + entityUUID +
                '}';
    }
}