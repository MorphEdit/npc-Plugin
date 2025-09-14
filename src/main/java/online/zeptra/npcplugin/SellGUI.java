package online.zeptra.npcplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellGUI {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final NPCManager npcManager;
    private final Map<Player, String> playerNPCMap;

    public SellGUI(JavaPlugin plugin, ConfigManager config, NPCManager npcManager) {
        this.plugin = plugin;
        this.config = config;
        this.npcManager = npcManager;
        this.playerNPCMap = new HashMap<>();
    }

    public void openSellGUI(Player player, TraderNPC npc) {
        if (!config.isSellSystemEnabled()) {
            player.sendMessage(config.getMessage("sell-system-disabled"));
            return;
        }

        if (!npc.isEnabled()) {
            player.sendMessage(config.getNPCDisabledMessage());
            return;
        }

        playerNPCMap.put(player, npc.getId());

        String title = config.getSellGUITitle()
                .replace("{npc_name}", ChatColor.stripColor(npc.getName()));
        title = ChatColor.translateAlternateColorCodes('&', title);

        Inventory gui = Bukkit.createInventory(null, config.getSellGUISize(), title);

        setupGUI(gui, npc);

        player.sendMessage(npc.getGreeting());
        player.openInventory(gui);

        config.debugLog("Opened sell GUI for " + player.getName() + " with NPC " + npc.getId());
    }

    private void setupGUI(Inventory gui, TraderNPC npc) {
        int size = gui.getSize();
        int sellSlots = Math.min(18, size - 9);
        int buttonRow = size - 9;

        ItemStack sellButton = createButton(Material.EMERALD,
                "&a&lSell All Items",
                Arrays.asList(
                        "&7Put items in the slots above",
                        "&7and click here to sell them!",
                        "",
                        "&eClick to sell!"
                )
        );
        gui.setItem(buttonRow + 4, sellButton);

        ItemStack infoButton = createButton(Material.BOOK,
                "&e&lPrice Information",
                Arrays.asList(
                        "&7View item prices for this NPC",
                        "",
                        "&eClick for prices!"
                )
        );
        gui.setItem(buttonRow + 2, infoButton);

        ItemStack closeButton = createButton(Material.BARRIER,
                "&c&lClose",
                Arrays.asList("&7Click to close this menu")
        );
        gui.setItem(buttonRow + 6, closeButton);

        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = buttonRow; i < size; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }

    public void handleSellClick(Player player, Inventory gui) {
        String npcId = playerNPCMap.get(player);
        if (npcId == null) {
            player.sendMessage(config.getSellFailedMessage());
            return;
        }

        TraderNPC npc = npcManager.getNPC(npcId);
        if (npc == null || !npc.isEnabled()) {
            player.sendMessage(config.getNPCDisabledMessage());
            return;
        }

        double totalPrice = 0;
        int itemCount = 0;
        int sellSlots = Math.min(18, gui.getSize() - 9);

        for (int i = 0; i < sellSlots; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                double price = calculateItemPrice(item, npc);
                if (price > 0) {
                    totalPrice += price;
                    itemCount += item.getAmount();
                }
            }
        }

        if (totalPrice <= 0 || itemCount == 0) {
            player.sendMessage(config.getNoItemsMessage());
            return;
        }

        for (int i = 0; i < sellSlots; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                double price = calculateItemPrice(item, npc);
                if (price > 0) {
                    gui.setItem(i, null);
                }
            }
        }

        runSellCommands(player, npc, totalPrice, itemCount);

        String message = config.getSellSuccessMessage()
                .replace("{total_price}", String.format("%.2f", totalPrice))
                .replace("{item_count}", String.valueOf(itemCount));
        player.sendMessage(message);

        player.closeInventory();

        config.debugLog(player.getName() + " sold items for $" + totalPrice + " to " + npc.getId());
    }

    public void handlePriceInfoClick(Player player) {
        String npcId = playerNPCMap.get(player);
        if (npcId == null) return;

        TraderNPC npc = npcManager.getNPC(npcId);
        if (npc == null) return;

        player.sendMessage(ChatColor.GOLD + "=== Price Information ===");
        player.sendMessage(ChatColor.YELLOW + "Trading with: " + npc.getName());

        String[] commonItems = {"COBBLESTONE", "STONE", "IRON_ORE", "COAL", "WHEAT", "IRON_INGOT"};

        for (String itemType : commonItems) {
            double price = npc.getItemPrice(itemType);
            if (price > 0) {
                String itemName = formatItemName(itemType);
                player.sendMessage(ChatColor.AQUA + itemName + ": " + ChatColor.GREEN + "$" + String.format("%.2f", price));
            }
        }

        player.sendMessage(ChatColor.GRAY + "Put items in the GUI to see their exact prices!");
    }

    private double calculateItemPrice(ItemStack item, TraderNPC npc) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        double unitPrice = npc.getItemPrice(item.getType().name());

        if (unitPrice <= 0) {
            return 0;
        }

        double damageModifier = calculateDamageModifier(item);

        return unitPrice * item.getAmount() * damageModifier;
    }

    private double calculateDamageModifier(ItemStack item) {
        if (item.getType().getMaxDurability() <= 0) {
            return 1.0;
        }

        if (item.getDurability() == 0) {
            return 1.0;
        }

        double maxDurability = item.getType().getMaxDurability();
        double currentDurability = maxDurability - item.getDurability();
        double durabilityPercentage = currentDurability / maxDurability;

        return Math.max(0.3, durabilityPercentage);
    }

    private void runSellCommands(Player player, TraderNPC npc, double totalPrice, int itemCount) {
        List<String> commands;

        if (npc.hasCustomCommands()) {
            commands = npc.getCustomCommands();
        } else {
            commands = config.getSellCommands();
        }

        for (String command : commands) {
            String processedCommand = command
                    .replace("{player}", player.getName())
                    .replace("{total_price}", String.format("%.2f", totalPrice))
                    .replace("{item_count}", String.valueOf(itemCount))
                    .replace("{npc_name}", ChatColor.stripColor(npc.getName()))
                    .replace("{npc_id}", npc.getId());

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            config.debugLog("Executed command: " + processedCommand);
        }
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            if (lore != null) {
                meta.setLore(lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .toList());
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    private String formatItemName(String itemType) {
        return Arrays.stream(itemType.toLowerCase().split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(itemType);
    }

    public void cleanupPlayer(Player player) {
        playerNPCMap.remove(player);
    }

    public TraderNPC getCurrentNPC(Player player) {
        String npcId = playerNPCMap.get(player);
        return npcId != null ? npcManager.getNPC(npcId) : null;
    }

    public boolean isSellGUI(String title) {
        String guiTitle = ChatColor.translateAlternateColorCodes('&', config.getSellGUITitle());
        return title.contains("Sell Items to") || title.equals(guiTitle);
    }
}