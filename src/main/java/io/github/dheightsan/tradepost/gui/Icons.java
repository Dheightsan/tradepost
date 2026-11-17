package io.github.dheightsan.tradepost.gui;

import io.github.dheightsan.tradepost.config.Messages;
import io.github.dheightsan.tradepost.economy.ShopEconomy;
import io.github.dheightsan.tradepost.shop.Market;
import io.github.dheightsan.tradepost.shop.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Icons {
    private Icons() {
    }

    static ItemStack simple(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Messages.noItalic(name));
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta(meta -> meta.setHideTooltip(true));
        return stack;
    }

    static Component itemName(ShopItem item) {
        return item.name() != null
                ? Messages.parse(item.name())
                : Component.translatable(item.material().translationKey());
    }

    static ItemStack product(ShopItem item, Market market, Messages messages, ShopEconomy economy) {
        TagResolver name = Placeholder.component("item", itemName(item));
        TagResolver trend = Placeholder.component("trend", trend(market.multiplier(item), messages));

        List<Component> lore = new ArrayList<>();
        if (item.canBuy()) {
            lore.add(messages.item("gui.lore.buy", name, trend,
                    Placeholder.unparsed("price", economy.format(market.buyPrice(item)))));
        } else {
            lore.add(messages.item("gui.lore.no-buy", name));
        }
        if (item.canSell()) {
            lore.add(messages.item("gui.lore.sell", name, trend,
                    Placeholder.unparsed("price", economy.format(market.sellPrice(item)))));
        } else {
            lore.add(messages.item("gui.lore.no-sell", name));
        }
        lore.add(Component.empty());
        if (!economy.isEnabled()) {
            lore.addAll(messages.itemLines("gui.lore.economy-off"));
        } else {
            if (item.canBuy()) {
                lore.addAll(messages.itemLines("gui.lore.hint-buy",
                        Placeholder.unparsed("stack", String.valueOf(item.material().getMaxStackSize()))));
            }
            if (item.canSell()) {
                lore.addAll(messages.itemLines("gui.lore.hint-sell"));
            }
        }
        return simple(item.material(), messages.item("gui.lore.name", name), lore);
    }

    static Component trend(double multiplier, Messages messages) {
        double percent = (multiplier - 1.0) * 100.0;
        if (Math.abs(percent) < 0.5) {
            return messages.get("gui.trend.flat");
        }
        TagResolver value = Placeholder.unparsed("percent",
                String.format(Locale.ROOT, "%.0f", Math.abs(percent)));
        return messages.get(percent > 0 ? "gui.trend.up" : "gui.trend.down", value);
    }
}
