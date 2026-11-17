package io.github.dheightsan.tradepost.storage;

public enum SqlDialect {
    SQLITE {
        @Override
        String createPrices(String table) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "item_id VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "multiplier DOUBLE NOT NULL, "
                    + "updated_at BIGINT NOT NULL)";
        }

        @Override
        String createTrades(String table) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "player_uuid CHAR(36) NOT NULL, "
                    + "item_id VARCHAR(64) NOT NULL, "
                    + "kind VARCHAR(4) NOT NULL, "
                    + "amount INTEGER NOT NULL, "
                    + "total DOUBLE NOT NULL, "
                    + "created_at BIGINT NOT NULL)";
        }

        @Override
        String createTradesIndex(String table) {
            return "CREATE INDEX IF NOT EXISTS " + table + "_player ON " + table + " (player_uuid, created_at)";
        }

        @Override
        String upsertPrice(String table) {
            return "INSERT INTO " + table + " (item_id, multiplier, updated_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT(item_id) DO UPDATE SET "
                    + "multiplier = excluded.multiplier, updated_at = excluded.updated_at";
        }
    },

    MYSQL {
        @Override
        String createPrices(String table) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "item_id VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "multiplier DOUBLE NOT NULL, "
                    + "updated_at BIGINT NOT NULL)";
        }

        @Override
        String createTrades(String table) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "player_uuid CHAR(36) NOT NULL, "
                    + "item_id VARCHAR(64) NOT NULL, "
                    + "kind VARCHAR(4) NOT NULL, "
                    + "amount INT NOT NULL, "
                    + "total DOUBLE NOT NULL, "
                    + "created_at BIGINT NOT NULL, "
                    + "INDEX " + table + "_player (player_uuid, created_at))";
        }

        @Override
        String createTradesIndex(String table) {
            return null;
        }

        @Override
        String upsertPrice(String table) {
            return "INSERT INTO " + table + " (item_id, multiplier, updated_at) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "multiplier = VALUES(multiplier), updated_at = VALUES(updated_at)";
        }
    };

    abstract String createPrices(String table);

    abstract String createTrades(String table);

    abstract String createTradesIndex(String table);

    abstract String upsertPrice(String table);
}
