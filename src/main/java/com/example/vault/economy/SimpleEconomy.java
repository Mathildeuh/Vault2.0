package com.example.vault.economy;

import com.example.vault.Database;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SimpleEconomy implements Economy {
    private final Plugin plugin;
    private final Database database; // may be null
    private final Map<UUID, Double> balances = new HashMap<>();
    private final DecimalFormat formatter = new DecimalFormat("#,##0.00");

    public SimpleEconomy(Plugin plugin) {
        this(plugin, null);
    }

    public SimpleEconomy(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "VaultEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public java.util.List<String> getBanks() {
        return java.util.Collections.emptyList();
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return formatter.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return "dollars";
    }

    @Override
    public String currencyNameSingular() {
        return "dollar";
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return balances.containsKey(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        balances.putIfAbsent(player.getUniqueId(), 0.0);
        if (database != null && database.isEnabled()) {
            try {
                database.saveBalance(player.getUniqueId(), 0.0);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to create account in DB: " + e.getMessage());
            }
        }
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        double bal = getBalance(player);
        if (bal < amount) {
            return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Insufficient funds");
        }
        double newBal = bal - amount;
        balances.put(player.getUniqueId(), newBal);
        if (database != null && database.isEnabled()) {
            try {
                database.saveBalance(player.getUniqueId(), newBal);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save balance after withdraw: " + e.getMessage());
            }
        }
        return new EconomyResponse(amount, newBal, ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        double bal = getBalance(player);
        double newBal = bal + amount;
        balances.put(player.getUniqueId(), newBal);
        if (database != null && database.isEnabled()) {
            try {
                database.saveBalance(player.getUniqueId(), newBal);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save balance after deposit: " + e.getMessage());
            }
        }
        return new EconomyResponse(amount, newBal, ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // -- Unused legacy methods (string-based variants) --
    @Override
    public boolean hasAccount(String playerName) { return false; }
    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return false;
        return createPlayerAccount(p);
    }
    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }
    @Override
    public double getBalance(String playerName) { return 0; }
    @Override
    public boolean has(String playerName, double amount) { return false; }
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }

    // world-aware string variants (delegate to OfflinePlayer-based implementations)
    @Override
    public boolean hasAccount(String playerName, String worldName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null && hasAccount(p);
    }
    @Override
    public double getBalance(String playerName, String worldName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null ? getBalance(p) : 0.0;
    }
    @Override
    public boolean has(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null && has(p, amount);
    }
    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return new EconomyResponse(0,0,ResponseType.FAILURE,"Player not found");
        return withdrawPlayer(p, amount);
    }
    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return new EconomyResponse(0,0,ResponseType.FAILURE,"Player not found");
        return depositPlayer(p, amount);
    }
    @Override
    public EconomyResponse bankBalance(String bank) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse bankHas(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse bankWithdraw(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse bankDeposit(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse isBankOwner(String bank, String playerName) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse isBankMember(String bank, String playerName) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public net.milkbowl.vault.economy.EconomyResponse createBank(String bank, String player) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    @Override
    public net.milkbowl.vault.economy.EconomyResponse createBank(String bank, org.bukkit.OfflinePlayer player) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    @Override
    public net.milkbowl.vault.economy.EconomyResponse deleteBank(String bank) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    public EconomyResponse bankWithdraw(String bank, org.bukkit.OfflinePlayer player, double amount) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse bankDeposit(String bank, org.bukkit.OfflinePlayer player, double amount) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse isBankOwner(String bank, org.bukkit.OfflinePlayer player) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse isBankMember(String bank, org.bukkit.OfflinePlayer player) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }

    // Persistence methods
    public void load() throws IOException {
        if (database != null && database.isEnabled()) {
            try {
                Map<UUID, Double> fromDb = database.loadAllBalances();
                balances.clear();
                balances.putAll(fromDb);
                return;
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load balances from DB: " + e.getMessage());
            }
        }
        // Fallback to YAML file
        File file = new File(plugin.getDataFolder(), "balances.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                double balance = config.getDouble(key);
                balances.put(uuid, balance);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid balance entry: " + key);
            }
        }
    }

    public void save() throws IOException {
        if (database != null && database.isEnabled()) {
            // save every balance
            for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
                try {
                    database.saveBalance(entry.getKey(), entry.getValue());
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to save balance to DB: " + e.getMessage());
                }
            }
            return;
        }
        File file = new File(plugin.getDataFolder(), "balances.yml");
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        config.save(file);
    }

    public void close() {
        if (database != null) {
            database.close();
        }
    }
}

