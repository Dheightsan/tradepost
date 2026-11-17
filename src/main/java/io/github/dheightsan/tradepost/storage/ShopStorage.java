package io.github.dheightsan.tradepost.storage;

import io.github.dheightsan.tradepost.pricing.PriceState;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ShopStorage {
    CompletableFuture<Void> init();

    CompletableFuture<Map<String, PriceState>> loadPrices();

    CompletableFuture<Void> recordTrade(Trade trade, PriceState newState);

    CompletableFuture<List<Trade>> recentTrades(UUID player, int limit);

    void close();
}
