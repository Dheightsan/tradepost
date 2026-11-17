package io.github.dheightsan.tradepost.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class Menu implements InventoryHolder {
    protected Inventory inventory;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public abstract void render();

    public abstract void click(Player player, InventoryClickEvent event);

    public void open(Player player) {
        render();
        player.openInventory(inventory);
    }

    public static Menu of(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder;
        try {
            holder = inventory.getHolder(false);
        } catch (RuntimeException e) {
            // mockbukkit has no getHolder(false)
            holder = inventory.getHolder();
        }
        return holder instanceof Menu menu ? menu : null;
    }
}
