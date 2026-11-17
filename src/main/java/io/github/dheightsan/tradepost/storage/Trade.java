package io.github.dheightsan.tradepost.storage;

import java.util.Objects;
import java.util.UUID;

public record Trade(UUID player, String itemId, Kind kind, int amount, double total, long createdAt) {
    public enum Kind {
        BUY, SELL
    }

    public Trade {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(kind, "kind");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
    }
}
