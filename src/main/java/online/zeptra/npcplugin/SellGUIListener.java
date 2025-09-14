package online.zeptra.npcplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class SellGUIListener implements Listener {
    private final SellGUI sellGUI;
    private final ConfigManager config;
    private final NPCManager npcManager;

    public SellGUIListener(SellGUI sellGUI, ConfigManager config, NPCManager npcManager) {
        this.sellGUI = sellGUI;
        this.config = config;
        this.npcManager = npcManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!sellGUI.isSellGUI(title)) {
            return;
        }

        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        int guiSize = event.getInventory().getSize();

        // Define sell area (first 4 rows = 36 slots)
        int sellSlots = 36;
        boolean isInSellArea = slot >= 0 && slot < sellSlots;
        boolean isInCategoryArea = slot >= 36 && slot < 45;
        boolean isInButtonArea = slot >= 45;

        // Handle different areas
        if (isInSellArea) {
            handleSellAreaClick(event, player, slot, clickedItem, cursorItem);
        } else if (isInCategoryArea) {
            event.setCancelled(true);
            handleCategoryAreaClick(event, player, slot, clickedItem);
        } else if (isInButtonArea) {
            event.setCancelled(true);
            handleButtonAreaClick(event, player, slot, clickedItem);
        } else {
            // Outside GUI - cancel
            event.setCancelled(true);
        }
    }

    private void handleSellAreaClick(InventoryClickEvent event, Player player, int slot,
                                     ItemStack clickedItem, ItemStack cursorItem) {
        // Allow normal inventory interactions in sell area
        TraderNPC npc = sellGUI.getCurrentNPC(player);
        if (npc == null) {
            event.setCancelled(true);
            return;
        }

        // Validate items being placed
        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            if (!isItemSellable(cursorItem, npc)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "This NPC doesn't buy " +
                        formatItemName(cursorItem.getType().name()) + "!");

                if (config.areSoundsEnabled()) {
                    player.playSound(player.getLocation(),
                            Sound.valueOf(config.getSound("sell-failed")), 1.0f, 1.0f);
                }
                return;
            }
        }

        // Handle shift-click from player inventory
        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            ItemStack shiftItem = event.getCurrentItem();
            if (shiftItem != null && !isItemSellable(shiftItem, npc)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "This NPC doesn't buy " +
                        formatItemName(shiftItem.getType().name()) + "!");
                return;
            }
        }

        // Play sound for successful item placement/removal
        if (config.areSoundsEnabled() &&
                ((cursorItem != null && cursorItem.getType() != Material.AIR) ||
                        (clickedItem != null && clickedItem.getType() != Material.AIR))) {

            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getSound("button-click")), 0.5f, 1.2f);
        }
    }

    private void handleCategoryAreaClick(InventoryClickEvent event, Player player,
                                         int slot, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        String itemName = getItemDisplayName(clickedItem);
        if (itemName == null) {
            return;
        }

        // Play category switch sound
        if (config.areSoundsEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getSound("category-switch")), 1.0f, 1.0f);
        }

        // Determine category from slot and item
        String category = determineCategoryFromSlot(slot, itemName);
        if (category != null) {
            sellGUI.handleCategoryClick(player, category);
        }
    }

    private void handleButtonAreaClick(InventoryClickEvent event, Player player,
                                       int slot, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        String itemName = getItemDisplayName(clickedItem);
        if (itemName == null) {
            return;
        }

        // Play button click sound
        if (config.areSoundsEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getSound("button-click")), 1.0f, 1.0f);
        }

        // Handle different button types
        if (sellGUI.isSellButton(clickedItem)) {
            sellGUI.handleSellClick(player, event.getInventory());

        } else if (sellGUI.isCategorySellButton(clickedItem)) {
            sellGUI.handleCategorySellAllClick(player);

        } else if (sellGUI.isPriceInfoButton(clickedItem)) {
            sellGUI.handlePriceInfoClick(player);

        } else if (sellGUI.isComparePricesButton(clickedItem)) {
            sellGUI.handleComparePricesClick(player);

        } else if (sellGUI.isCloseButton(clickedItem)) {
            player.closeInventory();

            if (config.areSoundsEnabled()) {
                player.playSound(player.getLocation(),
                        Sound.valueOf(config.getSound("gui-close")), 1.0f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!sellGUI.isSellGUI(title)) {
            return;
        }

        // Check if drag includes non-sell slots
        for (int slot : event.getRawSlots()) {
            if (slot >= 36) { // Outside sell area
                event.setCancelled(true);
                return;
            }
        }

        // Validate dragged items
        TraderNPC npc = sellGUI.getCurrentNPC(player);
        if (npc != null) {
            ItemStack draggedItem = event.getOldCursor();
            if (draggedItem != null && !isItemSellable(draggedItem, npc)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "This NPC doesn't buy " +
                        formatItemName(draggedItem.getType().name()) + "!");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        if (sellGUI.isSellGUI(title)) {
            // Return unsold items to player
            returnUnsoldItems(player, event.getInventory());

            // Cleanup player data
            sellGUI.cleanupPlayer(player);

            // Play close sound if not already played
            if (config.areSoundsEnabled()) {
                player.playSound(player.getLocation(),
                        Sound.valueOf(config.getSound("gui-close")), 0.8f, 1.0f);
            }

            config.debugLog("Cleaned up sell GUI data for " + player.getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cleanup any GUI data for disconnecting player
        sellGUI.cleanupPlayer(player);

        config.debugLog("Cleaned up data for disconnecting player: " + player.getName());
    }

    // Utility Methods
    private boolean isItemSellable(ItemStack item, TraderNPC npc) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        double price = npc.getItemPrice(item.getType().name());
        return price > 0;
    }

    private String getItemDisplayName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return null;
        }
        return ChatColor.stripColor(item.getItemMeta().getDisplayName());
    }

    private String determineCategoryFromSlot(int slot, String itemName) {
        // Map slots to categories based on GUI layout
        switch (slot) {
            case 36: return "all";      // All items button
            case 37: return "ores";     // Ores category
            case 38: return "food";     // Food category
            case 39: return "tools";    // Tools category
            case 40: return "blocks";   // Blocks category
            case 41: return "misc";     // Misc category
            default: return null;
        }
    }

    private void returnUnsoldItems(Player player, org.bukkit.inventory.Inventory gui) {
        // Return items from sell area (slots 0-35) to player inventory
        for (int i = 0; i < 36; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                // Try to add to player inventory
                var leftover = player.getInventory().addItem(item);

                // Drop items that don't fit
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    player.sendMessage(ChatColor.YELLOW + "Some items were dropped because your inventory is full!");
                }

                gui.setItem(i, null);
            }
        }
    }

    private String formatItemName(String itemType) {
        return Arrays.stream(itemType.toLowerCase().split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(itemType);
    }

    // Advanced validation methods
    private boolean isValidSellTransaction(Player player, org.bukkit.inventory.Inventory gui) {
        TraderNPC npc = sellGUI.getCurrentNPC(player);
        if (npc == null || !npc.isEnabled()) {
            return false;
        }

        // Check if player has items to sell
        boolean hasItems = false;
        for (int i = 0; i < 36; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                if (isItemSellable(item, npc)) {
                    hasItems = true;
                    break;
                }
            }
        }

        return hasItems;
    }

    private void handleInvalidItemPlacement(Player player, ItemStack item) {
        player.sendMessage(ChatColor.RED + "You cannot sell " +
                formatItemName(item.getType().name()) + " to this NPC!");

        if (config.areSoundsEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getSound("sell-failed")), 1.0f, 0.8f);
        }
    }

    // Debug methods
    private void debugClick(Player player, InventoryClickEvent event) {
        if (config.isDebugMode()) {
            config.debugLog(String.format(
                    "GUI Click - Player: %s, Slot: %d, Action: %s, Item: %s",
                    player.getName(),
                    event.getSlot(),
                    event.getAction(),
                    event.getCurrentItem() != null ? event.getCurrentItem().getType() : "null"
            ));
        }
    }

    // Error handling
    private void handleGUIError(Player player, Exception e) {
        config.debugLog("GUI Error for player " + player.getName() + ": " + e.getMessage());
        player.sendMessage(ChatColor.RED + "An error occurred with the sell GUI. Please try again.");
        player.closeInventory();
    }
}