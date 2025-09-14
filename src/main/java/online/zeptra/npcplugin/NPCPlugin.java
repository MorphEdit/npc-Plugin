package online.zeptra.npcplugin;

import org.bukkit.plugin.java.JavaPlugin;

public final class NPCPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private NPCManager npcManager;
    private SellGUI sellGUI;

    @Override
    public void onEnable() {
        getLogger().info("NPCPlugin has been enabled!");

        // Initialize configuration manager
        this.configManager = new ConfigManager(this);

        // Initialize NPC manager
        this.npcManager = new NPCManager(this, configManager);

        // Initialize sell GUI
        this.sellGUI = new SellGUI(this, configManager, npcManager);

        // Register commands
        getCommand("npc").setExecutor(new NPCCommand(npcManager, configManager));
        getCommand("sellnpc").setExecutor(new SellCommand(sellGUI, npcManager));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new NPCInteractListener(sellGUI, npcManager), this);
        getServer().getPluginManager().registerEvents(new SellGUIListener(sellGUI, configManager), this);

        // Load NPCs
        npcManager.loadNPCs();

        getLogger().info("NPCPlugin loaded successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("NPCPlugin is shutting down...");

        // Remove all NPCs
        if (npcManager != null) {
            npcManager.removeAllNPCs();
        }

        getLogger().info("NPCPlugin has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public NPCManager getNPCManager() {
        return npcManager;
    }

    public SellGUI getSellGUI() {
        return sellGUI;
    }
}