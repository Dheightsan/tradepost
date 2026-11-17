package io.github.dheightsan.tradepost.gui;

import io.github.dheightsan.tradepost.config.Messages;
import io.github.dheightsan.tradepost.shop.Category;
import io.github.dheightsan.tradepost.shop.ShopCatalog;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

public final class MainMenu extends Menu {
    private final ShopServices services;
    private final Map<Integer, Category> bySlot = new HashMap<>();
    private final Player viewer;

    public MainMenu(ShopServices services, Player viewer) {
        this.services = services;
        this.viewer = viewer;
        ShopCatalog catalog = services.market().catalog();
        this.inventory = Bukkit.createInventory(this, catalog.menuRows() * 9, Messages.parse(catalog.menuTitle()));
    }

    @Override
    public void render() {
        inventory.clear();
        bySlot.clear();
        Messages messages = services.messages();
        for (Category category : services.market().catalog().categories()) {
            var title = Placeholder.component("category", Messages.parse(category.title()));
            var count = Placeholder.unparsed("count", String.valueOf(category.items().size()));
            inventory.setItem(category.slot(), Icons.simple(category.icon(),
                    messages.item("gui.category.name", title),
                    messages.itemLines("gui.category.lore", title, count)));
            bySlot.put(category.slot(), category);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, Icons.filler());
            }
        }
    }

    @Override
    public void click(Player player, InventoryClickEvent event) {
        Category category = bySlot.get(event.getSlot());
        if (category != null) {
            new CategoryMenu(services, category, 0, viewer).open(player);
        }
    }
}
