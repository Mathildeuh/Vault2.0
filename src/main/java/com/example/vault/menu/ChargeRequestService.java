package com.example.vault.menu;

import com.example.vault.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;

public class ChargeRequestService implements Listener {
    private final Plugin plugin;
    private final Messages messages;

    private final Map<String, List<PendingRequest>> pendingByRecipient = new ConcurrentHashMap<>();
    private final Set<String> awaitingAmount = ConcurrentHashMap.newKeySet();
    private final Map<String, String> targetBySender = new ConcurrentHashMap<>();
    // Enum de modo para distinguir entre pago directo y solicitud (cobro)
    private enum Mode { PAY, CHARGE }
    private final Map<String, Mode> modeBySender = new ConcurrentHashMap<>();

    public ChargeRequestService(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    private static class PendingRequest {
        final String sender;
        final double amount;

        PendingRequest(String sender, double amount) {
            this.sender = sender;
            this.amount = amount;
        }
    }

    public void addPending(String recipientName, String senderName, double amount) {
        pendingByRecipient.computeIfAbsent(recipientName.toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add(new PendingRequest(senderName, amount));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<PendingRequest> list = pendingByRecipient.get(player.getName().toLowerCase(Locale.ROOT));
        if (list == null || list.isEmpty()) return;
        int max = plugin.getConfig().getInt("pay_pending.max_on_join", 5);
        int total = list.size();
        int shown = Math.min(max, total);
        player.sendMessage(messages.formatChat("pay.pending.header", Collections.singletonMap("count", String.valueOf(total))));
        if (total > shown) {
            Map<String, String> m = new HashMap<>();
            m.put("shown", String.valueOf(shown));
            m.put("total", String.valueOf(total));
            player.sendMessage(messages.formatChat("pay.pending.limit_notice", m));
        }
        for (PendingRequest pr : list.subList(0, shown)) {
            Map<String, String> m2 = new HashMap<>();
            m2.put("player", pr.sender);
            m2.put("amount", String.valueOf(pr.amount));
            // Prefix global + texto de prefijo de solicitud + botón clicable
            TextComponent combined = new TextComponent("");
            for (BaseComponent bc : TextComponent.fromLegacyText(messages.prefix())) combined.addExtra(bc);
            combined.addExtra(new TextComponent(messages.get("pay.request.prefix")));
            TextComponent clickable = new TextComponent(messages.format("pay.request.click", m2));
            clickable.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pay " + pr.sender + " " + pr.amount));
            clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{new TextComponent(messages.format("pay.request.hover", m2))}));
            combined.addExtra(clickable);
            player.spigot().sendMessage(combined);
        }
        if (total > shown) {
            pendingByRecipient.put(player.getName().toLowerCase(Locale.ROOT), new ArrayList<>(list.subList(shown, total)));
        } else {
            pendingByRecipient.remove(player.getName().toLowerCase(Locale.ROOT));
        }
    }

    public void startRequest(Player sender, Player target) {
        targetBySender.put(sender.getName(), target.getName());
        modeBySender.put(sender.getName(), Mode.CHARGE);
    }

