package online.zeptra.npcplugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class NPCCommand implements CommandExecutor, TabCompleter {
    private final NPCManager npcManager;
    private final ConfigManager config;
    private final PlayerDataManager playerDataManager;

    public NPCCommand(NPCManager npcManager, ConfigManager config, PlayerDataManager playerDataManager) {
        this.npcManager = npcManager;
        this.config = config;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("npcplugin.npc")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create":
                    handleCreateCommand(sender, args);
                    break;
                case "remove":
                case "delete":
                    handleRemoveCommand(sender, args);
                    break;
                case "list":
                    handleListCommand(sender, args);
                    break;
                case "toggle":
                    handleToggleCommand(sender, args);
                    break;
                case "reload":
                    handleReloadCommand(sender);
                    break;
                case "info":
                    handleInfoCommand(sender, args);
                    break;
                case "validate":
                case "check":
                    handleValidateCommand(sender);
                    break;
                case "teleport":
                case "tp":
                    handleTeleportCommand(sender, args);
                    break;
                case "stats":
                    handleStatsCommand(sender, args);
                    break;
                case "edit":
                    handleEditCommand(sender, args);
                    break;
                case "hologram":
                case "holo":
                    handleHologramCommand(sender, args);
                    break;
                case "effects":
                    handleEffectsCommand(sender, args);
                    break;
                case "resetstats":
                    handleResetStatsCommand(sender, args);
                    break;
                case "export":
                    handleExportCommand(sender, args);
                    break;
                case "import":
                    handleImportCommand(sender, args);
                    break;
                default:
                    showHelp(sender);
                    break;
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "An error occurred: " + e.getMessage());
            config.debugLog("Command error: " + e.toString());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("npcplugin.npc")) {
            return completions;
        }

        if (args.length == 1) {
            // Main subcommands
            List<String> subCommands = Arrays.asList(
                    "create", "remove", "delete", "list", "toggle", "reload", "info",
                    "validate", "check", "teleport", "tp", "stats", "edit", "hologram",
                    "holo", "effects", "resetstats", "export", "import"
            );
            completions.addAll(subCommands);
        }
        else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "remove":
                case "delete":
                case "toggle":
                case "info":
                case "teleport":
                case "tp":
                case "stats":
                case "edit":
                case "hologram":
                case "holo":
                case "effects":
                case "resetstats":
                case "export":
                    // Add NPC IDs
                    completions.addAll(npcManager.getNPCIds());
                    break;
                case "list":
                    completions.addAll(Arrays.asList("all", "enabled", "disabled", "online", "offline"));
                    break;
            }
        }
        else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "edit":
                    completions.addAll(Arrays.asList("name", "greeting", "profession", "skin"));
                    break;
                case "hologram":
                case "holo":
                    completions.addAll(Arrays.asList("toggle", "update", "height", "lines"));
                    break;
                case "effects":
                    if ("particles".equals(args[1])) {
                        completions.addAll(Arrays.asList("toggle", "type", "count"));
                    } else if ("sounds".equals(args[1])) {
                        completions.addAll(Arrays.asList("toggle", "play"));
                    }
                    break;
            }
        }

        // Filter completions based on current input
        String currentArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(currentArg))
                .sorted()
                .collect(Collectors.toList());
    }

    private void handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can create NPCs!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc create <id> <name>");
            sender.sendMessage(ChatColor.GRAY + "Example: /npc create miner \"Mining Expert\"");
            return;
        }

        Player player = (Player) sender;
        String npcId = args[1];
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // Validate NPC ID
        if (!isValidNPCId(npcId)) {
            sender.sendMessage(ChatColor.RED + "Invalid NPC ID! Use only letters, numbers, and underscores.");
            return;
        }

        if (npcManager.getNPC(npcId) != null) {
            sender.sendMessage(ChatColor.RED + "NPC with ID '" + npcId + "' already exists!");
            return;
        }

        if (npcManager.createNPC(npcId, name, player)) {
            sender.sendMessage(ChatColor.GREEN + "Successfully created NPC '" + npcId + "' with name: " + ChatColor.YELLOW + name);
            sender.sendMessage(ChatColor.GRAY + "NPC spawned at your location: " +
                    formatLocation(player.getLocation()));
            sender.sendMessage(ChatColor.AQUA + "Use " + ChatColor.WHITE + "/npc edit " + npcId + ChatColor.AQUA + " to customize further!");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create NPC '" + npcId + "'");
        }
    }

    private void handleRemoveCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc remove <id>");
            return;
        }

        String npcId = args[1];

        if (npcManager.removeNPC(npcId)) {
            sender.sendMessage(ChatColor.GREEN + "Successfully removed NPC '" + npcId + "'");
        } else {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
        }
    }

    private void handleListCommand(CommandSender sender, String[] args) {
        String filter = args.length > 1 ? args[1].toLowerCase() : "all";
        Map<String, TraderNPC> npcs = npcManager.getAllNPCs();

        if (npcs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No NPCs found.");
            return;
        }

        // Filter NPCs based on criteria
        List<Map.Entry<String, TraderNPC>> filteredNPCs = npcs.entrySet().stream()
                .filter(entry -> matchesFilter(entry.getValue(), filter))
                .collect(Collectors.toList());

        if (filteredNPCs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No NPCs found matching filter: " + filter);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== NPCs (" + filteredNPCs.size() + "/" + npcs.size() + ") ===");

        for (Map.Entry<String, TraderNPC> entry : filteredNPCs) {
            TraderNPC npc = entry.getValue();
            String status = getStatusColor(npc) + getStatusText(npc);
            String interaction = npc.getDailyInteractionCount() > 0 ?
                    ChatColor.GREEN + " (" + npc.getDailyInteractionCount() + " interactions)" : "";

            sender.sendMessage(ChatColor.AQUA + entry.getKey() + ChatColor.WHITE + " - " +
                    npc.getName() + " " + status + interaction);

            if (sender.hasPermission("npcplugin.admin") && config.isDebugMode()) {
                sender.sendMessage(ChatColor.GRAY + "  Location: " + formatLocation(npc.getLocation()));
                if (npc.hasCustomPrices()) {
                    sender.sendMessage(ChatColor.GRAY + "  Custom Prices: Yes");
                }
            }
        }
    }

    private void handleToggleCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc toggle <id>");
            return;
        }

        String npcId = args[1];

        if (npcManager.toggleNPC(npcId)) {
            TraderNPC npc = npcManager.getNPC(npcId);
            boolean enabled = npc != null && npc.isEnabled();

            sender.sendMessage(ChatColor.GREEN + "NPC '" + npcId + "' is now " +
                    (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
        } else {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading NPC system...");

        try {
            long startTime = System.currentTimeMillis();
            npcManager.reloadNPCs();
            long duration = System.currentTimeMillis() - startTime;

            sender.sendMessage(ChatColor.GREEN + "NPC system reloaded successfully! (" + duration + "ms)");
            sender.sendMessage(ChatColor.GRAY + "Loaded " + npcManager.getEnabledNPCCount() +
                    " enabled NPCs out of " + npcManager.getNPCCount() + " total");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading NPC system: " + e.getMessage());
            config.debugLog("Reload error: " + e.toString());
        }
    }

    private void handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showSystemInfo(sender);
            return;
        }

        String npcId = args[1];
        TraderNPC npc = npcManager.getNPC(npcId);

        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
            return;
        }

        showNPCInfo(sender, npc);
    }

    private void handleValidateCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Validating all NPCs...");
        long startTime = System.currentTimeMillis();

        npcManager.validateNPCs();

        long duration = System.currentTimeMillis() - startTime;
        sender.sendMessage(ChatColor.GREEN + "NPC validation completed! (" + duration + "ms)");
    }

    private void handleTeleportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can teleport!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc teleport <id>");
            return;
        }

        Player player = (Player) sender;
        String npcId = args[1];
        TraderNPC npc = npcManager.getNPC(npcId);

        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
            return;
        }

        player.teleport(npc.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Teleported to NPC '" + npcId + "'!");
    }

    private void handleStatsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showGlobalStats(sender);
            return;
        }

        String npcId = args[1];
        TraderNPC npc = npcManager.getNPC(npcId);

        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
            return;
        }

        showNPCStats(sender, npc);
    }

    private void handleEditCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc edit <id> <property> <value>");
            sender.sendMessage(ChatColor.GRAY + "Properties: name, greeting, profession, skin");
            return;
        }

        String npcId = args[1];
        String property = args[2].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        TraderNPC npc = npcManager.getNPC(npcId);
        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
            return;
        }

        if (editNPCProperty(npcId, property, value)) {
            sender.sendMessage(ChatColor.GREEN + "Updated " + property + " for NPC '" + npcId + "'");
            npc.updateFromConfig();
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to update " + property + " for NPC '" + npcId + "'");
        }
    }

    private void handleHologramCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc hologram <id> <action>");
            sender.sendMessage(ChatColor.GRAY + "Actions: toggle, update, height <value>, lines");
            return;
        }

        String npcId = args[1];
        String action = args[2].toLowerCase();

        TraderNPC npc = npcManager.getNPC(npcId);
        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
            return;
        }

        switch (action) {
            case "toggle":
                // Toggle hologram for this NPC
                sender.sendMessage(ChatColor.YELLOW + "Hologram toggle not implemented yet");
                break;
            case "update":
                npc.updateFromConfig();
                sender.sendMessage(ChatColor.GREEN + "Updated hologram for NPC '" + npcId + "'");
                break;
            case "height":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /npc hologram <id> height <value>");
                    return;
                }
                try {
                    double height = Double.parseDouble(args[3]);
                    sender.sendMessage(ChatColor.YELLOW + "Height adjustment not implemented yet");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid height value!");
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown hologram action: " + action);
        }
    }

    private void handleEffectsCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc effects <id> <type>");
            sender.sendMessage(ChatColor.GRAY + "Types: particles, sounds, test");
            return;
        }

        String npcId = args[1];
        String effectType = args[2].toLowerCase();

        TraderNPC npc = npcManager.getNPC(npcId);
        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
            return;
        }

        switch (effectType) {
            case "particles":
                npc.playParticleEffect("sell-success");
                sender.sendMessage(ChatColor.GREEN + "Played particle effect for NPC '" + npcId + "'");
                break;
            case "sounds":
                npc.playSound("sell-success");
                sender.sendMessage(ChatColor.GREEN + "Played sound effect for NPC '" + npcId + "'");
                break;
            case "test":
                npc.playParticleEffect("sell-success");
                npc.playSound("sell-success");
                sender.sendMessage(ChatColor.GREEN + "Played test effects for NPC '" + npcId + "'");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown effect type: " + effectType);
        }
    }

    private void handleResetStatsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc resetstats <id|all>");
            return;
        }

        String target = args[1];

        if ("all".equals(target)) {
            for (TraderNPC npc : npcManager.getAllNPCs().values()) {
                npc.resetDailyStats();
            }
            sender.sendMessage(ChatColor.GREEN + "Reset stats for all NPCs!");
        } else {
            TraderNPC npc = npcManager.getNPC(target);
            if (npc == null) {
                sender.sendMessage(ChatColor.RED + "NPC '" + target + "' not found!");
                return;
            }

            npc.resetDailyStats();
            sender.sendMessage(ChatColor.GREEN + "Reset stats for NPC '" + target + "'!");
        }
    }

    private void handleExportCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Export functionality not implemented yet");
    }

    private void handleImportCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Import functionality not implemented yet");
    }

    // Helper methods
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== NPC Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/npc create <id> <name> - Create new NPC");
        sender.sendMessage(ChatColor.YELLOW + "/npc remove <id> - Remove NPC");
        sender.sendMessage(ChatColor.YELLOW + "/npc list [filter] - List NPCs (all/enabled/disabled)");
        sender.sendMessage(ChatColor.YELLOW + "/npc toggle <id> - Enable/disable NPC");
        sender.sendMessage(ChatColor.YELLOW + "/npc info [id] - Show NPC/system information");
        sender.sendMessage(ChatColor.YELLOW + "/npc reload - Reload NPC system");
        sender.sendMessage(ChatColor.YELLOW + "/npc validate - Validate and respawn NPCs");
        sender.sendMessage(ChatColor.YELLOW + "/npc teleport <id> - Teleport to NPC");
        sender.sendMessage(ChatColor.YELLOW + "/npc stats [id] - Show statistics");
        sender.sendMessage(ChatColor.YELLOW + "/npc edit <id> <property> <value> - Edit NPC");
        sender.sendMessage(ChatColor.YELLOW + "/npc effects <id> <type> - Test effects");
        sender.sendMessage(ChatColor.GRAY + "Use /sellnpc to trade with NPCs");
    }

    private void showSystemInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== NPC System Info ===");
        sender.sendMessage(ChatColor.YELLOW + "Total NPCs: " + npcManager.getNPCCount());
        sender.sendMessage(ChatColor.YELLOW + "Enabled NPCs: " + npcManager.getEnabledNPCCount());
        sender.sendMessage(ChatColor.YELLOW + "System Enabled: " +
                (config.isNPCSystemEnabled() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Sell System: " +
                (config.isSellSystemEnabled() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Effects: " +
                (config.areParticlesEnabled() ? ChatColor.GREEN + "Particles " : ChatColor.RED + "No Particles ") +
                (config.areSoundsEnabled() ? ChatColor.GREEN + "Sounds " : ChatColor.RED + "No Sounds ") +
                (config.areHologramsEnabled() ? ChatColor.GREEN + "Holograms" : ChatColor.RED + "No Holograms"));
        sender.sendMessage(ChatColor.YELLOW + "Language: " + config.getLanguage().toUpperCase());
    }

    private void showNPCInfo(CommandSender sender, TraderNPC npc) {
        sender.sendMessage(ChatColor.GOLD + "=== NPC Info: " + npc.getId() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Name: " + npc.getName());
        sender.sendMessage(ChatColor.YELLOW + "Enabled: " +
                (npc.isEnabled() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Status: " +
                (npc.isValid() ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));
        sender.sendMessage(ChatColor.YELLOW + "Location: " + formatLocation(npc.getLocation()));
        sender.sendMessage(ChatColor.YELLOW + "Custom Commands: " +
                (npc.hasCustomCommands() ? ChatColor.GREEN + "Yes (" + npc.getCustomCommands().size() + ")" : ChatColor.GRAY + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Custom Prices: " +
                (npc.hasCustomPrices() ? ChatColor.GREEN + "Yes" : ChatColor.GRAY + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Daily Interactions: " + npc.getDailyInteractionCount());

        // Sample prices
        sender.sendMessage(ChatColor.YELLOW + "Sample Prices:");
        String[] samples = {"IRON_ORE", "DIAMOND", "WHEAT", "COBBLESTONE"};
        for (String item : samples) {
            double price = npc.getItemPrice(item);
            if (price > 0) {
                sender.sendMessage(ChatColor.GRAY + "  " + formatItemName(item) + ": $" + String.format("%.2f", price));
            }
        }
    }

    private void showGlobalStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Global NPC Statistics ===");

        Map<String, TraderNPC> npcs = npcManager.getAllNPCs();
        int totalInteractions = npcs.values().stream()
                .mapToInt(TraderNPC::getDailyInteractionCount)
                .sum();

        sender.sendMessage(ChatColor.YELLOW + "Total Daily Interactions: " + totalInteractions);
        sender.sendMessage(ChatColor.YELLOW + "Active NPCs: " +
                npcs.values().stream().mapToInt(npc -> npc.isValid() ? 1 : 0).sum());

        // Top 3 NPCs by interactions
        List<TraderNPC> topNPCs = npcs.values().stream()
                .sorted((a, b) -> Integer.compare(b.getDailyInteractionCount(), a.getDailyInteractionCount()))
                .limit(3)
                .collect(Collectors.toList());

        if (!topNPCs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Most Active NPCs:");
            for (int i = 0; i < topNPCs.size(); i++) {
                TraderNPC npc = topNPCs.get(i);
                sender.sendMessage(ChatColor.GRAY + "  " + (i + 1) + ". " + npc.getId() +
                        " (" + npc.getDailyInteractionCount() + " interactions)");
            }
        }
    }

    private void showNPCStats(CommandSender sender, TraderNPC npc) {
        sender.sendMessage(ChatColor.GOLD + "=== Stats for " + npc.getId() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Daily Interactions: " + npc.getDailyInteractionCount());
        sender.sendMessage(ChatColor.YELLOW + "Last Interaction: " +
                (npc.getLastInteractionTime() > 0 ?
                        formatTime(System.currentTimeMillis() - npc.getLastInteractionTime()) + " ago" :
                        "Never"));
        sender.sendMessage(ChatColor.YELLOW + "Status: " + getStatusText(npc));
    }

    // Utility methods
    private boolean isValidNPCId(String id) {
        return id != null && id.matches("^[a-zA-Z0-9_]+$") && id.length() <= 32;
    }

    private boolean matchesFilter(TraderNPC npc, String filter) {
        switch (filter) {
            case "enabled": return npc.isEnabled();
            case "disabled": return !npc.isEnabled();
            case "online": return npc.isValid();
            case "offline": return !npc.isValid();
            default: return true;
        }
    }

    private String getStatusColor(TraderNPC npc) {
        if (!npc.isEnabled()) return ChatColor.RED.toString();
        if (!npc.isValid()) return ChatColor.YELLOW.toString();
        return ChatColor.GREEN.toString();
    }

    private String getStatusText(TraderNPC npc) {
        if (!npc.isEnabled()) return "Disabled";
        if (!npc.isValid()) return "Offline";
        return "Online";
    }

    private boolean editNPCProperty(String npcId, String property, String value) {
        String path = "npcs." + npcId + ".";

        switch (property) {
            case "name":
                config.getNPCsConfig().set(path + "name", value);
                break;
            case "greeting":
                config.getNPCsConfig().set(path + "settings.greeting", value);
                break;
            case "profession":
                config.getNPCsConfig().set(path + "profession", value.toUpperCase());
                break;
            case "skin":
                config.getConfig().set("npc-skins.custom-skins." + npcId, value);
                break;
            default:
                return false;
        }

        config.saveNPCsConfig();
        return true;
    }

    private String formatLocation(org.bukkit.Location location) {
        return String.format("%s: %.1f, %.1f, %.1f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ());
    }

    private String formatItemName(String itemType) {
        return Arrays.stream(itemType.toLowerCase().split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(itemType);
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else {
            return (seconds / 3600) + "h";
        }
    }
}