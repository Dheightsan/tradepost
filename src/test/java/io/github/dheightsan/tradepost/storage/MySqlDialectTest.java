package io.github.dheightsan.tradepost.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.dheightsan.tradepost.pricing.PriceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MySqlDialectTest {
    private ShopStorage storage;

    @BeforeEach
    void open() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:tp_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(2);
        storage = new SqlShopStorage(new HikariDataSource(config), SqlDialect.MYSQL, "tp_", Logger.getLogger("test"));
        storage.init().get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void upsertAndHistoryWork() throws Exception {
        UUID player = UUID.randomUUID();
        storage.recordTrade(new Trade(player, "emerald", Trade.Kind.BUY, 2, 241.2, 1L), new PriceState(1.02, 1L));
        storage.recordTrade(new Trade(player, "emerald", Trade.Kind.SELL, 1, 35, 2L), new PriceState(1.01, 2L));

        assertEquals(new PriceState(1.01, 2L), storage.loadPrices().get(10, TimeUnit.SECONDS).get("emerald"));
        assertEquals(2, storage.recentTrades(player, 10).get(10, TimeUnit.SECONDS).size());
    }

    @Test
    void initRunsTwiceWithoutErrors() throws Exception {
        storage.init().get(10, TimeUnit.SECONDS);
    }
}
