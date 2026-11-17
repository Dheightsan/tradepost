package io.github.dheightsan.tradepost.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomy implements ShopEconomy {
    private final Economy economy;

    private VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    public static VaultEconomy lookup() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultEconomy(rsp.getProvider());
    }

    public String providerName() {
        return economy.getName();
    }

    @Override
    public boolean isEnabled() {
        return economy.isEnabled();
    }

    @Override
    public double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
