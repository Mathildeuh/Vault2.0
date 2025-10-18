package com.example.vault.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public final class LogoPrinter {
    private LogoPrinter() {}

    public static void printEnable(JavaPlugin plugin) {
        printBanner(plugin, true);
    }

    public static void printDisable(JavaPlugin plugin) {
        printBanner(plugin, false);
    }

    private static void printBanner(JavaPlugin plugin, boolean enabled) {
        PluginDescriptionFile desc = plugin.getDescription();
        String name = desc.getName();
        String version = desc.getVersion();
        String authors = String.join(", ", desc.getAuthors());

        String line = ChatColor.GOLD + "==============================================================";
        String status = enabled ? (ChatColor.GREEN + "enabled") : (ChatColor.RED + "disabled");

        send(line);
        send(ChatColor.YELLOW + " " + name + ChatColor.GRAY + " — " + ChatColor.WHITE + "Internal Economy (Vault API)" );
        send(ChatColor.GRAY + " Version: " + ChatColor.WHITE + version + ChatColor.GRAY + "    Authors: " + ChatColor.WHITE + (authors.isEmpty() ? "unknown" : authors));
        send(ChatColor.GRAY + " Status: " + status);
        send(line);
    }

    private static void send(String msg) {
        Bukkit.getConsoleSender().sendMessage(msg);
    }
}