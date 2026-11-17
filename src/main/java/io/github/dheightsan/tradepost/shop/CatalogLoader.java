package io.github.dheightsan.tradepost.shop;

import io.github.dheightsan.tradepost.pricing.PriceSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class CatalogLoader {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final long MINUTE = 60_000L;

    public record Result(ShopCatalog catalog, List<String> warnings) {
    }

    private CatalogLoader() {
    }

    public static Result load(ConfigurationSection root) {
        List<String> warnings = new ArrayList<>();
        PriceSettings defaults = readPricing(root.getConfigurationSection("pricing"), null, "pricing", warnings);

        int rows = root.getInt("menu.rows", 3);
        if (rows < 1 || rows > 6) {
            warnings.add("menu.rows must be between 1 and 6, using 3");
            rows = 3;
        }
        String title = root.getString("menu.title", "<dark_gray>Shop");

        List<Category> categories = new ArrayList<>();
        Set<String> seenItems = new HashSet<>();
        Set<Integer> usedSlots = new HashSet<>();
        ConfigurationSection cats = root.getConfigurationSection("categories");
        if (cats == null) {
            warnings.add("No 'categories' section found; the shop is empty");
        } else {
            for (String catId : cats.getKeys(false)) {
                ConfigurationSection cs = cats.getConfigurationSection(catId);
                if (cs == null || !validId(catId, "categories." + catId, warnings)) {
                    continue;
                }
                Material icon = material(cs.getString("icon", "CHEST"), "categories." + catId + ".icon", warnings);
                if (icon == null) {
                    icon = Material.CHEST;
                }
                int slot = cs.getInt("slot", -1);
                if (slot < 0 || slot >= rows * 9 || usedSlots.contains(slot)) {
                    if (cs.contains("slot")) {
                        warnings.add("categories." + catId + ".slot " + slot + " is outside the menu or taken; placing it automatically");
                    }
                    slot = firstFree(usedSlots, rows * 9);
                    if (slot < 0) {
                        warnings.add("No room left in the main menu for category '" + catId + "'; raise menu.rows");
                        continue;
                    }
                }
                usedSlots.add(slot);

                List<ShopItem> items = new ArrayList<>();
                ConfigurationSection is = cs.getConfigurationSection("items");
                if (is != null) {
                    for (String itemId : is.getKeys(false)) {
                        String path = "categories." + catId + ".items." + itemId;
                        ShopItem item = readItem(is.getConfigurationSection(itemId), itemId, catId, path, defaults,
                                warnings);
                        if (item == null) {
                            continue;
                        }
                        if (!seenItems.add(itemId)) {
                            warnings.add(path + ": item id '" + itemId + "' is used twice; ids must be unique across categories");
                            continue;
                        }
                        items.add(item);
                    }
                }
                categories.add(new Category(catId, cs.getString("title", catId), icon, slot, items));
            }
        }
        return new Result(new ShopCatalog(title, rows, categories), warnings);
    }

    private static ShopItem readItem(ConfigurationSection s, String id, String catId, String path,
                                     PriceSettings defaults, List<String> warnings) {
        if (s == null || !validId(id, path, warnings)) {
            return null;
        }
        Material material = material(s.getString("material", id), path + ".material", warnings);
        if (material == null) {
            return null;
        }
        double buy = s.getDouble("buy", -1);
        double sell = s.getDouble("sell", -1);
        if (buy < 0 && sell < 0) {
            warnings.add(path + ": needs a 'buy' or 'sell' price (or both)");
            return null;
        }
        PriceSettings pricing = defaults;
        if (!s.getBoolean("dynamic", true)) {
            pricing = PriceSettings.STATIC;
        } else if (s.isConfigurationSection("pricing")) {
            pricing = readPricing(s.getConfigurationSection("pricing"), defaults, path + ".pricing", warnings);
        }
        if (buy >= 0 && sell >= 0 && sell * (1 + pricing.step()) >= buy) {
            warnings.add(path + ": sell price is too close to the buy price; players can profit by buying and "
                    + "selling back in a loop");
        }
        return new ShopItem(id, catId, material, s.getString("name"), buy, sell, pricing);
    }

    private static PriceSettings readPricing(ConfigurationSection s, PriceSettings fallback, String path,
                                             List<String> warnings) {
        PriceSettings base = fallback != null ? fallback
                : new PriceSettings(0.005, 0.5, 2.0, 30 * MINUTE);
        if (s == null) {
            return base;
        }
        if (!s.getBoolean("enabled", true)) {
            return PriceSettings.STATIC;
        }
        double step = s.getDouble("step", base.step());
        double min = s.getDouble("min-multiplier", base.minMultiplier());
        double max = s.getDouble("max-multiplier", base.maxMultiplier());
        long halfLife = s.contains("recovery-half-life-minutes")
                ? Math.round(s.getDouble("recovery-half-life-minutes") * MINUTE)
                : base.halfLifeMillis();
        try {
            return new PriceSettings(step, min, max, halfLife);
        } catch (IllegalArgumentException e) {
            warnings.add(path + ": " + e.getMessage() + "; using the defaults instead");
            return base;
        }
    }

    private static boolean validId(String id, String path, List<String> warnings) {
        if (ID.matcher(id).matches()) {
            return true;
        }
        warnings.add(path + ": ids may only use lowercase letters, digits and '_'");
        return false;
    }

    private static Material material(String name, String path, List<String> warnings) {
        Material material = name == null ? null : Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (material == null || material.isAir() || !material.isItem()) {
            warnings.add(path + ": unknown item material '" + name + "'");
            return null;
        }
        return material;
    }

    private static int firstFree(Set<Integer> used, int size) {
        for (int i = 0; i < size; i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        return -1;
    }
}
