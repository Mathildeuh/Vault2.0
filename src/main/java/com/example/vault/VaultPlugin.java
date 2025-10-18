package com.example.vault;

import com.example.vault.commands.BalanceCommand;
import com.example.vault.commands.PayCommand;
import com.example.vault.commands.VaultCommand;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.menu.PayMenuService;
import com.example.vault.i18n.Messages;
import com.example.vault.menu.ChargeRequestService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class VaultPlugin extends JavaPlugin {
    private Economy economy;
    private PayMenuService payMenuService;
    private Messages messages;

    @Override
    public void onEnable() {
        // Ensure data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        // Save default config if not exists
        saveDefaultConfig();
        // Clean obsolete sections after defaults are written
        migrateConfig();

        // Load messages based on config language
        String lang = getConfig().getString("language", "en");
        messages = new Messages(this, lang);

        // Create our internal Economy provider and register it in ServicesManager
        SimpleEconomy provider = new SimpleEconomy(this);
        this.economy = provider;
        getServer().getServicesManager().register(Economy.class, provider, this, ServicePriority.Highest);

        // Register PlaceholderAPI expansion if plugin is present
        // if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
        //     new VaultPlaceholderExpansion(this, economy).register();
        // }

        // Register commands using our Economy
        if (getCommand("balance") != null) {
            getCommand("balance").setExecutor(new BalanceCommand(this, economy, messages));
        }
        if (getCommand("pay") != null) {
            // After economy initialization
            ChargeRequestService chargeRequestService = new ChargeRequestService(this, messages);
            getServer().getPluginManager().registerEvents(chargeRequestService, this);
            payMenuService = new PayMenuService(this, economy, messages, chargeRequestService);
            getServer().getPluginManager().registerEvents(payMenuService, this);
            // Register commands
            getCommand("pay").setExecutor(new PayCommand(this, economy, payMenuService, messages));
        }
        if (getCommand("vault") != null) {
            getCommand("vault").setExecutor(new VaultCommand(this, messages));
        }

        getLogger().info(messages.get("plugin.enabled"));
    }

    public void reloadPluginState() {
        // Reload config and messages using current language
        reloadConfig();
        // Clean obsolete sections after reload
        migrateConfig();
        String lang = getConfig().getString("language", "en");
        messages.reload(lang);
    }

    private void migrateConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        boolean changed = false;
        if (cfg.isConfigurationSection("permissions")) {
            cfg.set("permissions", null);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("Removed obsolete 'permissions' section from config.yml");
        }
    }

    @Override
    public void onDisable() {
        // Unregister our Economy service
        getServer().getServicesManager().unregister(Economy.class, economy);
        getLogger().info(messages.get("plugin.disabled"));
    }
}