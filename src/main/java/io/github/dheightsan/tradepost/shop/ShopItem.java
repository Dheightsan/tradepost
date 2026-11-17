package io.github.dheightsan.tradepost.shop;

import io.github.dheightsan.tradepost.pricing.PriceSettings;
import org.bukkit.Material;

import java.util.Objects;

public record ShopItem(String id, String categoryId, Material material, String name, double buyPrice,
                       double sellPrice, PriceSettings pricing) {
    public ShopItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(pricing, "pricing");
    }

    public boolean canBuy() {
        return buyPrice >= 0;
    }

    public boolean canSell() {
        return sellPrice >= 0;
    }
}
