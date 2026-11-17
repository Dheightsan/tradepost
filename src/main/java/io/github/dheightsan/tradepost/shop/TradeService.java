package io.github.dheightsan.tradepost.shop;

import io.github.dheightsan.tradepost.economy.ShopEconomy;
import io.github.dheightsan.tradepost.pricing.Quote;
import io.github.dheightsan.tradepost.storage.Trade;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class TradeService {
    public enum Outcome {
        SUCCESS,
        NOT_READY,
        ECONOMY_DISABLED,
        NOT_BUYABLE,
        NOT_SELLABLE,
        INVENTORY_FULL,
        NOT_ENOUGH_MONEY,
        NOTHING_TO_SELL,
        ECONOMY_ERROR
    }

    public record Result(Outcome outcome, int amount, double total) {
        static Result of(Outcome outcome) {
            return new Result(outcome, 0, 0);
        }

        public boolean success() {
            return outcome == Outcome.SUCCESS;
        }
    }

    private final Market market;
    private final Supplier<ShopEconomy> economy;

    public TradeService(Market market, Supplier<ShopEconomy> economy) {
        this.market = Objects.requireNonNull(market, "market");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    public Result buy(Player player, ShopItem item, int requested) {
        ShopEconomy eco = economy.get();
        Result blocked = precheck(eco);
        if (blocked != null) {
            return blocked;
        }
        if (!item.canBuy()) {
            return Result.of(Outcome.NOT_BUYABLE);
        }
        int amount = Math.min(requested, freeSpace(player.getInventory(), item));
        if (amount <= 0) {
            return Result.of(Outcome.INVENTORY_FULL);
        }
        Quote quote = market.quoteBuy(item, amount);
        if (eco.balance(player) < quote.total()) {
            return new Result(Outcome.NOT_ENOUGH_MONEY, amount, quote.total());
        }
        if (!eco.withdraw(player, quote.total())) {
            return new Result(Outcome.ECONOMY_ERROR, amount, quote.total());
        }
        give(player, item, amount);
        market.commit(player.getUniqueId(), item, Trade.Kind.BUY, quote);
        return new Result(Outcome.SUCCESS, amount, quote.total());
    }

    public Result sell(Player player, ShopItem item, int requested) {
        ShopEconomy eco = economy.get();
        Result blocked = precheck(eco);
        if (blocked != null) {
            return blocked;
        }
        if (!item.canSell()) {
            return Result.of(Outcome.NOT_SELLABLE);
        }
        int amount = Math.min(requested, countSellable(player.getInventory(), item));
        if (amount <= 0) {
            return Result.of(Outcome.NOTHING_TO_SELL);
        }
        Quote quote = market.quoteSell(item, amount);
        take(player.getInventory(), item, amount);
        if (!eco.deposit(player, quote.total())) {
            give(player, item, amount);
            return new Result(Outcome.ECONOMY_ERROR, amount, quote.total());
        }
        market.commit(player.getUniqueId(), item, Trade.Kind.SELL, quote);
        return new Result(Outcome.SUCCESS, amount, quote.total());
    }

    private Result precheck(ShopEconomy eco) {
        if (!market.isReady()) {
            return Result.of(Outcome.NOT_READY);
        }
        if (!eco.isEnabled()) {
            return Result.of(Outcome.ECONOMY_DISABLED);
        }
        return null;
    }

    static ItemStack template(ShopItem item) {
        return new ItemStack(item.material());
    }

    static int freeSpace(PlayerInventory inventory, ShopItem item) {
        ItemStack template = template(item);
        int max = template.getMaxStackSize();
        int space = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                space += max;
            } else if (stack.isSimilar(template)) {
                space += Math.max(0, max - stack.getAmount());
            }
        }
        return space;
    }

    static int countSellable(PlayerInventory inventory, ShopItem item) {
        ItemStack template = template(item);
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(template)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static void take(PlayerInventory inventory, ShopItem item, int amount) {
        ItemStack template = template(item);
        ItemStack[] contents = inventory.getStorageContents();
        int left = amount;
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !stack.isSimilar(template)) {
                continue;
            }
            int removed = Math.min(left, stack.getAmount());
            left -= removed;
            if (removed == stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - removed);
            }
        }
        inventory.setStorageContents(contents);
    }

    private static void give(Player player, ShopItem item, int amount) {
        ItemStack template = template(item);
        int max = template.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int size = Math.min(left, max);
            left -= size;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(template.asQuantity(size));
            // should not happen, space was checked
            overflow.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }
}
