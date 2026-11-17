package io.github.dheightsan.tradepost.shop;

import org.bukkit.Material;

import java.util.List;

public record Category(String id, String title, Material icon, int slot, List<ShopItem> items) {
    public Category {
        items = List.copyOf(items);
    }
}
