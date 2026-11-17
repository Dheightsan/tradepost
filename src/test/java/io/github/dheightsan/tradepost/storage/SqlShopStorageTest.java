package io.github.dheightsan.tradepost.storage;

import io.github.dheightsan.tradepost.pricing.PriceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlShopStorageTest {
    private static final Logger LOG = Logger.getLogger("test");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @TempDir
    Path dir;

    private final List<ShopStorage> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(ShopStorage::close);
    }

    private ShopStorage sqlite() throws Exception {
        ShopStorage storage = StorageFactory.sqlite(new File(dir.toFile(), "shop.db"), "tp_", LOG);
        opened.add(storage);
        await(storage.init());
        return storage;
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    @Test
    void emptyDatabaseHasNoPrices() throws Exception {
        assertTrue(await(sqlite().loadPrices()).isEmpty());
    }

    @Test
    void initIsIdempotent() throws Exception {
        ShopStorage storage = sqlite();
        await(storage.init());
        await(storage.init());
        assertTrue(await(storage.loadPrices()).isEmpty());
    }

    @Test
    void tradeStoresPriceAndHistory() throws Exception {
        ShopStorage storage = sqlite();
        await(storage.recordTrade(new Trade(ALEX, "diamond", Trade.Kind.BUY, 3, 912.5, 1000L),
                new PriceState(1.03, 1000L)));

        assertEquals(Map.of("diamond", new PriceState(1.03, 1000L)), await(storage.loadPrices()));
        List<Trade> history = await(storage.recentTrades(ALEX, 10));
        assertEquals(List.of(new Trade(ALEX, "diamond", Trade.Kind.BUY, 3, 912.5, 1000L)), history);
    }

    @Test
    void laterTradesOverwriteThePrice() throws Exception {
        ShopStorage storage = sqlite();
        storage.recordTrade(new Trade(ALEX, "coal", Trade.Kind.BUY, 1, 8, 1L), new PriceState(1.01, 1L));
        storage.recordTrade(new Trade(ALEX, "coal", Trade.Kind.SELL, 5, 9, 2L), new PriceState(0.96, 2L));
        assertEquals(new PriceState(0.96, 2L), await(storage.loadPrices()).get("coal"));
    }

    @Test
    void writesAreAppliedInSubmissionOrder() throws Exception {
        ShopStorage storage = sqlite();
        for (int i = 1; i <= 200; i++) {
            storage.recordTrade(new Trade(ALEX, "iron_ingot", Trade.Kind.BUY, 1, 25, i),
                    new PriceState(1.0 + i / 1000.0, i));
        }
        assertEquals(new PriceState(1.2, 200L), await(storage.loadPrices()).get("iron_ingot"));
        assertEquals(100, await(storage.recentTrades(ALEX, 1000)).size(), "history query is capped at 100");
    }

    @Test
    void dataSurvivesARestart() throws Exception {
        ShopStorage first = sqlite();
        await(first.recordTrade(new Trade(STEVE, "wheat", Trade.Kind.SELL, 64, 60.5, 5L), new PriceState(0.77, 5L)));
        first.close();
        opened.remove(first);

        ShopStorage second = sqlite();
        assertEquals(new PriceState(0.77, 5L), await(second.loadPrices()).get("wheat"));
        assertEquals(1, await(second.recentTrades(STEVE, 5)).size());
    }

    @Test
    void closeWaitsForQueuedWrites() throws Exception {
        ShopStorage first = sqlite();
        for (int i = 0; i < 50; i++) {
            first.recordTrade(new Trade(STEVE, "item_" + i, Trade.Kind.BUY, 1, 1, i), new PriceState(1.5, i));
        }
        first.close();
        opened.remove(first);

        assertEquals(50, await(sqlite().loadPrices()).size());
    }

    @Test
    void historyIsPerPlayerNewestFirstAndLimited() throws Exception {
        ShopStorage storage = sqlite();
        storage.recordTrade(new Trade(ALEX, "a", Trade.Kind.BUY, 1, 1, 10L), new PriceState(1, 10L));
        storage.recordTrade(new Trade(STEVE, "b", Trade.Kind.BUY, 1, 1, 20L), new PriceState(1, 20L));
        storage.recordTrade(new Trade(ALEX, "c", Trade.Kind.SELL, 2, 3, 30L), new PriceState(1, 30L));
        storage.recordTrade(new Trade(ALEX, "d", Trade.Kind.SELL, 2, 3, 40L), new PriceState(1, 40L));

        List<Trade> alex = await(storage.recentTrades(ALEX, 2));
        assertEquals(List.of("d", "c"), alex.stream().map(Trade::itemId).toList());
        assertEquals(List.of("b"), await(storage.recentTrades(STEVE, 10)).stream().map(Trade::itemId).toList());
    }

    @Test
    void tablePrefixKeepsShopsApart() throws Exception {
        File file = new File(dir.toFile(), "shared.db");
        ShopStorage a = StorageFactory.sqlite(file, "one_", LOG);
        ShopStorage b = StorageFactory.sqlite(file, "two_", LOG);
        opened.add(a);
        opened.add(b);
        await(a.init());
        await(b.init());
        await(a.recordTrade(new Trade(ALEX, "x", Trade.Kind.BUY, 1, 1, 1L), new PriceState(1.1, 1L)));
        assertTrue(await(b.loadPrices()).isEmpty());
        assertNotEquals(Map.of(), await(a.loadPrices()));
    }

    @Test
    void rejectsUnsafeTablePrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> StorageFactory.sqlite(new File(dir.toFile(), "x.db"), "x; DROP TABLE y; --", LOG));
    }

    @Test
    void callsAfterCloseFailInsteadOfHanging() throws Exception {
        ShopStorage storage = sqlite();
        storage.close();
        opened.remove(storage);
        CompletableFuture<Map<String, PriceState>> late = storage.loadPrices();
        assertTrue(late.isCompletedExceptionally());
    }
}
