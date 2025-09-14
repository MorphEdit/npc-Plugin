package online.zeptra.npcplugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration npcsConfig;
    private FileConfiguration messagesConfig;
    private File npcsFile;
    private File messagesFile;

    // Caching for better performance
    private final Map<String, Double> priceCache = new HashMap<>();
    private final Map<String, String> messageCache = new HashMap<>();
    private final Map<String, List<String>> categoryCache = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadNPCsConfig();
        loadMessagesConfig();
        clearCaches();
        validateConfigFiles(); // เพิ่มการตรวจสอบไฟล์
        plugin.getLogger().info("Configuration files loaded successfully!");
    }

    private void loadNPCsConfig() {
        npcsFile = new File(plugin.getDataFolder(), "npcs.yml");
        if (!npcsFile.exists()) {
            plugin.saveResource("npcs.yml", false);
        }
        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
    }

    private void loadMessagesConfig() {
        String language = getLanguage();
        String fileName = "messages_" + language + ".yml";
        messagesFile = new File(plugin.getDataFolder(), fileName);

        if (!messagesFile.exists()) {
            // Try to save from resources, fallback to english
            try {
                plugin.saveResource(fileName, false);
            } catch (Exception e) {
                fileName = "messages_en.yml";
                messagesFile = new File(plugin.getDataFolder(), fileName);
                if (!messagesFile.exists()) {
                    createDefaultMessagesFile();
                }
            }
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void createDefaultMessagesFile() {
        messagesConfig = new YamlConfiguration();
        // Set default messages
        messagesConfig.set("sell-success", "&aYou sold items for &e${total_price}&a!");
        messagesConfig.set("sell-failed", "&cFailed to sell items!");
        messagesConfig.set("no-items", "&cYou need to put items in the sell slots!");
        messagesConfig.set("npc-disabled", "&cThis NPC is currently disabled!");
        messagesConfig.set("daily-limit-reached", "&cYou have reached your daily selling limit!");
        messagesConfig.set("cooldown-active", "&cPlease wait {cooldown} seconds before selling again!");

        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create default messages file: " + e.getMessage());
        }
    }

    // **แก้ไข: ปรับปรุงเมธอด saveNPCsConfig**
    public void saveNPCsConfig() {
        if (npcsFile == null || npcsConfig == null) {
            plugin.getLogger().warning("NPCs config not initialized, cannot save");
            return;
        }

        try {
            // สร้าง parent directory ถ้าไม่มี
            if (!npcsFile.getParentFile().exists()) {
                npcsFile.getParentFile().mkdirs();
            }

            npcsConfig.save(npcsFile);
            debugLog("Saved NPCs config to: " + npcsFile.getAbsolutePath());

        } catch (IOException e) {
            plugin.getLogger().severe("Could not save npcs.yml: " + e.getMessage());
            e.printStackTrace();

            // พยายาม backup และ save อีกครั้ง
            try {
                String backupName = "npcs_backup_" + System.currentTimeMillis() + ".yml";
                File backupFile = new File(plugin.getDataFolder(), backupName);
                npcsConfig.save(backupFile);
                plugin.getLogger().info("Created backup at: " + backupName);
            } catch (IOException backupError) {
                plugin.getLogger().severe("Failed to create backup: " + backupError.getMessage());
            }
        }
    }

    // **เพิ่ม: เมธอดสำหรับตรวจสอบไฟล์**
    public void validateConfigFiles() {
        // ตรวจสอบไฟล์ npcs.yml
        if (!npcsFile.exists()) {
            plugin.getLogger().warning("npcs.yml does not exist, creating new file");
            npcsConfig = new YamlConfiguration();
            npcsConfig.set("npcs", new HashMap<>());
            saveNPCsConfig();
        }

        // ตรวจสอบว่าไฟล์อ่านได้
        if (!npcsFile.canRead()) {
            plugin.getLogger().severe("Cannot read npcs.yml file!");
        }

        if (!npcsFile.canWrite()) {
            plugin.getLogger().severe("Cannot write to npcs.yml file!");
        }

        debugLog("Config files validation completed");
    }

    // **เพิ่ม: เมธอดสำหรับ force reload**
    public void forceReloadNPCs() {
        try {
            if (npcsFile.exists()) {
                npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
                plugin.getLogger().info("Force reloaded npcs.yml");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error force reloading NPCs config: " + e.getMessage());
        }
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
        loadMessagesConfig();
        clearCaches();
        validateConfigFiles(); // เพิ่มการตรวจสอบ
        plugin.getLogger().info("Configuration reloaded!");
    }

    private void clearCaches() {
        priceCache.clear();
        messageCache.clear();
        categoryCache.clear();
    }

    // Plugin Settings
    public boolean isPluginEnabled() {
        return config.getBoolean("plugin.enabled", true);
    }

    public boolean isDebugMode() {
        return config.getBoolean("plugin.debug", false);
    }

    public String getLanguage() {
        return config.getString("plugin.language", "en");
    }

    // NPC Settings
    public boolean isNPCSystemEnabled() {
        return config.getBoolean("npc.enabled", true);
    }

    public double getNPCLookDistance() {
        return config.getDouble("npc.look-distance", 5.0);
    }

    public long getNPCUpdateInterval() {
        return config.getLong("npc.update-interval", 10);
    }

    // Effects Settings
    public boolean areParticlesEnabled() {
        return config.getBoolean("npc.effects.particles", true);
    }

    public boolean areSoundsEnabled() {
        return config.getBoolean("npc.effects.sounds", true);
    }

    public boolean areHologramsEnabled() {
        return config.getBoolean("npc.effects.holograms", true);
    }

    // Sell System Settings
    public boolean isSellSystemEnabled() {
        return config.getBoolean("sell-system.enabled", true);
    }

    public String getSellGUITitle() {
        return config.getString("sell-system.gui.title", "&aSell Items to {npc_name}");
    }

    public int getSellGUISize() {
        return config.getInt("sell-system.gui.size", 54);
    }

    public List<String> getSellCommands() {
        return config.getStringList("sell-system.sell-commands");
    }

    // Limits & Cooldowns
    public boolean areLimitsEnabled() {
        return config.getBoolean("sell-system.limits.enabled", false);
    }

    public double getDailyLimit() {
        return config.getDouble("sell-system.limits.daily-limit", 10000.0);
    }

    public int getSellCooldown() {
        return config.getInt("sell-system.limits.cooldown", 30);
    }

    public String getDailyResetTime() {
        return config.getString("sell-system.limits.reset-time", "00:00");
    }

    // Categories
    public boolean areCategoriesEnabled() {
        return config.getBoolean("sell-system.gui.categories.enabled", true);
    }

    public boolean showAllCategory() {
        return config.getBoolean("sell-system.gui.categories.show-all", true);
    }

    public String getCategoryName(String category) {
        return config.getString("sell-system.gui.categories.items." + category + ".name", "&7" + category);
    }

    public String getCategoryIcon(String category) {
        return config.getString("sell-system.gui.categories.items." + category + ".icon", "STONE");
    }

    public int getCategorySlot(String category) {
        return config.getInt("sell-system.gui.categories.items." + category + ".slot", 45);
    }

    // Advanced Features
    public boolean isRealTimePreviewEnabled() {
        return config.getBoolean("sell-system.features.real-time-preview", true);
    }

    public boolean isCategorySellAllEnabled() {
        return config.getBoolean("sell-system.features.category-sell-all", true);
    }

    public boolean isConfirmationDialogEnabled() {
        return config.getBoolean("sell-system.features.confirmation-dialog", true);
    }

    public boolean isPriceComparisonEnabled() {
        return config.getBoolean("sell-system.features.price-comparison", true);
    }

    // Messages
    public String getMessage(String path) {
        String message = messageCache.get(path);
        if (message != null) {
            return message;
        }

        // Try to get from messages file first
        if (messagesConfig != null && messagesConfig.contains(path)) {
            message = messagesConfig.getString(path);
        } else {
            // Fallback to main config
            message = config.getString("sell-system.messages." + path, "Message not found: " + path);
        }

        message = ChatColor.translateAlternateColorCodes('&', message);
        messageCache.put(path, message);
        return message;
    }

    public String getSellSuccessMessage() {
        return getMessage("sell-success");
    }

    public String getSellFailedMessage() {
        return getMessage("sell-failed");
    }

    public String getNoItemsMessage() {
        return getMessage("no-items");
    }

    public String getNPCDisabledMessage() {
        return getMessage("npc-disabled");
    }

    public String getDailyLimitReachedMessage() {
        return getMessage("daily-limit-reached");
    }

    public String getCooldownActiveMessage() {
        return getMessage("cooldown-active");
    }

    public String getConfirmationRequiredMessage() {
        return getMessage("confirmation-required");
    }

    public String getCategorySoldMessage() {
        return getMessage("category-sold");
    }

    // Item Categories
    public List<String> getItemCategory(String category) {
        List<String> items = categoryCache.get(category);
        if (items != null) {
            return items;
        }

        items = config.getStringList("item-categories." + category);
        categoryCache.put(category, items);
        return items;
    }

    public String getItemCategoryName(String itemType) {
        for (String category : List.of("ores", "food", "tools", "blocks", "misc")) {
            List<String> items = getItemCategory(category);
            if (items.contains(itemType)) {
                return category;
            }
        }
        return "misc";
    }

    // Item Prices
    public double getItemPrice(String itemType) {
        Double price = priceCache.get(itemType);
        if (price != null) {
            return price;
        }

        price = config.getDouble("item-prices." + itemType, 0.0);
        priceCache.put(itemType, price);
        return price;
    }

    public boolean hasItemPrice(String itemType) {
        return config.contains("item-prices." + itemType);
    }

    // NPC Skin Support
    public boolean areSkinsEnabled() {
        return config.getBoolean("npc-skins.enabled", true);
    }

    public String getDefaultSkin() {
        return config.getString("npc-skins.default-skin", "MHF_Villager");
    }

    public String getNPCSkin(String npcId) {
        return config.getString("npc-skins.custom-skins." + npcId, getDefaultSkin());
    }

    // Sound Effects
    public String getSound(String soundType) {
        return config.getString("sounds." + soundType, "UI_BUTTON_CLICK");
    }

    // Particle Effects
    public String getParticleType(String particleType) {
        return config.getString("particles." + particleType + ".type", "VILLAGER_HAPPY");
    }

    public int getParticleCount(String particleType) {
        return config.getInt("particles." + particleType + ".count", 10);
    }

    public double getParticleOffset(String particleType) {
        return config.getDouble("particles." + particleType + ".offset", 0.5);
    }

    public int getParticleInterval(String particleType) {
        return config.getInt("particles." + particleType + ".interval", 100);
    }

    // Hologram Settings
    public double getHologramHeightOffset() {
        return config.getDouble("holograms.height-offset", 2.5);
    }

    public int getHologramUpdateInterval() {
        return config.getInt("holograms.update-interval", 60);
    }

    public List<String> getHologramLines() {
        return config.getStringList("holograms.lines");
    }

    public boolean showHologramPrices() {
        return config.getBoolean("holograms.show-prices.enabled", true);
    }

    public int getHologramMaxItems() {
        return config.getInt("holograms.show-prices.max-items", 3);
    }

    public String getHologramPriceFormat() {
        return config.getString("holograms.show-prices.format", "&e{item}: &6${price}");
    }

    // NPCs Configuration
    public FileConfiguration getNPCsConfig() {
        return npcsConfig;
    }

    public boolean isNPCEnabled(String npcId) {
        return npcsConfig.getBoolean("npcs." + npcId + ".enabled", false);
    }

    public String getNPCName(String npcId) {
        return npcsConfig.getString("npcs." + npcId + ".name", "Unnamed NPC");
    }

    public String getNPCEntityType(String npcId) {
        return npcsConfig.getString("npcs." + npcId + ".entity-type", "VILLAGER");
    }

    public String getNPCProfession(String npcId) {
        return npcsConfig.getString("npcs." + npcId + ".profession", "FARMER");
    }

    public String getNPCGreeting(String npcId) {
        return npcsConfig.getString("npcs." + npcId + ".settings.greeting", "&aHello!");
    }

    public boolean hasCustomPrices(String npcId) {
        return npcsConfig.getBoolean("npcs." + npcId + ".settings.custom-prices", false);
    }

    public double getNPCItemPrice(String npcId, String itemType) {
        return npcsConfig.getDouble("npcs." + npcId + ".item-prices." + itemType, 0.0);
    }

    public List<String> getNPCCustomCommands(String npcId) {
        return npcsConfig.getStringList("npcs." + npcId + ".custom-commands");
    }

    // **เพิ่ม: เมธอดสำหรับการ backup**
    public boolean createBackup() {
        try {
            String backupName = "config_backup_" + System.currentTimeMillis() + ".yml";
            File backupDir = new File(plugin.getDataFolder(), "backups");

            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            File configBackup = new File(backupDir, "config_" + backupName);
            File npcsBackup = new File(backupDir, "npcs_" + backupName);

            config.save(configBackup);
            npcsConfig.save(npcsBackup);

            plugin.getLogger().info("Configuration backup created: " + backupName);
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create backup: " + e.getMessage());
            return false;
        }
    }

    // **เพิ่ม: เมธอดสำหรับการ restore**
    public boolean restoreFromBackup(String backupName) {
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            File configBackup = new File(backupDir, "config_" + backupName);
            File npcsBackup = new File(backupDir, "npcs_" + backupName);

            if (!configBackup.exists() || !npcsBackup.exists()) {
                plugin.getLogger().warning("Backup files not found: " + backupName);
                return false;
            }

            // Load from backup
            config = YamlConfiguration.loadConfiguration(configBackup);
            npcsConfig = YamlConfiguration.loadConfiguration(npcsBackup);

            // Save as current config
            config.save(new File(plugin.getDataFolder(), "config.yml"));
            npcsConfig.save(npcsFile);

            clearCaches();
            plugin.getLogger().info("Configuration restored from backup: " + backupName);
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to restore from backup: " + e.getMessage());
            return false;
        }
    }

    // **เพิ่ม: เมธอดสำหรับรายการ backup**
    public List<String> getAvailableBackups() {
        File backupDir = new File(plugin.getDataFolder(), "backups");
        List<String> backups = new java.util.ArrayList<>();

        if (backupDir.exists() && backupDir.isDirectory()) {
            for (File file : backupDir.listFiles()) {
                String name = file.getName();
                if (name.startsWith("config_") && name.endsWith(".yml")) {
                    String backupName = name.substring(7, name.length() - 4);
                    backups.add(backupName);
                }
            }
        }

        return backups;
    }

    // Utility Methods
    public void debugLog(String message) {
        if (isDebugMode()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // Cache management
    public void invalidateCache() {
        clearCaches();
    }

    public void invalidatePriceCache() {
        priceCache.clear();
    }

    public void invalidateMessageCache() {
        messageCache.clear();
    }

    // **เพิ่ม: เมธอดสำหรับตรวจสอบ config integrity**
    public boolean validateConfigIntegrity() {
        boolean valid = true;

        try {
            // ตรวจสอบ required sections
            if (!config.contains("plugin")) {
                plugin.getLogger().warning("Missing 'plugin' section in config.yml");
                valid = false;
            }

            if (!config.contains("sell-system")) {
                plugin.getLogger().warning("Missing 'sell-system' section in config.yml");
                valid = false;
            }

            if (!config.contains("item-prices")) {
                plugin.getLogger().warning("Missing 'item-prices' section in config.yml");
                valid = false;
            }

            // ตรวจสอบ npcs.yml
            if (npcsConfig == null) {
                plugin.getLogger().warning("NPCs config is null");
                valid = false;
            }

            if (valid) {
                debugLog("Configuration integrity check passed");
            } else {
                plugin.getLogger().warning("Configuration integrity check failed!");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error during config integrity check: " + e.getMessage());
            valid = false;
        }

        return valid;
    }

    // **เพิ่ม: เมธอดสำหรับ cleanup cache เก่า**
    public void cleanupOldCaches() {
        // ลบ cache entries ที่ไม่ได้ใช้
        priceCache.entrySet().removeIf(entry -> !hasItemPrice(entry.getKey()));

        debugLog("Cleaned up old cache entries");
    }

    // **เพิ่ม: เมธอดสำหรับ statistics**
    public Map<String, Object> getConfigStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("cached_prices", priceCache.size());
        stats.put("cached_messages", messageCache.size());
        stats.put("cached_categories", categoryCache.size());
        stats.put("config_file_size", new File(plugin.getDataFolder(), "config.yml").length());
        stats.put("npcs_file_size", npcsFile.length());
        stats.put("debug_mode", isDebugMode());
        stats.put("language", getLanguage());

        return stats;
    }
}