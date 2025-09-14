package online.zeptra.npcplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class SellGUI {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final NPCManager npcManager;
    private final PlayerDataManager playerDataManager;

    // Player tracking
    private final Map<Player, String> playerNPCMap = new WeakHashMap<>();
    private final Map<Player, String> playerCategoryMap = new WeakHashMap<>();
    private final Map<Player, Double> playerPreviewMap = new WeakHashMap<>();
    private final Map<Player, Long> playerConfirmationMap = new WeakHashMap<>();
    private final Set<Player> playersInConfirmation = new HashSet<>();

    // GUI update tasks
    private final Map<Player, BukkitRunnable> previewTasks = new WeakHashMap<>();

    public SellGUI(JavaPlugin plugin, ConfigManager config, NPCManager npcManager) {
        this.plugin = plugin;
        this.config = config;
        this.npcManager = npcManager;
        this.playerDataManager = new PlayerDataManager(plugin);
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

        // Check cooldown
        if (config.areLimitsEnabled() && playerDataManager.isOnCooldown(player)) {
            long remaining = playerDataManager.getRemainingCooldown(player);
            String message = config.getCooldownActiveMessage()
                    .replace("{cooldown}", String.valueOf(remaining));
            player.sendMessage(message);
            return;
        }

        // Check daily limit
        if (config.areLimitsEnabled()) {
            double dailySold = playerDataManager.getDailySoldAmount(player);
            if (dailySold >= config.getDailyLimit()) {
                String message = config.getDailyLimitReachedMessage()
                        .replace("{daily_limit}", String.format("%.2f", config.getDailyLimit()));
                player.sendMessage(message);
                return;
            }
        }

        playerNPCMap.put(player, npc.getId());
        playerCategoryMap.put(player, "all");

        openCategoryGUI(player, npc, "all");

        // Play sound
        if (config.areSoundsEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getSound("gui-open")), 1.0f, 1.0f);
        }

        player.sendMessage(npc.getGreeting());
        config.debugLog("Opened sell GUI for " + player.getName() + " with NPC " + npc.getId());
    }

    private void openCategoryGUI(Player player, TraderNPC npc, String category) {
        String title = config.getSellGUITitle()
                .replace("{npc_name}", ChatColor.stripColor(npc.getName()));
        title = ChatColor.translateAlternateColorCodes('&', title);

        Inventory gui = Bukkit.createInventory(null, config.getSellGUISize(), title);

        setupCategoryGUI(gui, npc, category);

        player.openInventory(gui);

        // Start real-time preview if enabled
        if (config.isRealTimePreviewEnabled()) {
            startPreviewTask(player, gui, npc);
        }
    }

    private void setupCategoryGUI(Inventory gui, TraderNPC npc, String category) {
        int size = gui.getSize();

        // Calculate sell area (4 rows for selling)
        int sellRows = 4;
        int sellSlots = sellRows * 9; // 36 slots for selling

        // Bottom row for buttons (row 5, slots 45-53)
        int buttonRow = size - 9;

        // Category buttons (if enabled)
        if (config.areCategoriesEnabled()) {
            setupCategoryButtons(gui, buttonRow - 9); // Row 4 for categories
        }

        // Main action buttons
        setupActionButtons(gui, buttonRow, npc);

        // Fill empty spaces with glass panes
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = buttonRow - 9; i < size; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }

    private void setupCategoryButtons(Inventory gui, int startRow) {
        List<String> categories = Arrays.asList("ores", "food", "tools", "blocks", "misc");

        // All items button
        if (config.showAllCategory()) {
            ItemStack allButton = createButton(Material.CHEST,
                    "&e&lAll Items",
                    Arrays.asList("&7Show all sellable items", "", "&eClick to view all!")
            );
            gui.setItem(startRow, allButton);
        }

        // Category buttons
        for (int i = 0; i < categories.size() && i < 8; i++) {
            String category = categories.get(i);
            String name = config.getCategoryName(category);
            Material icon = Material.valueOf(config.getCategoryIcon(category));

            List<String> lore = Arrays.asList(
                    "&7Items in this category:",
                    "&8" + getItemCountInCategory(category) + " different items",
                    "",
                    "&eClick to filter by " + category + "!"
            );

            ItemStack categoryButton = createButton(icon, name, lore);
            gui.setItem(startRow + i + 1, categoryButton);
        }
    }

    private void setupActionButtons(Inventory gui, int buttonRow, TraderNPC npc) {
        // Sell All button
        ItemStack sellButton = createButton(Material.EMERALD,
                "&a&lSell All Items",
                Arrays.asList(
                        "&7Put items in the slots above",
                        "&7and click here to sell them!",
                        "",
                        "&aTotal Value: &e$0.00",
                        "",
                        "&eClick to sell!"
                )
        );
        gui.setItem(buttonRow + 4, sellButton);

        // Category Sell All (if enabled)
        if (config.isCategorySellAllEnabled()) {
            ItemStack categorySellButton = createButton(Material.GOLD_INGOT,
                    "&6&lSell Category",
                    Arrays.asList(
                            "&7Sell all items of current category",
                            "&7from your inventory",
                            "",
                            "&eClick to sell all from category!"
                    )
            );
            gui.setItem(buttonRow + 3, categorySellButton);
        }

        // Price Info button
        ItemStack infoButton = createButton(Material.BOOK,
                "&e&lPrice Information",
                Arrays.asList(
                        "&7View item prices for this NPC",
                        "",
                        "&eClick for prices!"
                )
        );
        gui.setItem(buttonRow + 2, infoButton);

        // Compare Prices button (if enabled)
        if (config.isPriceComparisonEnabled()) {
            ItemStack compareButton = createButton(Material.COMPARATOR,
                    "&b&lCompare Prices",
                    Arrays.asList(
                            "&7Compare prices with other NPCs",
                            "",
                            "&eClick to compare!"
                    )
            );
            gui.setItem(buttonRow + 5, compareButton);
        }

        // Close button
        ItemStack closeButton = createButton(Material.BARRIER,
                "&c&lClose",
                Arrays.asList("&7Click to close this menu")
        );
        gui.setItem(buttonRow + 8, closeButton);
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

        // Check limits
        if (config.areLimitsEnabled()) {
            if (playerDataManager.isOnCooldown(player)) {
                long remaining = playerDataManager.getRemainingCooldown(player);
                String message = config.getCooldownActiveMessage()
                        .replace("{cooldown}", String.valueOf(remaining));
                player.sendMessage(message);
                return;
            }

            double dailySold = playerDataManager.getDailySoldAmount(player);
            if (dailySold >= config.getDailyLimit()) {
                player.sendMessage(config.getDailyLimitReachedMessage());
                return;
            }
        }

        double totalPrice = 0;
        int itemCount = 0;
        int sellSlots = 36; // 4 rows
        List<ItemStack> soldItems = new ArrayList<>();

        // Calculate total and collect items
        for (int i = 0; i < sellSlots; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                double price = calculateItemPrice(item, npc);
                if (price > 0) {
                    totalPrice += price;
                    itemCount += item.getAmount();
                    soldItems.add(item.clone());
                }
            }
        }

        if (totalPrice <= 0 || itemCount == 0) {
            player.sendMessage(config.getNoItemsMessage());
            return;
        }

        // Check daily limit with this sale
        if (config.areLimitsEnabled()) {
            double dailySold = playerDataManager.getDailySoldAmount(player);
            if (dailySold + totalPrice > config.getDailyLimit()) {
                double remaining = config.getDailyLimit() - dailySold;
                player.sendMessage("&cThis sale would exceed your daily limit! Remaining: $" +
                        String.format("%.2f", remaining));
                return;
            }
        }

        // Confirmation dialog for expensive items
        if (config.isConfirmationDialogEnabled() && totalPrice >= 100.0) {
            if (!playersInConfirmation.contains(player)) {
                playersInConfirmation.add(player);
                playerConfirmationMap.put(player, System.currentTimeMillis());

                String message = config.getConfirmationRequiredMessage()
                        .replace("{total_price}", String.format("%.2f", totalPrice));
                player.sendMessage(message);

                // Auto-cancel confirmation after 10 seconds
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    playersInConfirmation.remove(player);
                    playerConfirmationMap.remove(player);
                }, 200L);

                return;
            } else {
                playersInConfirmation.remove(player);
                playerConfirmationMap.remove(player);
            }
        }

        // Process the sale
        processSale(player, gui, npc, totalPrice, itemCount, soldItems, sellSlots);
    }

    private void processSale(Player player, Inventory gui, TraderNPC npc,
                             double totalPrice, int itemCount, List<ItemStack> soldItems, int sellSlots) {

        // Clear sold items from GUI
        for (int i = 0; i < sellSlots; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                double price = calculateItemPrice(item, npc);
                if (price > 0) {
                    gui.setItem(i, null);
                }
            }
        }

        // Update player data
        if (config.areLimitsEnabled()) {
            playerDataManager.addSoldAmount(player, totalPrice);
            playerDataManager.setCooldown(player, config.getSellCooldown());
        }

        // Run sell commands
        runSellCommands(player, npc, totalPrice, itemCount);

        // Send success message
        String message = config.getSellSuccessMessage()
                .replace("{total_price}", String.format("%.2f", totalPrice))
                .replace("{item_count}", String.valueOf(itemCount));
        player.sendMessage(message);

        // Play effects
        if (config.areSoundsEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getSound("sell-success")), 1.0f, 1.0f);
        }

        if (config.areParticlesEnabled()) {
            npc.playParticleEffect("sell-success");
        }

        // Update preview
        updatePreview(player, gui, npc);

        config.debugLog(player.getName() + " sold items for $" + totalPrice + " to " + npc.getId());
    }

    public void handleCategorySellAllClick(Player player) {
        String npcId = playerNPCMap.get(player);
        String category = playerCategoryMap.get(player);

        if (npcId == null || category == null) return;

        TraderNPC npc = npcManager.getNPC(npcId);
        if (npc == null || !npc.isEnabled()) return;

        double totalPrice = 0;
        int itemCount = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // Find items in player inventory that match category
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            String itemCategory = config.getItemCategoryName(item.getType().name());
            if (category.equals("all") || category.equals(itemCategory)) {
                double price = calculateItemPrice(item, npc);
                if (price > 0) {
                    totalPrice += price;
                    itemCount += item.getAmount();
                    itemsToRemove.add(item);
                }
            }
        }

        if (totalPrice <= 0) {
            player.sendMessage("&cNo sellable " + category + " items found in your inventory!");
            return;
        }

        // Remove items from inventory
        for (ItemStack item : itemsToRemove) {
            player.getInventory().removeItem(item);
        }

        // Process sale
        runSellCommands(player, npc, totalPrice, itemCount);

        String message = config.getCategorySoldMessage()
                .replace("{category}", category)
                .replace("{total_price}", String.format("%.2f", totalPrice));
        player.sendMessage(message);

        // Play effects
        if (config.areSoundsEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getSound("sell-success")), 1.0f, 1.0f);
        }
    }

    public void handleCategoryClick(Player player, String category) {
        String npcId = playerNPCMap.get(player);
        if (npcId == null) return;

        TraderNPC npc = npcManager.getNPC(npcId);
        if (npc == null) return;

        playerCategoryMap.put(player, category);

        // Play sound
        if (config.areSoundsEnabled()) {
            player.playSound(player.getLocation(),
                    Sound.valueOf(config.getSound("category-switch")), 1.0f, 1.0f);
        }

        // Reopen GUI with new category
        openCategoryGUI(player, npc, category);
    }

    public void handlePriceInfoClick(Player player) {
        String npcId = playerNPCMap.get(player);
        if (npcId == null) return;

        TraderNPC npc = npcManager.getNPC(npcId);
        if (npc == null) return;

        player.sendMessage(ChatColor.GOLD + "=== Price Information ===");
        player.sendMessage(ChatColor.YELLOW + "Trading with: " + npc.getName());

        String category = playerCategoryMap.get(player);
        List<String> itemsToShow;

        if ("all".equals(category)) {
            itemsToShow = Arrays.asList("COBBLESTONE", "IRON_ORE", "DIAMOND", "WHEAT", "IRON_INGOT", "COAL");
        } else {
            itemsToShow = config.getItemCategory(category);
        }

        for (String itemType : itemsToShow) {
            double price = npc.getItemPrice(itemType);
            if (price > 0) {
                String itemName = formatItemName(itemType);
                player.sendMessage(ChatColor.AQUA + itemName + ": " +
                        ChatColor.GREEN + "$" + String.format("%.2f", price));
            }
        }

        player.sendMessage(ChatColor.GRAY + "Put items in the GUI to see their exact prices!");
    }

    public void handleComparePricesClick(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Price Comparison ===");

        Map<String, TraderNPC> allNPCs = npcManager.getAllNPCs();
        String[] sampleItems = {"IRON_ORE", "DIAMOND", "WHEAT"};

        for (String itemType : sampleItems) {
            player.sendMessage(ChatColor.YELLOW + formatItemName(itemType) + ":");

            List<String> priceList = new ArrayList<>();
            for (TraderNPC npc : allNPCs.values()) {
                if (npc.isEnabled()) {
                    double price = npc.getItemPrice(itemType);
                    if (price > 0) {
                        priceList.add(ChatColor.stripColor(npc.getName()) + ": $" +
                                String.format("%.2f", price));
                    }
                }
            }

            if (priceList.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "  No NPCs buy this item");
            } else {
                for (String priceInfo : priceList) {
                    player.sendMessage(ChatColor.AQUA + "  " + priceInfo);
                }
            }
        }
    }

    private void startPreviewTask(Player player, Inventory gui, TraderNPC npc) {
        // Cancel existing task
        BukkitRunnable existingTask = previewTasks.get(player);
        if (existingTask != null) {
            existingTask.cancel();
        }

        BukkitRunnable previewTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !gui.equals(player.getOpenInventory().getTopInventory())) {
                    this.cancel();
                    previewTasks.remove(player);
                    return;
                }

                updatePreview(player, gui, npc);
            }
        };

        previewTask.runTaskTimer(plugin, 10L, 10L); // Update every 0.5 seconds
        previewTasks.put(player, previewTask);
    }

    private void updatePreview(Player player, Inventory gui, TraderNPC npc) {
        double totalValue = 0;
        int itemCount = 0;

        for (int i = 0; i < 36; i++) { // 4 rows for selling
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                double price = calculateItemPrice(item, npc);
                if (price > 0) {
                    totalValue += price;
                    itemCount += item.getAmount();
                }
            }
        }

        // Update sell button with current total
        ItemStack sellButton = gui.getItem(49); // Sell button slot
        if (sellButton != null && sellButton.hasItemMeta()) {
            ItemMeta meta = sellButton.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7Put items in the slots above"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7and click here to sell them!"));
            lore.add("");
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    "&aTotal Value: &e$" + String.format("%.2f", totalValue)));
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    "&aItems: &e" + itemCount));
            lore.add("");
            lore.add(ChatColor.translateAlternateColorCodes('&', "&eClick to sell!"));

            meta.setLore(lore);
            sellButton.setItemMeta(meta);
        }

        playerPreviewMap.put(player, totalValue);
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
                        .collect(Collectors.toList()));
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

    private int getItemCountInCategory(String category) {
        return config.getItemCategory(category).size();
    }

    public void cleanupPlayer(Player player) {
        playerNPCMap.remove(player);
        playerCategoryMap.remove(player);
        playerPreviewMap.remove(player);
        playersInConfirmation.remove(player);
        playerConfirmationMap.remove(player);

        BukkitRunnable task = previewTasks.remove(player);
        if (task != null) {
            task.cancel();
        }
    }

    public TraderNPC getCurrentNPC(Player player) {
        String npcId = playerNPCMap.get(player);
        return npcId != null ? npcManager.getNPC(npcId) : null;
    }

    public boolean isSellGUI(String title) {
        String guiTitle = ChatColor.translateAlternateColorCodes('&', config.getSellGUITitle());
        return title.contains("Sell Items to") || title.equals(guiTitle);
    }

    // Getters for button identification
    public boolean isCategoryButton(ItemStack item, int slot) {
        return slot >= 36 && slot < 45 && item != null;
    }

    public boolean isSellButton(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Sell All Items");
    }

    public boolean isCategorySellButton(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Sell Category");
    }

    public boolean isPriceInfoButton(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Price Information");
    }

    public boolean isComparePricesButton(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Compare Prices");
    }

    public boolean isCloseButton(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Close");
    }
}