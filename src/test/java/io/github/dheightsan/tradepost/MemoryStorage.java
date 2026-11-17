package io.github.dheightsan.tradepost;

import io.github.dheightsan.tradepost.pricing.PriceState;
import io.github.dheightsan.tradepost.storage.ShopStorage;
import io.github.dheightsan.tradepost.storage.Trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MemoryStorage implements ShopStorage {
    public final Map<String, PriceState> prices = new HashMap<>();
    public final List<Trade> trades = new ArrayList<>();

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, PriceState>> loadPrices() {
        return CompletableFuture.completedFuture(Map.copyOf(prices));
    }

    @Override
    public CompletableFuture<Void> recordTrade(Trade trade, PriceState newState) {
        prices.put(trade.itemId(), newState);
        trades.add(trade);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Trade>> recentTrades(UUID player, int limit) {
        return CompletableFuture.completedFuture(trades.stream()
                .filter(t -> t.player().equals(player))
                .limit(limit)
                .toList());
    }

    @Override
    public void close() {
    }
}
