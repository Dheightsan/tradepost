package io.github.dheightsan.tradepost.economy;

import org.bukkit.OfflinePlayer;

public interface ShopEconomy {
    boolean isEnabled();

    double balance(OfflinePlayer player);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);

    ShopEconomy DISABLED = new ShopEconomy() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public double balance(OfflinePlayer player) {
            return 0;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return false;
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            return false;
        }

        @Override
        public String format(double amount) {
            return String.format(java.util.Locale.ROOT, "%.2f", amount);
        }
    };
}
