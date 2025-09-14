package online.zeptra.npcplugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellCommand implements CommandExecutor {
    private final SellGUI sellGUI;
    private final NPCManager npcManager;

    public SellCommand(SellGUI sellGUI, NPCManager npcManager) {
        this.sellGUI = sellGUI;
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (!sender.hasPermission("npcplugin.sell")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            TraderNPC nearestNPC = findNearestNPC(player);
            if (nearestNPC == null) {
                player.sendMessage(ChatColor.RED + "No NPCs found nearby!");
                player.sendMessage(ChatColor.GRAY + "Get closer to an NPC or specify an NPC ID: /sellnpc <id>");
                return true;
            }

            sellGUI.openSellGUI(player, nearestNPC);
            return true;
        }

        String npcId = args[0];
        TraderNPC npc = npcManager.getNPC(npcId);

        if (npc == null) {
            player.sendMessage(ChatColor.RED + "NPC '" + npcId + "' not found!");
            player.sendMessage(ChatColor.GRAY + "Use /npc list to see available NPCs");
            return true;
        }

        if (!npc.isEnabled()) {
            player.sendMessage(ChatColor.RED + "NPC '" + npcId + "' is currently disabled!");
            return true;
        }

        sellGUI.openSellGUI(player, npc);
        return true;
    }

    private TraderNPC findNearestNPC(Player player) {
        TraderNPC nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double maxDistance = 10.0;

        for (TraderNPC npc : npcManager.getAllNPCs().values()) {
            if (!npc.isEnabled() || !npc.isValid()) {
                continue;
            }

            if (!npc.getLocation().getWorld().equals(player.getWorld())) {
                continue;
            }

            double distance = player.getLocation().distance(npc.getLocation());
            if (distance <= maxDistance && distance < nearestDistance) {
                nearest = npc;
                nearestDistance = distance;
            }
        }

        return nearest;
    }
}