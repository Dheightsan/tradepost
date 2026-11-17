package io.github.dheightsan.tradepost.pricing;

public record PriceSettings(double step, double minMultiplier, double maxMultiplier, long halfLifeMillis) {
    public static final PriceSettings STATIC = new PriceSettings(0.0, 1.0, 1.0, 0L);

    public PriceSettings {
        if (!(step >= 0.0 && step < 1.0)) {
            throw new IllegalArgumentException("step must be in [0, 1), got " + step);
        }
        if (!(minMultiplier > 0.0)) {
            throw new IllegalArgumentException("min-multiplier must be > 0, got " + minMultiplier);
        }
        if (!(maxMultiplier >= 1.0) || minMultiplier > 1.0) {
            throw new IllegalArgumentException(
                    "multipliers must satisfy min <= 1 <= max, got min=" + minMultiplier + " max=" + maxMultiplier);
        }
        if (halfLifeMillis < 0) {
            throw new IllegalArgumentException("half-life must not be negative, got " + halfLifeMillis);
        }
    }

    public boolean isDynamic() {
        return step > 0.0;
    }

    public double clamp(double multiplier) {
        return Math.max(minMultiplier, Math.min(maxMultiplier, multiplier));
    }
}
