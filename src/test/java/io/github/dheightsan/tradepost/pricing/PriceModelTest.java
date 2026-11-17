package io.github.dheightsan.tradepost.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceModelTest {
    private static final long HOUR = 3_600_000L;
    private static final PriceSettings SETTINGS = new PriceSettings(0.01, 0.5, 2.0, HOUR);
    private static final long NOW = 1_000_000_000L;

    @Test
    void neutralStateUsesBasePrice() {
        assertEquals(10.0, PriceModel.unitPrice(10.0, SETTINGS, PriceState.neutral(NOW), NOW));
    }

    @Test
    void buyingRaisesThePriceOneStepPerUnit() {
        Quote quote = PriceModel.quoteBuy(10.0, SETTINGS, PriceState.neutral(NOW), 3, NOW);
        assertEquals(30.30, quote.total(), 1e-9);
        assertEquals(Math.pow(1.01, 3), quote.after().multiplier(), 1e-12);
        assertEquals(NOW, quote.after().updatedAt());
    }

    @Test
    void sellingLowersThePriceOneStepPerUnit() {
        Quote quote = PriceModel.quoteSell(10.0, SETTINGS, PriceState.neutral(NOW), 2, NOW);
        assertEquals(19.90, quote.total(), 1e-9);
        assertEquals(0.99 * 0.99, quote.after().multiplier(), 1e-12);
    }

    @Test
    void oneBigTradeCostsTheSameAsManySmallOnes() {
        Quote bulk = PriceModel.quoteBuy(7.5, SETTINGS, PriceState.neutral(NOW), 64, NOW);

        PriceState state = PriceState.neutral(NOW);
        double sum = 0;
        for (int i = 0; i < 64; i++) {
            Quote single = PriceModel.quoteBuy(7.5, SETTINGS, state, 1, NOW);
            sum += 7.5 * PriceModel.multiplierAt(SETTINGS, state, NOW);
            state = single.after();
        }
        assertEquals(PriceModel.roundMoney(sum), bulk.total(), 1e-9);
        assertEquals(state.multiplier(), bulk.after().multiplier(), 1e-12);
    }

    @Test
    void priceNeverLeavesTheConfiguredRange() {
        Quote up = PriceModel.quoteBuy(10.0, SETTINGS, PriceState.neutral(NOW), 10_000, NOW);
        assertEquals(2.0, up.after().multiplier());

        Quote down = PriceModel.quoteSell(10.0, SETTINGS, PriceState.neutral(NOW), 10_000, NOW);
        assertEquals(0.5, down.after().multiplier());
    }

    @Test
    void cappedUnitsAreChargedAtTheCap() {
        PriceState atCap = new PriceState(2.0, NOW);
        Quote quote = PriceModel.quoteBuy(10.0, SETTINGS, atCap, 5, NOW);
        assertEquals(100.0, quote.total(), 1e-9);
    }

    @Test
    void halfTheDistanceRecoversAfterOneHalfLife() {
        PriceState pumped = new PriceState(1.8, NOW);
        assertEquals(1.4, PriceModel.multiplierAt(SETTINGS, pumped, NOW + HOUR), 1e-9);
        assertEquals(1.2, PriceModel.multiplierAt(SETTINGS, pumped, NOW + 2 * HOUR), 1e-9);

        PriceState dumped = new PriceState(0.6, NOW);
        assertEquals(0.8, PriceModel.multiplierAt(SETTINGS, dumped, NOW + HOUR), 1e-9);
    }

    @Test
    void recoverySnapsToBaseOnceTheDifferenceIsInvisible() {
        PriceState pumped = new PriceState(1.5, NOW);
        assertEquals(1.0, PriceModel.multiplierAt(SETTINGS, pumped, NOW + 40 * HOUR));
    }

    @Test
    void clockGoingBackwardsDoesNotMoveThePrice() {
        PriceState pumped = new PriceState(1.5, NOW);
        assertEquals(1.5, PriceModel.multiplierAt(SETTINGS, pumped, NOW - HOUR));
        Quote quote = PriceModel.quoteBuy(10, SETTINGS, pumped, 1, NOW - HOUR);
        assertEquals(NOW, quote.after().updatedAt(), "timestamp must never go backwards");
    }

    @Test
    void tradesStartFromTheRecoveredPrice() {
        PriceState old = new PriceState(2.0, NOW);
        Quote quote = PriceModel.quoteBuy(10.0, SETTINGS, old, 1, NOW + HOUR);
        assertEquals(15.0, quote.total(), 1e-9);
        assertEquals(NOW + HOUR, quote.after().updatedAt());
    }

    @Test
    void staticSettingsNeverMove() {
        Quote quote = PriceModel.quoteBuy(4.0, PriceSettings.STATIC, PriceState.neutral(NOW), 100, NOW);
        assertEquals(400.0, quote.total(), 1e-9);
        assertEquals(1.0, quote.after().multiplier());
    }

    @Test
    void noRecoveryWhenHalfLifeIsZero() {
        PriceSettings sticky = new PriceSettings(0.01, 0.5, 2.0, 0);
        assertEquals(1.7, PriceModel.multiplierAt(sticky, new PriceState(1.7, NOW), NOW + 100 * HOUR));
    }

    @Test
    void storedMultiplierOutsideNewLimitsIsClamped() {
        PriceSettings tight = new PriceSettings(0.01, 0.9, 1.1, 0);
        assertEquals(1.1, PriceModel.multiplierAt(tight, new PriceState(1.9, NOW), NOW));
    }

    @Test
    void totalsAreRoundedToCents() {
        PriceSettings flat = PriceSettings.STATIC;
        Quote quote = PriceModel.quoteBuy(0.333, flat, PriceState.neutral(NOW), 3, NOW);
        assertEquals(1.0, quote.total());
    }

    @Test
    void sellingNeverPaysMoreThanBuyingCostsWhenSpreadIsSane() {
        double buyBase = 10.0;
        double sellBase = 3.0;
        Quote bought = PriceModel.quoteBuy(buyBase, SETTINGS, PriceState.neutral(NOW), 64, NOW);
        Quote sold = PriceModel.quoteSell(sellBase, SETTINGS, bought.after(), 64, NOW);
        assertTrue(sold.total() < bought.total());
    }

    @Test
    void zeroAmountIsFreeAndChangesNothing() {
        Quote quote = PriceModel.quoteBuy(10.0, SETTINGS, PriceState.neutral(NOW), 0, NOW);
        assertEquals(0.0, quote.total());
        assertEquals(1.0, quote.after().multiplier());
        assertEquals(0.0, quote.unitAverage());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> PriceModel.quoteBuy(10.0, SETTINGS, PriceState.neutral(NOW), -1, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> PriceModel.quoteBuy(-1.0, SETTINGS, PriceState.neutral(NOW), 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> new PriceSettings(1.0, 0.5, 2.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PriceSettings(0.01, 0.0, 2.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PriceSettings(0.01, 1.2, 2.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PriceSettings(0.01, 0.5, 0.9, 0));
        assertThrows(IllegalArgumentException.class, () -> new PriceSettings(0.01, 0.5, 2.0, -5));
        assertThrows(IllegalArgumentException.class, () -> new PriceState(0.0, NOW));
        assertThrows(IllegalArgumentException.class, () -> new PriceState(Double.NaN, NOW));
    }
}
