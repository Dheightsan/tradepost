package io.github.dheightsan.tradepost;

import io.github.dheightsan.tradepost.command.ShopCommand;
import io.github.dheightsan.tradepost.config.Messages;
import io.github.dheightsan.tradepost.economy.ShopEconomy;
import io.github.dheightsan.tradepost.economy.VaultEconomy;
import io.github.dheightsan.tradepost.gui.MenuListener;
import io.github.dheightsan.tradepost.gui.ShopServices;
import io.github.dheightsan.tradepost.hook.ShopPlaceholders;
import io.github.dheightsan.tradepost.shop.CatalogLoader;
import io.github.dheightsan.tradepost.shop.Market;
import io.github.dheightsan.tradepost.shop.TradeService;
import io.github.dheightsan.tradepost.storage.ShopStorage;
import io.github.dheightsan.tradepost.storage.StorageFactory;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

// not final, mockbukkit subclasses it
public class TradepostPlugin extends JavaPlugin {
    private ShopStorage storage;
    private volatile ShopServices services;
    private volatile ShopEconomy economy = ShopEconomy.DISABLED;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        try {
            storage = StorageFactory.create(getConfig().getConfigurationSection("storage"), getDataFolder(), getLogger());
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Invalid storage settings in config.yml, disabling Tradepost", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        CatalogLoader.Result loaded = loadCatalog();
        Market market = new Market(loaded.catalog(), storage, System::currentTimeMillis, getLogger());
        services = buildServices(market);

        hookEconomy(false);
        getServer().getScheduler().runTask(this, () -> {
            if (!economy.isEnabled()) {
                hookEconomy(true);
            }
        });

        storage.init()
                .thenCompose(ignored -> storage.loadPrices())
                .whenComplete((prices, error) -> getServer().getScheduler().runTask(this, () -> {
                    if (error != null) {
                        getLogger().log(Level.SEVERE, "Could not open the shop database. The shop stays closed "
                                + "until this is fixed and the server restarts.", error);
                        return;
                    }
                    market.loadStates(prices);
                    getLogger().info("Loaded " + prices.size() + " stored prices; shop is open.");
                }));

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        PluginCommand command = getCommand("shop");
        if (command != null) {
            ShopCommand executor = new ShopCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ShopPlaceholders(() -> services == null ? null : services.market(),
                    String.join(", ", getPluginMeta().getAuthors()), getPluginMeta().getVersion()).register();
            getLogger().info("Registered PlaceholderAPI placeholders (%tradepost_buy_<item>% and others).");
        }
    }

    @Override
    public void onDisable() {
        MenuListener.closeAll();
        if (storage != null) {
            storage.close();
        }
    }

    public ShopServices services() {
        return services;
    }

    public ShopStorage storage() {
        return storage;
    }

    public List<String> reloadShop() {
        MenuListener.closeAll();
        reloadConfig();
        CatalogLoader.Result loaded = loadCatalog();
        Market market = services.market();
        market.setCatalog(loaded.catalog());
        services = buildServices(market);
        hookEconomy(false);
        return loaded.warnings();
    }

    private CatalogLoader.Result loadCatalog() {
        CatalogLoader.Result result = CatalogLoader.load(getConfig());
        result.warnings().forEach(warning -> getLogger().warning("config.yml: " + warning));
        getLogger().info("Loaded " + result.catalog().categories().size() + " categories with "
                + result.catalog().items().size() + " items.");
        return result;
    }

    private ShopServices buildServices(Market market) {
        Messages messages = Messages.load(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"));
        return new ShopServices(market, new TradeService(market, () -> economy), messages, () -> economy);
    }

    private void hookEconomy(boolean finalAttempt) {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = ShopEconomy.DISABLED;
            if (finalAttempt) {
                getLogger().warning("Vault is not installed. The shop menu works, but buying and selling are "
                        + "disabled until Vault and an economy plugin are added.");
            }
            return;
        }
        VaultEconomy vault = VaultEconomy.lookup();
        if (vault == null) {
            economy = ShopEconomy.DISABLED;
            if (finalAttempt) {
                getLogger().warning("Vault is installed but no economy plugin is registered with it. Buying and "
                        + "selling are disabled.");
            }
            return;
        }
        boolean changed = economy != vault && !(economy instanceof VaultEconomy);
        economy = vault;
        if (changed) {
            getLogger().info("Using economy provider: " + vault.providerName());
        }
    }
}
