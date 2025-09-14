package online.zeptra.npcplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class NPCPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private NPCManager npcManager;
    private PlayerDataManager playerDataManager;
    private SellGUI sellGUI;

    // Command handlers
    private NPCCommand npcCommand;
    private SellCommand sellCommand;

    // Event listeners
    private NPCInteractListener npcInteractListener;
    private SellGUIListener sellGUIListener;

    // System tasks
    private BukkitRunnable dailyResetTask;
    private BukkitRunnable saveTask;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        getLogger().info("=== NPCPlugin Starting ===");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Author: " + getDescription().getAuthors());

        try {
            // Initialize core managers
            initializeManagers();

            // Setup commands
            setupCommands();

            // Register event listeners
            registerEventListeners();

            // Load NPCs
            loadNPCs();

            // Start system tasks
            startSystemTasks();

            // Setup metrics (if available)
            setupMetrics();

            long duration = System.currentTimeMillis() - startTime;
            getLogger().info("NPCPlugin enabled successfully! (" + duration + "ms)");

            // Display startup information
            displayStartupInfo();

        } catch (Exception e) {
            getLogger().severe("Failed to enable NPCPlugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("NPCPlugin is shutting down...");

        try {
            // Stop system tasks
            stopSystemTasks();

            // **แก้ไข: เรียก shutdown method ใหม่**
            if (npcManager != null) {
                npcManager.shutdown();
            }

            // Save all data
            saveAllData();

            // Cleanup player data
            cleanupPlayerData();

            // Unregister listeners
            HandlerList.unregisterAll(this);

            getLogger().info("NPCPlugin disabled successfully!");

        } catch (Exception e) {
            getLogger().severe("Error during plugin shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        // Initialize configuration manager
        this.configManager = new ConfigManager(this);

        // Initialize player data manager
        this.playerDataManager = new PlayerDataManager(this);

        // Initialize NPC manager
        this.npcManager = new NPCManager(this, configManager);

        // Initialize sell GUI
        this.sellGUI = new SellGUI(this, configManager, npcManager);

        getLogger().info("Managers initialized successfully!");
    }

    private void setupCommands() {
        getLogger().info("Setting up commands...");

        // Initialize command handlers
        this.npcCommand = new NPCCommand(npcManager, configManager, playerDataManager);
        this.sellCommand = new SellCommand(sellGUI, npcManager);

        // Register NPC command
        PluginCommand npcCmd = getCommand("npc");
        if (npcCmd != null) {
            npcCmd.setExecutor(npcCommand);
            npcCmd.setTabCompleter((TabCompleter) npcCommand);
        } else {
            getLogger().warning("Could not register /npc command!");
        }

        // Register sell command
        PluginCommand sellCmd = getCommand("sellnpc");
        if (sellCmd != null) {
            sellCmd.setExecutor(sellCommand);
        } else {
            getLogger().warning("Could not register /sellnpc command!");
        }

        getLogger().info("Commands registered successfully!");
    }

    private void registerEventListeners() {
        getLogger().info("Registering event listeners...");

        // Initialize listeners
        this.npcInteractListener = new NPCInteractListener(sellGUI, npcManager);
        this.sellGUIListener = new SellGUIListener(sellGUI, configManager, npcManager);

        // Register listeners
        getServer().getPluginManager().registerEvents(npcInteractListener, this);
        getServer().getPluginManager().registerEvents(sellGUIListener, this);

        getLogger().info("Event listeners registered successfully!");
    }

    private void loadNPCs() {
        if (!configManager.isPluginEnabled()) {
            getLogger().info("Plugin is disabled in config - skipping NPC loading");
            return;
        }

        getLogger().info("Loading NPCs...");

        try {
            npcManager.loadNPCs();
            getLogger().info("NPCs loaded successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to load NPCs: " + e.getMessage());
            throw e;
        }
    }

    private void startSystemTasks() {
        getLogger().info("Starting system tasks...");

        // Daily reset task
        startDailyResetTask();

        // Auto-save task
        startAutoSaveTask();

        // NPC validation task
        startValidationTask();

        getLogger().info("System tasks started successfully!");
    }

    private void startDailyResetTask() {
        dailyResetTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Reset daily statistics for all NPCs
                    for (TraderNPC npc : npcManager.getAllNPCs().values()) {
                        npc.resetDailyStats();
                    }

                    // Force daily reset for player data
                    playerDataManager.forceReset();

                    getLogger().info("Daily reset completed successfully!");

                } catch (Exception e) {
                    getLogger().severe("Error during daily reset: " + e.getMessage());
                }
            }
        };

        // Run every 6 hours (in case server misses midnight)
        dailyResetTask.runTaskTimer(this, 0L, 20L * 60L * 60L * 6L);
    }

    private void startAutoSaveTask() {
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    saveAllData();
                    configManager.debugLog("Auto-save completed");
                } catch (Exception e) {
                    getLogger().warning("Auto-save failed: " + e.getMessage());
                }
            }
        };

        // **แก้ไข: ลดเวลา auto-save เหลือ 1 นาที**
        saveTask.runTaskTimer(this, 20L * 60L, 20L * 60L); // ทุก 1 นาที
    }

    private void startValidationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    npcManager.validateNPCs();
                    configManager.debugLog("NPC validation completed");
                } catch (Exception e) {
                    getLogger().warning("NPC validation failed: " + e.getMessage());
                }
            }
        }.runTaskTimer(this, 20L * 60L, 20L * 60L * 5L); // ทุก 5 นาที
    }

    private void setupMetrics() {
        try {
            // bStats metrics (if available)
            getLogger().info("Setting up metrics...");

            // Custom metrics could be added here
            configManager.debugLog("Metrics setup completed");

        } catch (Exception e) {
            configManager.debugLog("Metrics setup failed: " + e.getMessage());
        }
    }

    private void displayStartupInfo() {
        getLogger().info("=== Startup Information ===");
        getLogger().info("Total NPCs: " + npcManager.getNPCCount());
        getLogger().info("Enabled NPCs: " + npcManager.getEnabledNPCCount());
        getLogger().info("Language: " + configManager.getLanguage().toUpperCase());
        getLogger().info("Debug Mode: " + (configManager.isDebugMode() ? "ON" : "OFF"));

        getLogger().info("Features:");
        getLogger().info("  - Sell System: " + (configManager.isSellSystemEnabled() ? "ON" : "OFF"));
        getLogger().info("  - Categories: " + (configManager.areCategoriesEnabled() ? "ON" : "OFF"));
        getLogger().info("  - Limits: " + (configManager.areLimitsEnabled() ? "ON" : "OFF"));
        getLogger().info("  - Particles: " + (configManager.areParticlesEnabled() ? "ON" : "OFF"));
        getLogger().info("  - Sounds: " + (configManager.areSoundsEnabled() ? "ON" : "OFF"));
        getLogger().info("  - Holograms: " + (configManager.areHologramsEnabled() ? "ON" : "OFF"));
        getLogger().info("  - Skins: " + (configManager.areSkinsEnabled() ? "ON" : "OFF"));

        getLogger().info("=== NPCPlugin Ready! ===");
    }

    private void stopSystemTasks() {
        if (dailyResetTask != null) {
            dailyResetTask.cancel();
            dailyResetTask = null;
        }

        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    private void saveAllData() {
        try {
            // **แก้ไข: บังคับ save NPCs ก่อน**
            if (configManager != null) {
                configManager.saveNPCsConfig();
            }

            // Save player data (if implemented)
            if (playerDataManager != null) {
                for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                    playerDataManager.savePlayerData(player);
                }
            }

            configManager.debugLog("All data saved successfully");

        } catch (Exception e) {
            getLogger().warning("Failed to save data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // **ลบเมธอด cleanupNPCs เพราะเราใช้ shutdown แทน**

    private void cleanupPlayerData() {
        if (playerDataManager != null) {
            try {
                playerDataManager.shutdown();
                getLogger().info("Player data cleaned up successfully");
            } catch (Exception e) {
                getLogger().warning("Failed to cleanup player data: " + e.getMessage());
            }
        }
    }

    // Public API for other plugins
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public NPCManager getNPCManager() {
        return npcManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public SellGUI getSellGUI() {
        return sellGUI;
    }

    // Reload method for commands
    public boolean reloadPlugin() {
        try {
            getLogger().info("Reloading NPCPlugin...");

            // Stop tasks
            stopSystemTasks();

            // Save current data
            saveAllData();

            // Remove NPCs properly
            if (npcManager != null) {
                npcManager.shutdown();
            }

            // Reload configurations
            configManager.reloadConfigs();

            // Reload NPCs
            npcManager.loadNPCs();

            // Restart tasks
            startSystemTasks();

            getLogger().info("NPCPlugin reloaded successfully!");
            return true;

        } catch (Exception e) {
            getLogger().severe("Failed to reload plugin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Plugin information methods
    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    public boolean isPluginEnabled() {
        return configManager != null && configManager.isPluginEnabled();
    }

    public int getTotalNPCs() {
        return npcManager != null ? npcManager.getNPCCount() : 0;
    }

    public int getEnabledNPCs() {
        return npcManager != null ? npcManager.getEnabledNPCCount() : 0;
    }

    // Health check method
    public boolean isHealthy() {
        try {
            return configManager != null &&
                    npcManager != null &&
                    playerDataManager != null &&
                    sellGUI != null &&
                    isPluginEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    // Emergency shutdown method
    public void emergencyShutdown(String reason) {
        getLogger().severe("Emergency shutdown triggered: " + reason);

        try {
            // Quick save
            saveAllData();

            // Remove NPCs
            if (npcManager != null) {
                npcManager.shutdown();
            }

            // Disable plugin
            getServer().getPluginManager().disablePlugin(this);

        } catch (Exception e) {
            getLogger().severe("Error during emergency shutdown: " + e.getMessage());
        }
    }

    // Utility methods for debugging
    public void debugInfo() {
        if (!configManager.isDebugMode()) {
            return;
        }

        getLogger().info("=== Debug Information ===");
        getLogger().info("Plugin Health: " + (isHealthy() ? "OK" : "UNHEALTHY"));
        getLogger().info("Memory Usage: " +
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + " MB");
        getLogger().info("Active NPCs: " + npcManager.getEnabledNPCCount() + "/" + npcManager.getNPCCount());
        getLogger().info("Online Players: " + Bukkit.getOnlinePlayers().size());

        // NPC status
        for (TraderNPC npc : npcManager.getAllNPCs().values()) {
            getLogger().info("NPC " + npc.getId() + ": " +
                    (npc.isValid() ? "Valid" : "Invalid") +
                    " (" + npc.getDailyInteractionCount() + " interactions)");
        }
    }

    // Configuration validation
    private void validateConfiguration() {
        boolean valid = true;

        // Check critical settings
        if (configManager.getSellGUISize() < 27 || configManager.getSellGUISize() > 54) {
            getLogger().warning("Invalid GUI size in config: " + configManager.getSellGUISize());
            valid = false;
        }

        if (configManager.areLimitsEnabled() && configManager.getDailyLimit() <= 0) {
            getLogger().warning("Invalid daily limit in config: " + configManager.getDailyLimit());
            valid = false;
        }

        if (!valid) {
            getLogger().warning("Configuration validation failed! Some features may not work correctly.");
        }
    }
}