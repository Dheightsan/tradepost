package io.github.dheightsan.tradepost;

import io.github.dheightsan.tradepost.gui.CategoryMenu;
import io.github.dheightsan.tradepost.gui.MainMenu;
import io.github.dheightsan.tradepost.gui.Menu;
import io.github.dheightsan.tradepost.hook.ShopPlaceholders;
import io.github.dheightsan.tradepost.shop.Market;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradepostPluginTest {
    private ServerMock server;
    private TradepostPlugin plugin;

    @BeforeEach
    void setUp() throws InterruptedException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(TradepostPlugin.class);
        for (int i = 0; i < 100 && !plugin.services().market().isReady(); i++) {
            Thread.sleep(20);
            server.getScheduler().performOneTick();
        }
        assertTrue(plugin.services().market().isReady(), "stored prices never finished loading");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static InventoryClickEvent click(PlayerMock player, int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
    }

    @Test
    void createsDatabaseAndDefaultFiles() {
        assertTrue(new File(plugin.getDataFolder(), "config.yml").isFile());
        assertTrue(new File(plugin.getDataFolder(), "messages.yml").isFile());
        assertTrue(new File(plugin.getDataFolder(), "shop.db").isFile());
    }

    @Test
    void shopCommandOpensTheMainMenuAndCategoriesOpenOnClick() {
        PlayerMock player = server.addPlayer();
        player.performCommand("shop");

        assertInstanceOf(MainMenu.class, Menu.of(player.getOpenInventory().getTopInventory()));
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(10), "blocks category icon");

        InventoryClickEvent event = click(player, 10, ClickType.LEFT);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "items must never be taken out of the menu");
        CategoryMenu menu = assertInstanceOf(CategoryMenu.class,
                Menu.of(player.getOpenInventory().getTopInventory()));
        assertEquals("blocks", menu.category().id());
    }

    @Test
    void withoutVaultTheShopOpensButRefusesTrades() {
        PlayerMock player = server.addPlayer();
        player.performCommand("shop ores");
        server.getPluginManager().callEvent(click(player, 0, ClickType.LEFT));

        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.nextComponentMessage());
        assertTrue(message.contains("Trading is disabled"), message);
    }

    @Test
    void permissionsAreChecked() {
        PlayerMock player = server.addPlayer();
        player.performCommand("shop reload");
        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.nextComponentMessage());
        assertTrue(message.contains("permission"), message);

        player.setOp(true);
        player.performCommand("shop reload");
        message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.nextComponentMessage());
        assertTrue(message.startsWith("[Shop] Reloaded"), message);
    }

    @Test
    void placeholdersResolve() {
        Market market = plugin.services().market();
        assertEquals("300.00", ShopPlaceholders.resolve(market, "buy_diamond"));
        assertEquals("90.00", ShopPlaceholders.resolve(market, "sell_diamond"));
        assertEquals("+0.0", ShopPlaceholders.resolve(market, "change_diamond"));
        assertEquals("", ShopPlaceholders.resolve(market, "buy_bone"), "sell-only item has no buy price");
        assertEquals("", ShopPlaceholders.resolve(market, "buy_nope"));
    }
}
