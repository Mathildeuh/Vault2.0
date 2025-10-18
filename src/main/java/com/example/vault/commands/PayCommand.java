package com.example.vault.commands;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.example.vault.menu.PayMenuService;
import com.example.vault.i18n.Messages;


public class PayCommand implements CommandExecutor {
    private final Plugin plugin;
    private final Economy economy;
    private final PayMenuService payMenuService;
    private final Messages messages;

    public PayCommand(Plugin plugin, Economy economy, PayMenuService payMenuService, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.payMenuService = payMenuService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.chat("pay.only_players"));
            return true;
        }
        Player player = (Player) sender;
        String permPay = plugin.getConfig().getString("permissions.pay_use", "vault.pay");
        if (permPay != null) {
            String p = permPay.trim();
            if (!(p.isEmpty() || p.equalsIgnoreCase("none") || p.equalsIgnoreCase("disabled"))) {
                if (!player.hasPermission(p)) {
                    player.sendMessage(messages.chat("pay.no_permission"));
                    return true;
                }
            }
        }
        if (args.length == 0) {
            payMenuService.openMainMenu(player);
            return true;
        }
        if (args.length == 1) {
            // /pay <player> open menu for target
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", args[0])));
                return true;
            }
            payMenuService.openPlayerMenu(player, target);
            return true;
        }
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(messages.formatChat("pay.player_offline", java.util.Collections.singletonMap("player", args[0])));
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(messages.chat("pay.invalid_amount"));
                return true;
            }
            String permBypassMin = plugin.getConfig().getString("permissions.pay_bypass_min", "vault.pay.bypass_min");
            String permBypassMax = plugin.getConfig().getString("permissions.pay_bypass_max", "vault.pay.bypass_max");
            double min = plugin.getConfig().getDouble("pay_limits.min", 0.0);
            double max = plugin.getConfig().getDouble("pay_limits.max", 0.0);
            if (min > 0 && amount < min && !player.hasPermission(permBypassMin)) {
                player.sendMessage(messages.formatChat("pay.amount_too_small", java.util.Collections.singletonMap("min", economy.format(min))));
                return true;
            }
            if (max > 0 && amount > max && !player.hasPermission(permBypassMax)) {
                player.sendMessage(messages.formatChat("pay.amount_too_large", java.util.Collections.singletonMap("max", economy.format(max))));
                return true;
            }
            economy.createPlayerAccount(player);
            economy.createPlayerAccount(target);
            if (economy.getBalance(player) < amount) {
                player.sendMessage(messages.chat("pay.not_enough_money"));
                return true;
            }
            EconomyResponse resp = economy.withdrawPlayer(player, amount);
            if (!resp.transactionSuccess()) {
                player.sendMessage(messages.chat("pay.withdraw_failed"));
                return true;
            }
            economy.depositPlayer(target, amount);
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("amount", economy.format(amount));
            player.sendMessage(messages.formatChat("pay.sent_ok", placeholders));
            java.util.Map<String, String> placeholders2 = new java.util.HashMap<>();
            placeholders2.put("player", player.getName());
            placeholders2.put("amount", economy.format(amount));
            target.sendMessage(messages.formatChat("pay.received_ok", placeholders2));
            return true;
        }
        return true;
    }
}