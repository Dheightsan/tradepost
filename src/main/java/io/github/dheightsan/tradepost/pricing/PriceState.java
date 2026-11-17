package io.github.dheightsan.tradepost.pricing;

public record PriceState(double multiplier, long updatedAt) {
    public static PriceState neutral(long now) {
        return new PriceState(1.0, now);
    }

    public PriceState {
        if (!(multiplier > 0.0) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("multiplier must be a positive finite number, got " + multiplier);
        }
    }
}
