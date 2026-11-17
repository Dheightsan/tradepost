package io.github.dheightsan.tradepost.storage;

import io.github.dheightsan.tradepost.pricing.PriceState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class SqlShopStorage implements ShopStorage {
    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");

    private final DataSource dataSource;
    private final AutoCloseable closeable;
    private final SqlDialect dialect;
    private final Logger logger;
    private final String pricesTable;
    private final String tradesTable;
    private final ExecutorService executor;

    public SqlShopStorage(DataSource dataSource, SqlDialect dialect, String tablePrefix, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.closeable = dataSource instanceof AutoCloseable c ? c : null;
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.logger = Objects.requireNonNull(logger, "logger");
        if (tablePrefix == null || !PREFIX.matcher(tablePrefix).matches()) {
            throw new IllegalArgumentException("table prefix may only contain letters, digits and '_': " + tablePrefix);
        }
        this.pricesTable = tablePrefix + "prices";
        this.tradesTable = tablePrefix + "trades";
        // single thread so writes stay in order
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Tradepost-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletableFuture<Void> init() {
        return run(connection -> {
            try (Statement st = connection.createStatement()) {
                st.executeUpdate(dialect.createPrices(pricesTable));
                st.executeUpdate(dialect.createTrades(tradesTable));
                String index = dialect.createTradesIndex(tradesTable);
                if (index != null) {
                    st.executeUpdate(index);
                }
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Map<String, PriceState>> loadPrices() {
        return run(connection -> {
            Map<String, PriceState> result = new HashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT item_id, multiplier, updated_at FROM " + pricesTable);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    double multiplier = rs.getDouble(2);
                    if (!(multiplier > 0.0) || Double.isInfinite(multiplier)) {
                        logger.warning("Ignoring stored price for '" + id + "': invalid multiplier " + multiplier);
                        continue;
                    }
                    result.put(id, new PriceState(multiplier, rs.getLong(3)));
                }
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Void> recordTrade(Trade trade, PriceState newState) {
        Objects.requireNonNull(trade, "trade");
        Objects.requireNonNull(newState, "newState");
        return run(connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement price = connection.prepareStatement(dialect.upsertPrice(pricesTable));
                 PreparedStatement log = connection.prepareStatement("INSERT INTO " + tradesTable
                         + " (player_uuid, item_id, kind, amount, total, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                price.setString(1, trade.itemId());
                price.setDouble(2, newState.multiplier());
                price.setLong(3, newState.updatedAt());
                price.executeUpdate();

                log.setString(1, trade.player().toString());
                log.setString(2, trade.itemId());
                log.setString(3, trade.kind().name());
                log.setInt(4, trade.amount());
                log.setDouble(5, trade.total());
                log.setLong(6, trade.createdAt());
                log.executeUpdate();

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<Trade>> recentTrades(UUID player, int limit) {
        Objects.requireNonNull(player, "player");
        int capped = Math.max(1, Math.min(limit, 100));
        return run(connection -> {
            List<Trade> trades = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT item_id, kind, amount, total, created_at FROM " + tradesTable
                            + " WHERE player_uuid = ? ORDER BY created_at DESC, id DESC LIMIT ?")) {
                ps.setString(1, player.toString());
                ps.setInt(2, capped);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Trade.Kind kind;
                        try {
                            kind = Trade.Kind.valueOf(rs.getString(2));
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                        trades.add(new Trade(player, rs.getString(1), kind, rs.getInt(3), rs.getDouble(4),
                                rs.getLong(5)));
                    }
                }
            }
            return trades;
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Storage did not finish pending writes within 10 seconds; some trades may be lost.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not close the connection pool", e);
            }
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private <T> CompletableFuture<T> run(SqlWork<T> work) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    return work.apply(connection);
                } catch (SQLException e) {
                    throw new CompletionException(e);
                }
            }, executor);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("storage is closed", e));
        }
    }
}
