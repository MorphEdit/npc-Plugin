package online.zeptra.npcplugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class NPCCommand implements CommandExecutor {
    private final NPCManager npcManager;
    private final ConfigManager config;

    public NPCCommand(NPCManager npcManager, ConfigManager config) {
        this.npcManager = npcManager;
        this.config = config;
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

        switch (subCommand) {
            case "create":
                handleCreateCommand(sender, args);
                break;
            case "remove":
                handleRemoveCommand(sender, args);
                break;
            case "list":
                handleListCommand(sender);
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
                handleValidateCommand(sender);
                break;
            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    private void handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can create NPCs!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /npc create <id> <name>");
            return;
        }

        Player player = (Player) sender;
        String npcId = args[1];
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        if (npcManager.getNPC(npcId) != null) {
            sender.sendMessage(ChatColor.RED + "NPC with ID '" + npcId + "' already exists!");
            return;
        }

        if (npcManager.createNPC(npcId, name, player)) {
            sender.sendMessage(ChatColor.GREEN + "Successfully created NPC '" + npcId + "' with name: " + ChatColor.YELLOW + name);
            sender.sendMessage(ChatColor.GRAY + "NPC spawned at your location: " +
                    String.format("%.1f, %.1f, %.1f", player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()));
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

    private void handleListCommand(CommandSender sender) {
        Map<String, TraderNPC> npcs = npcManager.getAllNPCs();

        if (npcs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No NPCs found.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== NPCs (" + npcs.size() + ") ===");

        for (Map.Entry<String, TraderNPC> entry : npcs.entrySet()) {
            TraderNPC npc = entry.getValue();
            String status = npc.isEnabled() ?
                    (npc.isValid() ? ChatColor.GREEN + "Online" : ChatColor.YELLOW + "Offline") :
                    ChatColor.RED + "Disabled";

            sender.sendMessage(ChatColor.AQUA + entry.getKey() + ChatColor.WHITE + " - " +
                    npc.getName() + " " + status);

            if (sender.hasPermission("npcplugin.npc") && config.isDebugMode()) {
                sender.sendMessage(ChatColor.GRAY + "  Location: " + formatLocation(npc.getLocation()));
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
            npcManager.reloadNPCs();
            sender.sendMessage(ChatColor.GREEN + "NPC system reloaded successfully!");
            sender.sendMessage(ChatColor.GRAY + "Loaded " + npcManager.getEnabledNPCCount() +
                    " enabled NPCs out of " + npcManager.getNPCCount() + " total");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading NPC system: " + e.getMessage());
            config.debugLog("Reload error: " + e.toString());
        }
    }

    private void handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "=== NPC System Info ===");
            sender.sendMessage(ChatColor.YELLOW + "Total NPCs: " + npcManager.getNPCCount());
            sender.sendMessage(ChatColor.YELLOW + "Enabled NPCs: " + npcManager.getEnabledNPCCount());
            sender.sendMessage(ChatColor.YELLOW + "System Enabled: " +
                    (config.isNPCSystemEnabled() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
            sender.sendMessage(ChatColor.YELLOW + "Sell System: " +
                    (config.isSellSystemEnabled() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
            return;
        }

        String npcId = args[1];
        TraderNPC npc = npcManager.getNPC(npcId);

        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== NPC Info: " + npcId + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Name: " + npc.getName());
        sender.sendMessage(ChatColor.YELLOW + "Enabled: " +
                (npc.isEnabled() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Status: " +
                (npc.isValid() ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));
        sender.sendMessage(ChatColor.YELLOW + "Location: " + formatLocation(npc.getLocation()));
        sender.sendMessage(ChatColor.YELLOW + "Custom Commands: " +
                (npc.hasCustomCommands() ? ChatColor.GREEN + "Yes (" + npc.getCustomCommands().size() + ")" : ChatColor.GRAY + "No"));

        sender.sendMessage(ChatColor.YELLOW + "Sample Prices:");
        String[] samples = {"IRON_ORE", "DIAMOND", "WHEAT"};
        for (String item : samples) {
            double price = npc.getItemPrice(item);
            if (price > 0) {
                sender.sendMessage(ChatColor.GRAY + "  " + item + ": $" + String.format("%.2f", price));
            }
        }
    }

    private void handleValidateCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Validating all NPCs...");
        npcManager.validateNPCs();
        sender.sendMessage(ChatColor.GREEN + "NPC validation completed!");
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== NPC Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/npc create <id> <name> - Create new NPC");
        sender.sendMessage(ChatColor.YELLOW + "/npc remove <id> - Remove NPC");
        sender.sendMessage(ChatColor.YELLOW + "/npc list - List all NPCs");
        sender.sendMessage(ChatColor.YELLOW + "/npc toggle <id> - Enable/disable NPC");
        sender.sendMessage(ChatColor.YELLOW + "/npc info [id] - Show NPC information");
        sender.sendMessage(ChatColor.YELLOW + "/npc reload - Reload NPC system");
        sender.sendMessage(ChatColor.YELLOW + "/npc validate - Validate and respawn NPCs");
        sender.sendMessage(ChatColor.GRAY + "Use /sellnpc to trade with NPCs");
    }

    private String formatLocation(org.bukkit.Location location) {
        return String.format("%s: %.1f, %.1f, %.1f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ());
    }
}