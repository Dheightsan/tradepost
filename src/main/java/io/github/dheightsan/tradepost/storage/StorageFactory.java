package io.github.dheightsan.tradepost.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

public final class StorageFactory {
    private StorageFactory() {
    }

    public static ShopStorage create(ConfigurationSection section, File dataFolder, Logger logger) {
        String type = section == null ? "sqlite" : section.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        String prefix = section == null ? "tradepost_" : section.getString("table-prefix", "tradepost_");
        return switch (type) {
            case "sqlite" -> sqlite(new File(dataFolder,
                    section == null ? "shop.db" : section.getString("sqlite.file", "shop.db")), prefix, logger);
            case "mysql", "mariadb" -> mysql(section.getConfigurationSection("mysql"), prefix, logger);
            default -> throw new IllegalArgumentException("Unknown storage type '" + type + "' (use sqlite or mysql)");
        };
    }

    public static ShopStorage sqlite(File file, String prefix, Logger logger) {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create " + parent);
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("Tradepost-SQLite");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        config.setMaximumPoolSize(1);
        lazyStart(config);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        return new SqlShopStorage(new HikariDataSource(config), SqlDialect.SQLITE, prefix, logger);
    }

    public static ShopStorage mysql(ConfigurationSection section, String prefix, Logger logger) {
        if (section == null) {
            throw new IllegalArgumentException("storage.type is mysql but the storage.mysql section is missing");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("Tradepost-MySQL");
        lazyStart(config);
        String driver = firstAvailable("com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver", "com.mysql.jdbc.Driver");
        if (driver != null) {
            config.setDriverClassName(driver);
        }
        config.setJdbcUrl("jdbc:mysql://" + section.getString("host", "localhost") + ":"
                + section.getInt("port", 3306) + "/" + section.getString("database", "minecraft"));
        config.setUsername(section.getString("username", "root"));
        config.setPassword(section.getString("password", ""));
        config.setMaximumPoolSize(Math.max(1, section.getInt("pool-size", 4)));
        config.setConnectionTimeout(section.getLong("connection-timeout-ms", 10_000L));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "64");
        ConfigurationSection props = section.getConfigurationSection("properties");
        if (props != null) {
            for (String key : props.getKeys(false)) {
                config.addDataSourceProperty(key, String.valueOf(props.get(key)));
            }
        }
        return new SqlShopStorage(new HikariDataSource(config), SqlDialect.MYSQL, prefix, logger);
    }

    // connect on the first query, not on enable
    private static void lazyStart(HikariConfig config) {
        config.setInitializationFailTimeout(-1);
    }

    private static String firstAvailable(String... classNames) {
        for (String name : classNames) {
            try {
                Class.forName(name, false, StorageFactory.class.getClassLoader());
                return name;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }
}
