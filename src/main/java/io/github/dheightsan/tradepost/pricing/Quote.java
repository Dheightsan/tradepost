package io.github.dheightsan.tradepost.pricing;

public record Quote(int amount, double total, PriceState after) {
    public double unitAverage() {
        return amount == 0 ? 0.0 : total / amount;
    }
}
