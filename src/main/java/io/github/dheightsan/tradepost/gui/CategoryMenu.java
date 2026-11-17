package io.github.dheightsan.tradepost.gui;

import io.github.dheightsan.tradepost.config.Messages;
import io.github.dheightsan.tradepost.economy.ShopEconomy;
import io.github.dheightsan.tradepost.shop.Category;
import io.github.dheightsan.tradepost.shop.ShopItem;
import io.github.dheightsan.tradepost.shop.TradeService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class CategoryMenu extends Menu {
    static final int PAGE_SIZE = 45;
    static final int SLOT_BACK = 45;
    static final int SLOT_PREVIOUS = 48;
    static final int SLOT_BALANCE = 49;
    static final int SLOT_NEXT = 50;

    private final ShopServices services;
    private final Category category;
    private final int page;
    private final Player viewer;

    public CategoryMenu(ShopServices services, Category category, int page, Player viewer) {
        this.services = services;
        this.viewer = viewer;
        this.category = category;
        this.page = Math.max(0, Math.min(page, pageCount(category) - 1));
        // plain text so messages.yml decides the title color
        TagResolver title = Placeholder.unparsed("category",
                PlainTextComponentSerializer.plainText().serialize(Messages.parse(category.title())));
        TagResolver pages = TagResolver.resolver(
                Placeholder.unparsed("page", String.valueOf(this.page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pageCount(category))));
        this.inventory = Bukkit.createInventory(this, 54, services.messages().get("gui.category.title", title, pages));
    }

    static int pageCount(Category category) {
        return Math.max(1, (category.items().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public Category category() {
        return category;
    }

    public int page() {
        return page;
    }

    @Override
    public void render() {
        inventory.clear();
        Messages messages = services.messages();
        ShopEconomy economy = services.economy().get();
        List<ShopItem> items = category.items();
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            inventory.setItem(i, Icons.product(items.get(start + i), services.market(), messages, economy));
        }
        for (int slot = PAGE_SIZE; slot < 54; slot++) {
            inventory.setItem(slot, Icons.filler());
        }
        inventory.setItem(SLOT_BACK, Icons.simple(Material.ARROW, messages.item("gui.back"), List.of()));
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS, Icons.simple(Material.PAPER, messages.item("gui.previous"), List.of()));
        }
        if (page < pageCount(category) - 1) {
            inventory.setItem(SLOT_NEXT, Icons.simple(Material.PAPER, messages.item("gui.next"), List.of()));
        }
        inventory.setItem(SLOT_BALANCE, balanceIcon(economy));
    }

    private ItemStack balanceIcon(ShopEconomy economy) {
        Messages messages = services.messages();
        if (!economy.isEnabled()) {
            return Icons.simple(Material.BARRIER, messages.item("gui.balance-off"), List.of());
        }
        return Icons.simple(Material.GOLD_INGOT, messages.item("gui.balance",
                Placeholder.unparsed("balance", economy.format(economy.balance(viewer)))), List.of());
    }

    @Override
    public void click(Player player, InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == SLOT_BACK) {
            new MainMenu(services, player).open(player);
            return;
        }
        if (slot == SLOT_PREVIOUS && page > 0) {
            new CategoryMenu(services, category, page - 1, player).open(player);
            return;
        }
        if (slot == SLOT_NEXT && page < pageCount(category) - 1) {
            new CategoryMenu(services, category, page + 1, player).open(player);
            return;
        }
        int index = page * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index >= category.items().size()) {
            return;
        }
        ShopItem item = category.items().get(index);
        TradeService.Result result = switch (event.getClick()) {
            case LEFT -> services.trades().buy(player, item, 1);
            case SHIFT_LEFT -> services.trades().buy(player, item, item.material().getMaxStackSize());
            case RIGHT -> services.trades().sell(player, item, 1);
            case SHIFT_RIGHT -> services.trades().sell(player, item, Integer.MAX_VALUE);
            default -> null;
        };
        if (result == null) {
            return;
        }
        boolean buying = event.getClick().isLeftClick();
        report(player, item, result, buying);
        render();
    }

    private void report(Player player, ShopItem item, TradeService.Result result, boolean buying) {
        ShopEconomy economy = services.economy().get();
        TagResolver tags = TagResolver.resolver(
                Placeholder.component("item", Icons.itemName(item)),
                Placeholder.unparsed("amount", String.valueOf(result.amount())),
                Placeholder.unparsed("total", economy.format(result.total())));
        String key = switch (result.outcome()) {
            case SUCCESS -> buying ? "trade.bought" : "trade.sold";
            case NOT_READY -> "loading";
            case ECONOMY_DISABLED -> "economy-disabled";
            case NOT_BUYABLE -> "trade.not-buyable";
            case NOT_SELLABLE -> "trade.not-sellable";
            case INVENTORY_FULL -> "trade.inventory-full";
            case NOT_ENOUGH_MONEY -> "trade.not-enough-money";
            case NOTHING_TO_SELL -> "trade.nothing-to-sell";
            case ECONOMY_ERROR -> "trade.economy-error";
        };
        services.messages().send(player, key, tags);
        player.playSound(player.getLocation(),
                result.success() ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.2f);
    }
}
