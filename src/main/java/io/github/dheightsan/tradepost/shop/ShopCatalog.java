package io.github.dheightsan.tradepost.shop;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ShopCatalog {
    private final List<Category> categories;
    private final Map<String, Category> categoriesById = new LinkedHashMap<>();
    private final Map<String, ShopItem> itemsById = new LinkedHashMap<>();
    private final String menuTitle;
    private final int menuRows;

    public ShopCatalog(String menuTitle, int menuRows, List<Category> categories) {
        this.menuTitle = menuTitle;
        this.menuRows = menuRows;
        this.categories = List.copyOf(categories);
        for (Category category : this.categories) {
            categoriesById.put(category.id(), category);
            for (ShopItem item : category.items()) {
                itemsById.put(item.id(), item);
            }
        }
    }

    public String menuTitle() {
        return menuTitle;
    }

    public int menuRows() {
        return menuRows;
    }

    public List<Category> categories() {
        return categories;
    }

    public Optional<Category> category(String id) {
        return Optional.ofNullable(categoriesById.get(id));
    }

    public Optional<ShopItem> item(String id) {
        return Optional.ofNullable(itemsById.get(id));
    }

    public Collection<ShopItem> items() {
        return itemsById.values();
    }
}
