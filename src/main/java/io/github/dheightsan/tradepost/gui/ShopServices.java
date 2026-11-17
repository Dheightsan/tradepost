package io.github.dheightsan.tradepost.gui;

import io.github.dheightsan.tradepost.config.Messages;
import io.github.dheightsan.tradepost.economy.ShopEconomy;
import io.github.dheightsan.tradepost.shop.Market;
import io.github.dheightsan.tradepost.shop.TradeService;

import java.util.function.Supplier;

public record ShopServices(Market market, TradeService trades, Messages messages, Supplier<ShopEconomy> economy) {
}
