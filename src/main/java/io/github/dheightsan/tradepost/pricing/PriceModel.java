package io.github.dheightsan.tradepost.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceModel {
    private PriceModel() {
    }

    public static double multiplierAt(PriceSettings settings, PriceState state, long now) {
        double m = state.multiplier();
        long elapsed = now - state.updatedAt();
        if (settings.halfLifeMillis() <= 0 || elapsed <= 0 || m == 1.0) {
            return settings.clamp(m);
        }
        double remaining = Math.pow(0.5, (double) elapsed / settings.halfLifeMillis());
        double recovered = 1.0 + (m - 1.0) * remaining;
        if (Math.abs(recovered - 1.0) < 1e-6) {
            recovered = 1.0;
        }
        return settings.clamp(recovered);
    }

    public static PriceState settle(PriceSettings settings, PriceState state, long now) {
        return new PriceState(multiplierAt(settings, state, now), Math.max(now, state.updatedAt()));
    }

    public static double unitPrice(double basePrice, PriceSettings settings, PriceState state, long now) {
        return roundMoney(basePrice * multiplierAt(settings, state, now));
    }

    public static Quote quoteBuy(double basePrice, PriceSettings settings, PriceState state, int amount, long now) {
        return quote(basePrice, settings, state, amount, now, 1.0 + settings.step());
    }

    public static Quote quoteSell(double basePrice, PriceSettings settings, PriceState state, int amount, long now) {
        return quote(basePrice, settings, state, amount, now, 1.0 - settings.step());
    }

    private static Quote quote(double basePrice, PriceSettings settings, PriceState state, int amount, long now,
                               double factor) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative, got " + amount);
        }
        if (basePrice < 0) {
            throw new IllegalArgumentException("base price must not be negative, got " + basePrice);
        }
        double m = multiplierAt(settings, state, now);
        double total = 0.0;
        for (int i = 0; i < amount; i++) {
            total += basePrice * m;
            m = settings.clamp(m * factor);
        }
        long stamp = Math.max(now, state.updatedAt());
        return new Quote(amount, roundMoney(total), new PriceState(m, stamp));
    }

    public static double roundMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
