package online.zeptra.npcplugin;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final JavaPlugin plugin;

    // Player data storage
    private final Map<UUID, PlayerSellData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();

    // Daily reset tracking
    private LocalDate lastResetDate;
    private BukkitRunnable dailyResetTask;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lastResetDate = LocalDate.now();
        startDailyResetTask();
    }

    // Cooldown Management
    public boolean isOnCooldown(Player player) {
        Long cooldownEnd = cooldownMap.get(player.getUniqueId());
        if (cooldownEnd == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= cooldownEnd) {
            cooldownMap.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    public void setCooldown(Player player, int seconds) {
        if (seconds <= 0) return;

        long cooldownEnd = System.currentTimeMillis() + (seconds * 1000L);
        cooldownMap.put(player.getUniqueId(), cooldownEnd);
    }

    public long getRemainingCooldown(Player player) {
        Long cooldownEnd = cooldownMap.get(player.getUniqueId());
        if (cooldownEnd == null) {
            return 0;
        }

        long remaining = (cooldownEnd - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void removeCooldown(Player player) {
        cooldownMap.remove(player.getUniqueId());
    }

    // Daily Limit Management
    public double getDailySoldAmount(Player player) {
        PlayerSellData data = getPlayerData(player);

        // Check if data is from today
        if (!data.getDate().equals(LocalDate.now())) {
            data.reset();
        }

        return data.getDailySoldAmount();
    }

    public void addSoldAmount(Player player, double amount) {
        PlayerSellData data = getPlayerData(player);

        // Reset if new day
        if (!data.getDate().equals(LocalDate.now())) {
            data.reset();
        }

        data.addSoldAmount(amount);
        data.incrementTransactionCount();
    }

    public int getDailyTransactionCount(Player player) {
        PlayerSellData data = getPlayerData(player);

        if (!data.getDate().equals(LocalDate.now())) {
            data.reset();
        }

        return data.getTransactionCount();
    }

    public double getRemainingDailyLimit(Player player, double dailyLimit) {
        double sold = getDailySoldAmount(player);
        return Math.max(0, dailyLimit - sold);
    }

    public boolean canSell(Player player, double amount, double dailyLimit) {
        if (isOnCooldown(player)) {
            return false;
        }

        double currentSold = getDailySoldAmount(player);
        return currentSold + amount <= dailyLimit;
    }

    // Statistics
    public PlayerSellStats getPlayerStats(Player player) {
        PlayerSellData data = getPlayerData(player);

        return new PlayerSellStats(
                data.getTotalSoldAmount(),
                data.getTotalTransactionCount(),
                getDailySoldAmount(player),
                getDailyTransactionCount(player),
                data.getLastSellTime(),
                data.getBestSingleSale()
        );
    }

    public void updateBestSale(Player player, double amount) {
        PlayerSellData data = getPlayerData(player);
        if (amount > data.getBestSingleSale()) {
            data.setBestSingleSale(amount);
        }
    }

    // Data Management
    private PlayerSellData getPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        return playerData.computeIfAbsent(uuid, k -> new PlayerSellData());
    }

    public void savePlayerData(Player player) {
        // In a real implementation, this would save to file/database
        // For now, data is kept in memory
        PlayerSellData data = getPlayerData(player);
        data.setLastSellTime(System.currentTimeMillis());
    }

    public void loadPlayerData(Player player) {
        // In a real implementation, this would load from file/database
        // For now, data is created on-demand
        getPlayerData(player);
    }

    public void unloadPlayerData(Player player) {
        // Keep data in memory but could be optimized to save and unload
        savePlayerData(player);
    }

    // Daily Reset System
    private void startDailyResetTask() {
        dailyResetTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkDailyReset();
            }
        };

        // Check every minute for daily reset
        dailyResetTask.runTaskTimer(plugin, 0L, 1200L);
    }

    private void checkDailyReset() {
        LocalDate currentDate = LocalDate.now();

        if (!currentDate.equals(lastResetDate)) {
            performDailyReset();
            lastResetDate = currentDate;
        }
    }

    private void performDailyReset() {
        plugin.getLogger().info("Performing daily reset for player sell data...");

        // Reset all player daily data
        for (PlayerSellData data : playerData.values()) {
            if (!data.getDate().equals(LocalDate.now())) {
                data.resetDailyData();
            }
        }

        plugin.getLogger().info("Daily reset completed!");
    }

    public void forceReset() {
        performDailyReset();
    }

    // Cleanup
    public void shutdown() {
        if (dailyResetTask != null) {
            dailyResetTask.cancel();
        }

        // Save all player data
        playerData.clear();
        cooldownMap.clear();
    }

    // Inner Classes
    private static class PlayerSellData {
        private LocalDate date;
        private double dailySoldAmount;
        private int dailyTransactionCount;
        private double totalSoldAmount;
        private int totalTransactionCount;
        private long lastSellTime;
        private double bestSingleSale;

        public PlayerSellData() {
            this.date = LocalDate.now();
            this.dailySoldAmount = 0.0;
            this.dailyTransactionCount = 0;
            this.totalSoldAmount = 0.0;
            this.totalTransactionCount = 0;
            this.lastSellTime = 0L;
            this.bestSingleSale = 0.0;
        }

        public void addSoldAmount(double amount) {
            this.dailySoldAmount += amount;
            this.totalSoldAmount += amount;
        }

        public void incrementTransactionCount() {
            this.dailyTransactionCount++;
            this.totalTransactionCount++;
        }

        public void reset() {
            this.date = LocalDate.now();
            resetDailyData();
        }

        public void resetDailyData() {
            this.dailySoldAmount = 0.0;
            this.dailyTransactionCount = 0;
            this.date = LocalDate.now();
        }

        // Getters and Setters
        public LocalDate getDate() { return date; }
        public double getDailySoldAmount() { return dailySoldAmount; }
        public int getTransactionCount() { return dailyTransactionCount; }
        public double getTotalSoldAmount() { return totalSoldAmount; }
        public int getTotalTransactionCount() { return totalTransactionCount; }
        public long getLastSellTime() { return lastSellTime; }
        public double getBestSingleSale() { return bestSingleSale; }

        public void setLastSellTime(long lastSellTime) { this.lastSellTime = lastSellTime; }
        public void setBestSingleSale(double bestSingleSale) { this.bestSingleSale = bestSingleSale; }
    }

    public static class PlayerSellStats {
        private final double totalSoldAmount;
        private final int totalTransactions;
        private final double dailySoldAmount;
        private final int dailyTransactions;
        private final long lastSellTime;
        private final double bestSingleSale;

        public PlayerSellStats(double totalSoldAmount, int totalTransactions,
                               double dailySoldAmount, int dailyTransactions,
                               long lastSellTime, double bestSingleSale) {
            this.totalSoldAmount = totalSoldAmount;
            this.totalTransactions = totalTransactions;
            this.dailySoldAmount = dailySoldAmount;
            this.dailyTransactions = dailyTransactions;
            this.lastSellTime = lastSellTime;
            this.bestSingleSale = bestSingleSale;
        }

        // Getters
        public double getTotalSoldAmount() { return totalSoldAmount; }
        public int getTotalTransactions() { return totalTransactions; }
        public double getDailySoldAmount() { return dailySoldAmount; }
        public int getDailyTransactions() { return dailyTransactions; }
        public long getLastSellTime() { return lastSellTime; }
        public double getBestSingleSale() { return bestSingleSale; }

        public double getAveragePerTransaction() {
            return totalTransactions > 0 ? totalSoldAmount / totalTransactions : 0.0;
        }
    }

    // Utility Methods
    public String formatTime(long timeInSeconds) {
        if (timeInSeconds < 60) {
            return timeInSeconds + " seconds";
        } else if (timeInSeconds < 3600) {
            return (timeInSeconds / 60) + " minutes";
        } else {
            return (timeInSeconds / 3600) + " hours";
        }
    }

    public String formatCurrency(double amount) {
        return String.format("$%.2f", amount);
    }

    // Admin Commands Support
    public void resetPlayerData(Player player) {
        PlayerSellData data = getPlayerData(player);
        data.reset();
        removeCooldown(player);
    }

    public void setPlayerDailyAmount(Player player, double amount) {
        PlayerSellData data = getPlayerData(player);
        data.dailySoldAmount = amount;
    }

    public Map<UUID, PlayerSellStats> getAllPlayerStats() {
        Map<UUID, PlayerSellStats> stats = new HashMap<>();

        for (Map.Entry<UUID, PlayerSellData> entry : playerData.entrySet()) {
            PlayerSellData data = entry.getValue();
            stats.put(entry.getKey(), new PlayerSellStats(
                    data.getTotalSoldAmount(),
                    data.getTotalTransactionCount(),
                    data.getDailySoldAmount(),
                    data.getTransactionCount(),
                    data.getLastSellTime(),
                    data.getBestSingleSale()
            ));
        }

        return stats;
    }
}