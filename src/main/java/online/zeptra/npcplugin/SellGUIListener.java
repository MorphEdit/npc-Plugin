package online.zeptra.npcplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class SellGUIListener implements Listener {
    private final SellGUI sellGUI;
    private final ConfigManager config;

    public SellGUIListener(SellGUI sellGUI, ConfigManager config) {
        this.sellGUI = sellGUI;
        this.config = config;
    }

    @EventHandler
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
        int guiSize = event.getInventory().getSize();
        int sellSlots = Math.min(18, guiSize - 9);

        if (slot < sellSlots) {
            return;
        }

        event.setCancelled(true);

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        String itemName = "";
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
            itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        }

        if (itemName.contains("Sell All Items")) {
            sellGUI.handleSellClick(player, event.getInventory());
        } else if (itemName.contains("Price Information")) {
            sellGUI.handlePriceInfoClick(player);
        } else if (itemName.contains("Close")) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        if (sellGUI.isSellGUI(title)) {
            sellGUI.cleanupPlayer(player);
            config.debugLog("Cleaned up sell GUI data for " + player.getName());
        }
    }
}