    public void requestAmountAndCharge(Player sender) {
        awaitingAmount.add(sender.getName());
        String target = targetBySender.get(sender.getName());
        if (target == null) target = "";
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target);
        sender.sendMessage(messages.formatChat("pay.prompt.enter_amount_charge", placeholders));
    }

    public void startPay(Player sender, Player target) {
        targetBySender.put(sender.getName(), target.getName());
        modeBySender.put(sender.getName(), Mode.PAY);
    }

    public void requestAmountAndPay(Player sender) {
        awaitingAmount.add(sender.getName());
        String target = targetBySender.get(sender.getName());
        if (target == null) target = "";
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target);
        sender.sendMessage(messages.formatChat("pay.prompt.enter_amount_pay", placeholders));
    }

    public boolean isAwaitingAmount(Player player) {
        return awaitingAmount.contains(player.getName());
    }

    public void cancelRequest(Player player) {
        awaitingAmount.remove(player.getName());
        targetBySender.remove(player.getName());
        modeBySender.remove(player.getName());
    }

    private void fulfillRequest(Player sender, double amount) {
        String recipientName = targetBySender.get(sender.getName());
        if (recipientName == null || recipientName.isEmpty()) {
            sender.sendMessage(messages.chat("cmd.pay.usage"));
            return;
        }
        Player recipient = Bukkit.getPlayerExact(recipientName);
        if (recipient != null && recipient.isOnline()) {
            String senderName = sender.getName();
            Map<String, String> m = new HashMap<>();
            m.put("player", senderName);
            m.put("amount", String.valueOf(amount));
            TextComponent combined = new TextComponent("");
            for (BaseComponent bc : TextComponent.fromLegacyText(messages.prefix())) combined.addExtra(bc);
            combined.addExtra(new TextComponent(messages.get("pay.request.prefix")));
            TextComponent clickable = new TextComponent(messages.format("pay.request.click", m));
            clickable.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pay " + senderName + " " + amount));
            clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{new TextComponent(messages.format("pay.request.hover", m))}));
            combined.addExtra(clickable);
            recipient.spigot().sendMessage(combined);
            Map<String, String> ms = new HashMap<>();
            ms.put("player", recipientName);
            ms.put("amount", String.valueOf(amount));
            sender.sendMessage(messages.formatChat("pay.request.sent", ms));
            awaitingAmount.remove(sender.getName());
            targetBySender.remove(sender.getName());
            return;
        }
        // Recipient offline: store request
        addPending(recipientName, sender.getName(), amount);
        Map<String, String> ms2 = new HashMap<>();
        ms2.put("player", recipientName);
        ms2.put("amount", String.valueOf(amount));
        sender.sendMessage(messages.formatChat("pay.request.stored", ms2));
        awaitingAmount.remove(sender.getName());
        targetBySender.remove(sender.getName());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!awaitingAmount.contains(sender.getName())) return;
        String message = event.getMessage().trim();

        // Allow cancel in chat
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.equals("cancel") || lower.equals("cancelar")) {
            cancelRequest(sender);
            event.setCancelled(true);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(message);
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.chat("pay.invalid_amount"));
            event.setCancelled(true);
            return;
        }

        double min = plugin.getConfig().getDouble("pay_limits.min", 0.0);
        double max = plugin.getConfig().getDouble("pay_limits.max", 0.0);
        boolean bypass = sender.hasPermission("vault.pay.bypass_limits") || sender.hasPermission("vault.pay.bypass_min") || sender.hasPermission("vault.pay.bypass_max");
        if (!bypass && min > 0 && amount < min) {
            sender.sendMessage(messages.formatChat("pay.amount_too_small", Collections.singletonMap("min", String.valueOf(min))));
            event.setCancelled(true);
            return;
        }
        if (!bypass && max > 0 && amount > max) {
            sender.sendMessage(messages.formatChat("pay.amount_too_large", Collections.singletonMap("max", String.valueOf(max))));
            event.setCancelled(true);
            return;
        }

        // Decide mode: direct PAY or CHARGE (request)
        Mode m = modeBySender.get(sender.getName());
        if (m == Mode.PAY) {
            final String target = targetBySender.get(sender.getName());
            final double amt = amount;
            // cleanup first
            awaitingAmount.remove(sender.getName());
            modeBySender.remove(sender.getName());
            targetBySender.remove(sender.getName());
            if (target == null || target.isEmpty()) {
                sender.sendMessage(messages.chat("cmd.pay.usage"));
                event.setCancelled(true);
                return;
            }
            // Execute /pay target amount on main thread
            Bukkit.getScheduler().runTask(plugin, () -> sender.performCommand("pay " + target + " " + amt));
            event.setCancelled(true);
            return;
        }

        // Otherwise: CHARGE request flow
        fulfillRequest(sender, amount);
        event.setCancelled(true);
    }
}