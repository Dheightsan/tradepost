package io.github.dheightsan.tradepost.shop;

import io.github.dheightsan.tradepost.FakeEconomy;
import io.github.dheightsan.tradepost.MemoryStorage;
import io.github.dheightsan.tradepost.economy.ShopEconomy;
import io.github.dheightsan.tradepost.pricing.PriceSettings;
import io.github.dheightsan.tradepost.pricing.PriceState;
import io.github.dheightsan.tradepost.storage.Trade;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.ECONOMY_DISABLED;
import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.ECONOMY_ERROR;
import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.INVENTORY_FULL;
import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.NOTHING_TO_SELL;
import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.NOT_BUYABLE;
import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.NOT_ENOUGH_MONEY;
import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.NOT_READY;
import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.NOT_SELLABLE;
import static io.github.dheightsan.tradepost.shop.TradeService.Outcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeServiceTest {
    private static final long NOW = 50_000L;
    private static final PriceSettings PRICING = new PriceSettings(0.01, 0.5, 2.0, 0);

    private final ShopItem coal = new ShopItem("coal", "ores", Material.COAL, null, 10.0, 2.0, PRICING);
    private final ShopItem bread = new ShopItem("bread", "food", Material.BREAD, null, 5.0, -1, PRICING);
    private final ShopItem bone = new ShopItem("bone", "drops", Material.BONE, null, -1, 1.0, PRICING);

    private ServerMock server;
    private PlayerMock player;
    private MemoryStorage storage;
    private FakeEconomy economy;
    private Market market;
    private ShopEconomy activeEconomy;
    private TradeService trades;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        storage = new MemoryStorage();
        economy = new FakeEconomy();
        activeEconomy = economy;
        ShopCatalog catalog = new ShopCatalog("Shop", 3, List.of(new Category("all", "All", Material.CHEST, 0,
                List.of(coal, bread, bone))));
        market = new Market(catalog, storage, () -> NOW, Logger.getLogger("test"));
        market.loadStates(Map.of());
        trades = new TradeService(market, () -> activeEconomy);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private int count(Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Test
    void buyingChargesGivesItemsAndRecordsTheTrade() {
        economy.set(player, 100);

        TradeService.Result result = trades.buy(player, coal, 2);

        assertEquals(SUCCESS, result.outcome());
        assertEquals(20.10, result.total(), 1e-9);
        assertEquals(79.90, economy.balance(player), 1e-9);
        assertEquals(2, count(Material.COAL));
        assertEquals(1, storage.trades.size());
        Trade trade = storage.trades.getFirst();
        assertEquals(new Trade(player.getUniqueId(), "coal", Trade.Kind.BUY, 2, 20.10, NOW), trade);
        assertEquals(1.0201, storage.prices.get("coal").multiplier(), 1e-9);
        assertEquals(10.20, market.buyPrice(coal), 1e-9, "next unit is more expensive");
    }

    @Test
    void sellingPaysTakesItemsAndLowersThePrice() {
        player.getInventory().addItem(new ItemStack(Material.COAL, 10));

        TradeService.Result result = trades.sell(player, coal, 3);

        assertEquals(SUCCESS, result.outcome());
        assertEquals(2.0 + 1.98 + 1.9602, result.total(), 0.006);
        assertEquals(result.total(), economy.balance(player), 1e-9);
        assertEquals(7, count(Material.COAL));
        assertTrue(market.sellPrice(coal) < 2.0);
    }

    @Test
    void sellAllSellsEveryMatchingStack() {
        player.getInventory().addItem(new ItemStack(Material.COAL, 64), new ItemStack(Material.COAL, 10));

        TradeService.Result result = trades.sell(player, coal, Integer.MAX_VALUE);

        assertEquals(SUCCESS, result.outcome());
        assertEquals(74, result.amount());
        assertEquals(0, count(Material.COAL));
    }

    @Test
    void renamedItemsAreNeverSold() {
        ItemStack named = new ItemStack(Material.COAL, 5);
        named.editMeta(meta -> meta.displayName(Component.text("Lucky coal")));
        player.getInventory().addItem(named);

        assertEquals(NOTHING_TO_SELL, trades.sell(player, coal, Integer.MAX_VALUE).outcome());
        assertEquals(5, count(Material.COAL));
    }

    @Test
    void notEnoughMoneyChangesNothing() {
        economy.set(player, 5);

        TradeService.Result result = trades.buy(player, coal, 1);

        assertEquals(NOT_ENOUGH_MONEY, result.outcome());
        assertEquals(5, economy.balance(player));
        assertEquals(0, count(Material.COAL));
        assertTrue(storage.trades.isEmpty());
        assertEquals(10.0, market.buyPrice(coal));
    }

    @Test
    void purchaseIsTrimmedToFreeSpace() {
        economy.set(player, 10_000);
        ItemStack[] full = new ItemStack[36];
        for (int i = 0; i < full.length; i++) {
            full[i] = new ItemStack(Material.DIRT, 64);
        }
        full[0] = new ItemStack(Material.COAL, 60);
        player.getInventory().setStorageContents(full);

        TradeService.Result result = trades.buy(player, coal, 64);

        assertEquals(SUCCESS, result.outcome());
        assertEquals(4, result.amount());
        assertEquals(64, count(Material.COAL));
    }

    @Test
    void fullInventoryRefusesThePurchase() {
        economy.set(player, 10_000);
        ItemStack[] full = new ItemStack[36];
        for (int i = 0; i < full.length; i++) {
            full[i] = new ItemStack(Material.DIRT, 64);
        }
        player.getInventory().setStorageContents(full);

        assertEquals(INVENTORY_FULL, trades.buy(player, coal, 1).outcome());
        assertEquals(10_000, economy.balance(player));
    }

    @Test
    void buyOnlyAndSellOnlyItemsAreRespected() {
        economy.set(player, 100);
        player.getInventory().addItem(new ItemStack(Material.BREAD, 3));

        assertEquals(NOT_SELLABLE, trades.sell(player, bread, 1).outcome());
        assertEquals(NOT_BUYABLE, trades.buy(player, bone, 1).outcome());
    }

    @Test
    void refusedPaymentGivesTheItemsBack() {
        economy.refuseDeposits = true;
        player.getInventory().addItem(new ItemStack(Material.COAL, 8));

        assertEquals(ECONOMY_ERROR, trades.sell(player, coal, 8).outcome());
        assertEquals(8, count(Material.COAL));
        assertTrue(storage.trades.isEmpty());
    }

    @Test
    void refusedWithdrawalGivesNothing() {
        economy.set(player, 100);
        economy.refuseWithdrawals = true;

        assertEquals(ECONOMY_ERROR, trades.buy(player, coal, 1).outcome());
        assertEquals(0, count(Material.COAL));
    }

    @Test
    void noEconomyMeansNoTrades() {
        activeEconomy = ShopEconomy.DISABLED;
        player.getInventory().addItem(new ItemStack(Material.COAL, 8));

        assertEquals(ECONOMY_DISABLED, trades.buy(player, coal, 1).outcome());
        assertEquals(ECONOMY_DISABLED, trades.sell(player, coal, 1).outcome());
        assertEquals(8, count(Material.COAL));
    }

    @Test
    void tradingWaitsForStoredPrices() {
        Market loading = new Market(market.catalog(), storage, () -> NOW, Logger.getLogger("test"));
        TradeService early = new TradeService(loading, () -> economy);
        economy.set(player, 100);

        assertEquals(NOT_READY, early.buy(player, coal, 1).outcome());

        loading.loadStates(Map.of("coal", new PriceState(1.5, NOW)));
        TradeService.Result result = early.buy(player, coal, 1);
        assertEquals(SUCCESS, result.outcome());
        assertEquals(15.0, result.total(), 1e-9, "starts from the stored price, not the base");
    }
}
