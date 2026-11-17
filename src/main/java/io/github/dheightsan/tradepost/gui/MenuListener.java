package io.github.dheightsan.tradepost.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.ArrayList;
import java.util.List;

public final class MenuListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Menu menu = Menu.of(event.getView().getTopInventory());
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            menu.click(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (Menu.of(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    public static void closeAll() {
        List<HumanEntity> viewers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Menu.of(player.getOpenInventory().getTopInventory()) != null) {
                viewers.add(player);
            }
        }
        viewers.forEach(HumanEntity::closeInventory);
    }
}
