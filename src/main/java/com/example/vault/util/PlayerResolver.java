package com.example.vault.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Server;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Resolución segura de jugadores evitando el uso de API deprecada.
 */
public final class PlayerResolver {
    private PlayerResolver() {}

    /**
     * Resuelve un jugador por nombre priorizando jugadores en línea y
     * luego buscando entre los jugadores offline registrados por el servidor.
     * Retorna null si no se encuentra.
     */
    public static OfflinePlayer resolveByName(Plugin plugin, String name) {
        if (name == null || name.isEmpty()) return null;
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer p : plugin.getServer().getOfflinePlayers()) {
            String n = p.getName();
            if (n != null && n.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public static OfflinePlayer resolveByName(org.bukkit.Server server, String name) {
        if (name == null || name.isEmpty()) return null;
        Player online = server.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer p : server.getOfflinePlayers()) {
            String n = p.getName();
            if (n != null && n.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Igual que resolveByName, pero si el servidor está en offline-mode y
     * no se encuentra el jugador, genera un UUID offline determinista
     * y retorna el OfflinePlayer asociado a ese UUID.
     */
    public static OfflinePlayer resolveByNameWithOfflineFallback(Server server, String name) {
        OfflinePlayer found = resolveByName(server, name);
        if (found != null) return found;
        if (!server.getOnlineMode()) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return server.getOfflinePlayer(offlineUuid);
        }
        return null;
    }

    public static OfflinePlayer resolveByNameWithOfflineFallback(Plugin plugin, String name) {
        org.bukkit.Server server = plugin.getServer();
        OfflinePlayer found = resolveByName(server, name);
        if (found != null) return found;
        boolean allowFallback = true;
        try {
            if (plugin instanceof org.bukkit.plugin.java.JavaPlugin) {
                org.bukkit.plugin.java.JavaPlugin jp = (org.bukkit.plugin.java.JavaPlugin) plugin;
                allowFallback = jp.getConfig().getBoolean("offline-uuid-fallback", true);
            }
        } catch (Exception ignored) {}
        if (!server.getOnlineMode() && allowFallback) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return server.getOfflinePlayer(offlineUuid);
        }
        return null;
    }
}