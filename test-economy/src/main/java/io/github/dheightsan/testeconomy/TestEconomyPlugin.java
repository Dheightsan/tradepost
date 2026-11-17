package io.github.dheightsan.testeconomy;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TestEconomyPlugin extends JavaPlugin {
    private static final double START = 1000.0;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private File file;

    @Override
    public void onEnable() {
        file = new File(getDataFolder(), "balances.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            balances.put(UUID.fromString(key), yaml.getDouble(key));
        }
        Bukkit.getServicesManager().register(Economy.class, new Provider(), this, ServicePriority.Normal);
        getLogger().info("Registered test economy with " + balances.size() + " saved balances.");
    }

    @Override
    public void onDisable() {
        YamlConfiguration yaml = new YamlConfiguration();
        balances.forEach((id, value) -> yaml.set(id.toString(), value));
        try {
            yaml.save(file);
        } catch (IOException e) {
            getLogger().warning("Could not save balances: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            return false;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (args.length > 1) {
            balances.put(target.getUniqueId(), Double.parseDouble(args[1]));
        }
        sender.sendMessage(args[0] + " balance: " + String.format(Locale.ROOT, "%.2f", get(target)));
        return true;
    }

    private double get(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), START);
    }

    private final class Provider extends AbstractEconomy {
        @Override
        public boolean isEnabled() {
            return TestEconomyPlugin.this.isEnabled();
        }

        @Override
        public String getName() {
            return "TestEconomy";
        }

        @Override
        public boolean hasBankSupport() {
            return false;
        }

        @Override
        public int fractionalDigits() {
            return 2;
        }

        @Override
        public String format(double amount) {
            return String.format(Locale.ROOT, "$%.2f", amount);
        }

        @Override
        public String currencyNamePlural() {
            return "dollars";
        }

        @Override
        public String currencyNameSingular() {
            return "dollar";
        }

        @Override
        public boolean hasAccount(OfflinePlayer player) {
            return true;
        }

        @Override
        public double getBalance(OfflinePlayer player) {
            return get(player);
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return get(player) >= amount;
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            double balance = get(player);
            if (amount < 0 || balance < amount) {
                return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "insufficient funds");
            }
            balances.put(player.getUniqueId(), balance - amount);
            return new EconomyResponse(amount, balance - amount, EconomyResponse.ResponseType.SUCCESS, null);
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            if (amount < 0) {
                return new EconomyResponse(0, get(player), EconomyResponse.ResponseType.FAILURE, "negative amount");
            }
            double balance = get(player) + amount;
            balances.put(player.getUniqueId(), balance);
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }

        @Override
        public boolean createPlayerAccount(OfflinePlayer player) {
            return true;
        }

        @Override
        public boolean hasAccount(String name) {
            return true;
        }

        @Override
        public boolean hasAccount(String name, String world) {
            return true;
        }

        @Override
        public double getBalance(String name) {
            return getBalance(Bukkit.getOfflinePlayer(name));
        }

        @Override
        public double getBalance(String name, String world) {
            return getBalance(name);
        }

        @Override
        public boolean has(String name, double amount) {
            return has(Bukkit.getOfflinePlayer(name), amount);
        }

        @Override
        public boolean has(String name, String world, double amount) {
            return has(name, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String name, double amount) {
            return withdrawPlayer(Bukkit.getOfflinePlayer(name), amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String name, String world, double amount) {
            return withdrawPlayer(name, amount);
        }

        @Override
        public EconomyResponse depositPlayer(String name, double amount) {
            return depositPlayer(Bukkit.getOfflinePlayer(name), amount);
        }

        @Override
        public EconomyResponse depositPlayer(String name, String world, double amount) {
            return depositPlayer(name, amount);
        }

        @Override
        public boolean createPlayerAccount(String name) {
            return true;
        }

        @Override
        public boolean createPlayerAccount(String name, String world) {
            return true;
        }

        private EconomyResponse noBanks() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "no banks");
        }

        @Override
        public EconomyResponse createBank(String name, String player) {
            return noBanks();
        }

        @Override
        public EconomyResponse deleteBank(String name) {
            return noBanks();
        }

        @Override
        public EconomyResponse bankBalance(String name) {
            return noBanks();
        }

        @Override
        public EconomyResponse bankHas(String name, double amount) {
            return noBanks();
        }

        @Override
        public EconomyResponse bankWithdraw(String name, double amount) {
            return noBanks();
        }

        @Override
        public EconomyResponse bankDeposit(String name, double amount) {
            return noBanks();
        }

        @Override
        public EconomyResponse isBankOwner(String name, String player) {
            return noBanks();
        }

        @Override
        public EconomyResponse isBankMember(String name, String player) {
            return noBanks();
        }

        @Override
        public List<String> getBanks() {
            return List.of();
        }
    }
}
