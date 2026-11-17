package io.github.dheightsan.tradepost.shop;

import io.github.dheightsan.tradepost.pricing.PriceSettings;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogLoaderTest {
    @BeforeAll
    static void start() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stop() {
        MockBukkit.unmock();
    }

    private static CatalogLoader.Result load(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return CatalogLoader.load(config);
    }

    @Test
    void bundledConfigLoadsWithoutWarnings() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        CatalogLoader.Result result = CatalogLoader.load(config);

        assertEquals(List.of(), result.warnings());
        assertEquals(4, result.catalog().categories().size());
        ShopItem diamond = result.catalog().item("diamond").orElseThrow();
        assertEquals(new PriceSettings(0.01, 0.6, 3.0, 20 * 60_000L), diamond.pricing());
        assertEquals(PriceSettings.STATIC, result.catalog().item("name_tag").orElseThrow().pricing());
        assertFalse(result.catalog().item("bone").orElseThrow().canBuy());
        assertFalse(result.catalog().item("bread").orElseThrow().canSell());
    }

    @Test
    void materialDefaultsToTheItemId() throws Exception {
        CatalogLoader.Result result = load("""
                categories:
                  misc:
                    items:
                      oak_planks: { buy: 1 }
                """);
        assertEquals(Material.OAK_PLANKS, result.catalog().item("oak_planks").orElseThrow().material());
    }

    @Test
    void brokenEntriesAreSkippedWithAWarning() throws Exception {
        CatalogLoader.Result result = load("""
                categories:
                  misc:
                    items:
                      good: { material: STONE, buy: 1 }
                      typo: { material: STOEN, buy: 1 }
                      nothing: { material: DIRT }
                      Bad-Id: { material: DIRT, buy: 1 }
                      air: { material: AIR, buy: 1 }
                """);
        assertEquals(List.of("good"), result.catalog().items().stream().map(ShopItem::id).toList());
        assertEquals(4, result.warnings().size(), result.warnings().toString());
    }

    @Test
    void duplicateIdsAcrossCategoriesAreRejected() throws Exception {
        CatalogLoader.Result result = load("""
                categories:
                  one:
                    items:
                      coal: { buy: 1 }
                  two:
                    items:
                      coal: { buy: 2 }
                """);
        assertEquals(1, result.catalog().items().size());
        assertEquals("one", result.catalog().item("coal").orElseThrow().categoryId());
        assertTrue(result.warnings().getFirst().contains("used twice"));
    }

    @Test
    void warnsAboutBuySellLoops() throws Exception {
        CatalogLoader.Result result = load("""
                pricing: { step: 0.01 }
                categories:
                  misc:
                    items:
                      dirt: { buy: 1.0, sell: 1.0 }
                """);
        assertEquals(1, result.catalog().items().size(), "still loaded, it is only a warning");
        assertTrue(result.warnings().getFirst().contains("too close"));
    }

    @Test
    void invalidPricingFallsBackToDefaults() throws Exception {
        CatalogLoader.Result result = load("""
                pricing: { step: 0.02, min-multiplier: 0.8, max-multiplier: 1.5, recovery-half-life-minutes: 10 }
                categories:
                  misc:
                    items:
                      dirt:
                        buy: 1
                        pricing: { max-multiplier: 0.5 }
                """);
        assertEquals(new PriceSettings(0.02, 0.8, 1.5, 600_000L),
                result.catalog().item("dirt").orElseThrow().pricing());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void globalPricingCanBeTurnedOff() throws Exception {
        CatalogLoader.Result result = load("""
                pricing: { enabled: false }
                categories:
                  misc:
                    items:
                      dirt: { buy: 1 }
                """);
        assertEquals(PriceSettings.STATIC, result.catalog().item("dirt").orElseThrow().pricing());
    }

    @Test
    void categorySlotsAreKeptInsideTheMenu() throws Exception {
        CatalogLoader.Result result = load("""
                menu: { rows: 1 }
                categories:
                  a: { slot: 4 }
                  b: { slot: 4 }
                  c: { slot: 99 }
                """);
        List<Integer> slots = result.catalog().categories().stream().map(Category::slot).toList();
        assertEquals(List.of(4, 0, 1), slots);
        assertEquals(2, result.warnings().size());
    }

    @Test
    void invalidRowCountIsReplaced() throws Exception {
        CatalogLoader.Result result = load("menu: { rows: 9 }");
        assertEquals(3, result.catalog().menuRows());
    }
}
