package com.example.vault.menu;

import com.example.vault.i18n.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

public class PayMenuService implements Listener {
    private final Plugin plugin;
    private final Economy economy;
    private final Messages messages;
    private final ChargeRequestService chargeRequestService;
    private final java.util.Map<java.util.UUID, String> submenuTargets = new java.util.concurrent.ConcurrentHashMap<>();

    public PayMenuService(Plugin plugin, Economy economy, Messages messages, ChargeRequestService chargeRequestService) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
        this.chargeRequestService = chargeRequestService;
    }

    // Helper: create a player head item compatible across versions
    private ItemStack createHeadItem() {
        try {
            Material head = Material.valueOf("PLAYER_HEAD");
            return new ItemStack(head, 1);
        } catch (IllegalArgumentException ignored) {
            Material skull = Material.valueOf("SKULL_ITEM");
            return new ItemStack(skull, 1, (short) 3);
        }
    }

    // Helper: create a head for a specific player with meta set
    private ItemStack createHeadItemFor(Player target) {
        ItemStack head = createHeadItem();
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(target.getName());
            // Try to set owning player across versions
            try {
                SkullMeta skullMeta = (SkullMeta) meta;
                try {
                    // Use reflection to support setOwningPlayer when available
                    java.lang.reflect.Method m = skullMeta.getClass().getMethod(
                            "setOwningPlayer", org.bukkit.OfflinePlayer.class);
                    m.invoke(skullMeta, Bukkit.getOfflinePlayer(target.getUniqueId()));
                } catch (Throwable err) {
                    skullMeta.setOwner(target.getName());
                }
                head.setItemMeta(skullMeta);
            } catch (ClassCastException e) {
                head.setItemMeta(meta);
            }
        }
        return head;
    }

    private boolean isHeadMaterial(Material m) {
        String n = m.name();
        return n.equals("PLAYER_HEAD") || n.equals("SKULL_ITEM");
    }

    private String getTitleMain() {
        return messages.get("menu.title_main");
    }

    private String formatTitlePlayer(String playerName) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("player", playerName);
        return messages.format("menu.title_player", map);
    }

    public void openMainMenu(Player player) {
        int size = plugin.getConfig().getInt("pay_menu.size", 27);
        if (size % 9 != 0) size = 27;
        Inventory inv = Bukkit.createInventory(null, size, getTitleMain());

        boolean showSelf = plugin.getConfig().getBoolean("pay_menu.show_self", false);
        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!showSelf && p.getUniqueId().equals(player.getUniqueId())) continue;
            if (slot >= size) break;
            ItemStack head = createHeadItemFor(p);
            inv.setItem(slot++, head);
        }

        player.openInventory(inv);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        org.bukkit.inventory.Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        // Main menu: only react to clicks in the top inventory
        if (title.equals(getTitleMain())) {
            if (!clickedInv.equals(event.getView().getTopInventory())) return;
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                item = clickedInv.getItem(event.getSlot());
                if (item == null || item.getType() == Material.AIR) return;
            }
            if (isHeadMaterial(item.getType())) {
                String targetName = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : null;
                if (targetName != null) {
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null && target.isOnline()) {
                        openPlayerMenu(player, target);
                    } else {
                        player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", targetName)));
                    }
                }
            }
            return;
        }

        // Submenu: ensure clicks are in the top inventory and resolve target
        String targetName = submenuTargets.get(player.getUniqueId());
        if (targetName != null && title.equals(formatTitlePlayer(targetName))) {
            if (!clickedInv.equals(event.getView().getTopInventory())) return;
            event.setCancelled(true);
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null || !target.isOnline()) {
                player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", targetName)));
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                item = clickedInv.getItem(event.getSlot());
                if (item == null || item.getType() == Material.AIR) return;
            }
            ItemMeta meta = item.getItemMeta();
            String name = meta != null ? meta.getDisplayName() : "";
            if (messages.get("pay.menu.item.view_balance").equals(name)) {
                economy.createPlayerAccount(target);
                String amount = economy.format(economy.getBalance(target));
                String label = messages.get("pay.view.money_label");
                player.sendMessage(messages.prefix() + label + " " + amount);
            } else if (messages.get("pay.menu.item.pay").equals(name)) {
                chargeRequestService.startPay(player, target);
                chargeRequestService.requestAmountAndPay(player);
            } else if (messages.get("pay.menu.item.charge").equals(name)) {
                chargeRequestService.startRequest(player, target);
                chargeRequestService.requestAmountAndCharge(player);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            submenuTargets.remove(((Player) event.getPlayer()).getUniqueId());
        }
    }

    public void openPlayerMenu(Player player, Player target) {
        int size = 9;
        Inventory inv = Bukkit.createInventory(null, size, formatTitlePlayer(target.getName()));

        ItemStack payItem = new ItemStack(Material.EMERALD);
        ItemMeta payMeta = payItem.getItemMeta();
        if (payMeta != null) {
            payMeta.setDisplayName(messages.get("pay.menu.item.pay"));
            payItem.setItemMeta(payMeta);
        }

        ItemStack viewItem = new ItemStack(Material.PAPER);
        ItemMeta viewMeta = viewItem.getItemMeta();
        if (viewMeta != null) {
            viewMeta.setDisplayName(messages.get("pay.menu.item.view_balance"));
            // Add lore with current balance of target
            economy.createPlayerAccount(target);
            String amount = economy.format(economy.getBalance(target));
            String label = messages.get("pay.view.money_label");
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(label + " " + amount);
            viewMeta.setLore(lore);
            viewItem.setItemMeta(viewMeta);
        }

        ItemStack chargeItem = new ItemStack(Material.REDSTONE);
        ItemMeta chargeMeta = chargeItem.getItemMeta();
        if (chargeMeta != null) {
            chargeMeta.setDisplayName(messages.get("pay.menu.item.charge"));
            chargeItem.setItemMeta(chargeMeta);
        }

        inv.setItem(2, payItem);
        inv.setItem(4, viewItem);
        inv.setItem(6, chargeItem);

        // Abrir primero, luego registrar el objetivo (evita que onClose limpie el mapa)
        player.openInventory(inv);
        Bukkit.getScheduler().runTask(plugin, () -> submenuTargets.put(player.getUniqueId(), target.getName()));
    }
}