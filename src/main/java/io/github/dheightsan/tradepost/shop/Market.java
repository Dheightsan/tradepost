package io.github.dheightsan.tradepost.shop;

import io.github.dheightsan.tradepost.pricing.PriceModel;
import io.github.dheightsan.tradepost.pricing.PriceState;
import io.github.dheightsan.tradepost.pricing.Quote;
import io.github.dheightsan.tradepost.storage.ShopStorage;
import io.github.dheightsan.tradepost.storage.Trade;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Market {
    private final ShopStorage storage;
    private final LongSupplier clock;
    private final Logger logger;
    private final Map<String, PriceState> states = new ConcurrentHashMap<>();
    private volatile ShopCatalog catalog;
    private volatile boolean ready;

    public Market(ShopCatalog catalog, ShopStorage storage, LongSupplier clock, Logger logger) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public ShopCatalog catalog() {
        return catalog;
    }

    public void setCatalog(ShopCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public boolean isReady() {
        return ready;
    }

    public void loadStates(Map<String, PriceState> stored) {
        states.putAll(stored);
        ready = true;
    }

    public long now() {
        return clock.getAsLong();
    }

    public PriceState state(ShopItem item) {
        return states.getOrDefault(item.id(), PriceState.neutral(now()));
    }

    public double multiplier(ShopItem item) {
        return PriceModel.multiplierAt(item.pricing(), state(item), now());
    }

    public double buyPrice(ShopItem item) {
        return item.canBuy() ? PriceModel.unitPrice(item.buyPrice(), item.pricing(), state(item), now()) : -1;
    }

    public double sellPrice(ShopItem item) {
        return item.canSell() ? PriceModel.unitPrice(item.sellPrice(), item.pricing(), state(item), now()) : -1;
    }

    public Quote quoteBuy(ShopItem item, int amount) {
        return PriceModel.quoteBuy(item.buyPrice(), item.pricing(), state(item), amount, now());
    }

    public Quote quoteSell(ShopItem item, int amount) {
        return PriceModel.quoteSell(item.sellPrice(), item.pricing(), state(item), amount, now());
    }

    public void commit(UUID player, ShopItem item, Trade.Kind kind, Quote quote) {
        states.put(item.id(), quote.after());
        Trade trade = new Trade(player, item.id(), kind, quote.amount(), quote.total(), now());
        storage.recordTrade(trade, quote.after()).exceptionally(error -> {
            logger.log(Level.SEVERE, "Could not save " + kind + " of " + quote.amount() + "x " + item.id()
                    + " by " + player, error);
            return null;
        });
    }
}
