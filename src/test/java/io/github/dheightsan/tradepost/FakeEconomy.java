package io.github.dheightsan.tradepost;

import io.github.dheightsan.tradepost.economy.ShopEconomy;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FakeEconomy implements ShopEconomy {
    private final Map<UUID, Double> balances = new HashMap<>();
    public boolean refuseDeposits;
    public boolean refuseWithdrawals;

    public void set(OfflinePlayer player, double amount) {
        balances.put(player.getUniqueId(), amount);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public double balance(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (refuseWithdrawals || balance(player) < amount) {
            return false;
        }
        balances.put(player.getUniqueId(), balance(player) - amount);
        return true;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (refuseDeposits) {
            return false;
        }
        balances.put(player.getUniqueId(), balance(player) + amount);
        return true;
    }

    @Override
    public String format(double amount) {
        return String.format(Locale.ROOT, "$%.2f", amount);
    }
}
