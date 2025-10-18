package com.example.vault.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import com.example.vault.util.PlayerResolver;

public class VaultPlaceholderExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final Economy economy;

    public VaultPlaceholderExpansion(Plugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public String getIdentifier() {
        return "vault";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keep registered across reloads
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        if (params == null) return "";
        String key = params.toLowerCase();
        switch (key) {
            case "balance": {
                economy.createPlayerAccount(player);
                double bal = economy.getBalance(player);
                // raw number
                return String.valueOf(bal);
            }
            case "balance_formatted": {
                economy.createPlayerAccount(player);
                double bal = economy.getBalance(player);
                // formatted using Economy
                return economy.format(bal);
            }
            default:
                // support keys like balance_formatted_<playername>
                if (key.startsWith("balance_formatted_")) {
                    String name = key.substring("balance_formatted_".length());
                    OfflinePlayer other = PlayerResolver.resolveByNameWithOfflineFallback(plugin, name);
                    if (other == null) return "";
                    economy.createPlayerAccount(other);
                    return economy.format(economy.getBalance(other));
                }
                if (key.startsWith("balance_")) {
                    String name = key.substring("balance_".length());
                    OfflinePlayer other = PlayerResolver.resolveByNameWithOfflineFallback(plugin, name);
                    if (other == null) return "";
                    economy.createPlayerAccount(other);
                    return String.valueOf(economy.getBalance(other));
                }
                return "";
        }
    }
}