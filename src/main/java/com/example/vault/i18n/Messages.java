package com.example.vault.i18n;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Map;

public class Messages {
    private final Plugin plugin;
    private FileConfiguration primary;
    private FileConfiguration fallback;

    public Messages(Plugin plugin, String language) {
        this.plugin = plugin;
        reload(language);
    }

    private void saveResourceOnce(String name) {
        File out = new File(plugin.getDataFolder(), name);
        if (!out.exists()) {
            plugin.saveResource(name, false);
        }
    }

    public void reload(String language) {
        saveResourceOnce("messages_en.yml");
        saveResourceOnce("messages_es.yml");
        this.fallback = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages_en.yml"));
        File langFile = new File(plugin.getDataFolder(), "messages_" + language.toLowerCase() + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file messages_" + language + ".yml not found. Using English.");
            langFile = new File(plugin.getDataFolder(), "messages_en.yml");
        }
        this.primary = YamlConfiguration.loadConfiguration(langFile);
    }

    public String get(String key) {
        String s = primary.getString(key);
        if (s == null) s = fallback.getString(key, key);
        return s;
    }

    public String color(String key) {
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', get(key));
    }

    public String format(String key, Map<String, String> values) {
        String s = get(key);
        for (Map.Entry<String, String> e : values.entrySet()) {
            s = s.replace("%" + e.getKey() + "%", e.getValue());
        }
        return s;
    }

    public String prefix() {
        String raw = primary.getString("prefix");
        if (raw == null) raw = fallback.getString("prefix", "");
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String chat(String key) {
        return prefix() + net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', get(key));
    }

    public String formatChat(String key, Map<String, String> values) {
        return prefix() + net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', format(key, values));
    }
}