package online.zeptra.npcplugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration npcsConfig;
    private File npcsFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadNPCsConfig();
        plugin.getLogger().info("Configuration files loaded successfully!");
    }

    private void loadNPCsConfig() {
        npcsFile = new File(plugin.getDataFolder(), "npcs.yml");
        if (!npcsFile.exists()) {
            plugin.saveResource("npcs.yml", false);
        }
        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
    }

    public void saveNPCsConfig() {
        try {
            npcsConfig.save(npcsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save npcs.yml: " + e.getMessage());
        }
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
        plugin.getLogger().info("Configuration reloaded!");
    }

    // Plugin Settings
    public boolean isPluginEnabled() {
        return config.getBoolean("plugin.enabled", true);
    }

    public boolean isDebugMode() {
        return config.getBoolean("plugin.debug", false);
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

    // Sell System Settings
    public boolean isSellSystemEnabled() {
        return config.getBoolean("sell-system.enabled", true);
    }

    public String getSellGUITitle() {
        return config.getString("sell-system.gui.title", "&aSell Items to {npc_name}");
    }

    public int getSellGUISize() {
        return config.getInt("sell-system.gui.size", 27);
    }

    public List<String> getSellCommands() {
        return config.getStringList("sell-system.sell-commands");
    }

    // Messages
    public String getMessage(String path) {
        String message = config.getString("sell-system.messages." + path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
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

    // Item Prices
    public double getItemPrice(String itemType) {
        return config.getDouble("item-prices." + itemType, 0.0);
    }

    public boolean hasItemPrice(String itemType) {
        return config.contains("item-prices." + itemType);
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

    // Utility Methods
    public void debugLog(String message) {
        if (isDebugMode()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }
}