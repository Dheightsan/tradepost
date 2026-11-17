package io.github.dheightsan.tradepost.command;

import io.github.dheightsan.tradepost.TradepostPlugin;
import io.github.dheightsan.tradepost.config.Messages;
import io.github.dheightsan.tradepost.economy.ShopEconomy;
import io.github.dheightsan.tradepost.gui.CategoryMenu;
import io.github.dheightsan.tradepost.gui.MainMenu;
import io.github.dheightsan.tradepost.gui.ShopServices;
import io.github.dheightsan.tradepost.shop.Category;
import io.github.dheightsan.tradepost.shop.Market;
import io.github.dheightsan.tradepost.shop.ShopItem;
import io.github.dheightsan.tradepost.storage.Trade;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class ShopCommand implements TabExecutor {
    static final String PERM_USE = "tradepost.use";
    static final String PERM_RELOAD = "tradepost.reload";
    static final String PERM_PRICE = "tradepost.price";
    static final String PERM_HISTORY = "tradepost.history";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final TradepostPlugin plugin;

    public ShopCommand(TradepostPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        ShopServices services = plugin.services();
        Messages messages = services.messages();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (deny(sender, PERM_RELOAD, messages)) {
                    return true;
                }
                List<String> warnings = plugin.reloadShop();
                plugin.services().messages().send(sender, "reload.done",
                        Placeholder.unparsed("items", String.valueOf(plugin.services().market().catalog().items().size())),
                        Placeholder.unparsed("warnings", String.valueOf(warnings.size())));
            }
            case "price" -> {
                if (deny(sender, PERM_PRICE, messages)) {
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "usage.price");
                    return true;
                }
                price(sender, services, args[1].toLowerCase(Locale.ROOT));
            }
            case "history" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "players-only");
                    return true;
                }
                if (deny(sender, PERM_HISTORY, messages)) {
                    return true;
                }
                history(player, services);
            }
            default -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "players-only");
                    return true;
                }
                if (deny(sender, PERM_USE, messages)) {
                    return true;
                }
                if (!services.market().isReady()) {
                    messages.send(sender, "loading");
                    return true;
                }
                if (sub.isEmpty()) {
                    new MainMenu(services, player).open(player);
                    return true;
                }
                Optional<Category> category = services.market().catalog().category(sub);
                if (category.isEmpty()) {
                    messages.send(sender, "unknown-category", Placeholder.unparsed("input", args[0]));
                    return true;
                }
                new CategoryMenu(services, category.get(), 0, player).open(player);
            }
        }
        return true;
    }

    private static boolean deny(CommandSender sender, String permission, Messages messages) {
        if (sender.hasPermission(permission)) {
            return false;
        }
        messages.send(sender, "no-permission");
        return true;
    }

    private static void price(CommandSender sender, ShopServices services, String id) {
        Market market = services.market();
        Optional<ShopItem> found = market.catalog().item(id);
        if (found.isEmpty()) {
            services.messages().send(sender, "unknown-item", Placeholder.unparsed("input", id));
            return;
        }
        ShopItem item = found.get();
        ShopEconomy economy = services.economy().get();
        services.messages().send(sender, "price-info",
                Placeholder.unparsed("item", item.id()),
                Placeholder.unparsed("buy", item.canBuy() ? economy.format(market.buyPrice(item)) : "-"),
                Placeholder.unparsed("sell", item.canSell() ? economy.format(market.sellPrice(item)) : "-"),
                Placeholder.unparsed("change", String.format(Locale.ROOT, "%+.1f",
                        (market.multiplier(item) - 1.0) * 100.0)));
    }

    private void history(Player player, ShopServices services) {
        UUID id = player.getUniqueId();
        plugin.storage().recentTrades(id, 10).whenComplete((trades, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Messages messages = plugin.services().messages();
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not read trade history for " + id, error);
                        messages.send(player, "history.error");
                        return;
                    }
                    if (trades.isEmpty()) {
                        messages.send(player, "history.empty");
                        return;
                    }
                    messages.send(player, "history.header");
                    ShopEconomy economy = services.economy().get();
                    for (Trade trade : trades) {
                        TagResolver tags = TagResolver.resolver(
                                Placeholder.unparsed("time", TIME.format(Instant.ofEpochMilli(trade.createdAt()))),
                                Placeholder.unparsed("amount", String.valueOf(trade.amount())),
                                Placeholder.unparsed("item", trade.itemId()),
                                Placeholder.unparsed("total", economy.format(trade.total())));
                        messages.send(player, trade.kind() == Trade.Kind.BUY ? "history.bought" : "history.sold", tags);
                    }
                }));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                                      @NotNull String[] args) {
        Market market = plugin.services().market();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(PERM_USE)) {
                market.catalog().categories().forEach(c -> options.add(c.id()));
            }
            if (sender.hasPermission(PERM_RELOAD)) {
                options.add("reload");
            }
            if (sender.hasPermission(PERM_PRICE)) {
                options.add("price");
            }
            if (sender.hasPermission(PERM_HISTORY)) {
                options.add("history");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("price") && sender.hasPermission(PERM_PRICE)) {
            market.catalog().items().forEach(i -> options.add(i.id()));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }
}
