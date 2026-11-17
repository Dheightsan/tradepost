package io.github.dheightsan.tradepost.hook;

import io.github.dheightsan.tradepost.shop.Market;
import io.github.dheightsan.tradepost.shop.ShopItem;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public final class ShopPlaceholders extends PlaceholderExpansion {
    private final Supplier<Market> market;
    private final String author;
    private final String version;

    public ShopPlaceholders(Supplier<Market> market, String author, String version) {
        this.market = market;
        this.author = author;
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tradepost";
    }

    @Override
    public @NotNull String getAuthor() {
        return author;
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return resolve(market.get(), params);
    }

    public static String resolve(Market market, String params) {
        int split = params.indexOf('_');
        if (market == null || split <= 0) {
            return null;
        }
        String type = params.substring(0, split).toLowerCase(Locale.ROOT);
        Optional<ShopItem> found = market.catalog().item(params.substring(split + 1).toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            return "";
        }
        ShopItem item = found.get();
        return switch (type) {
            case "buy" -> item.canBuy() ? money(market.buyPrice(item)) : "";
            case "sell" -> item.canSell() ? money(market.sellPrice(item)) : "";
            case "change" -> String.format(Locale.ROOT, "%+.1f", (market.multiplier(item) - 1.0) * 100.0);
            default -> null;
        };
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
