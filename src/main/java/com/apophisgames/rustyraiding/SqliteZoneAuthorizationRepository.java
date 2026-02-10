package com.apophisgames.rustyraiding;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite implementation of IZoneRepository.
 */
public class SqliteZoneAuthorizationRepository implements IAuthRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path dataDirectory;
    private Connection connection;

    public SqliteZoneAuthorizationRepository(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public void initialize() throws Exception {
        Files.createDirectories(dataDirectory);
        openDatabase();
        createSchema();
    }

    private void openDatabase() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        try {
            Class.forName("org.sqlite.JDBC");
            Path dbPath = dataDirectory.resolve("zoneauths.db");
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString();
            connection = DriverManager.getConnection(url);
            LOGGER.atInfo().log("Connected to zone authorizations database: " + dbPath);
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            openDatabase();
        }
        return connection;
    }

    private void createSchema() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS zoneauths (
                id TEXT PRIMARY KEY,
                zone_id TEXT NOT NULL,
                player_id TEXT NOT NULL,
                
                UNIQUE(zone_id, player_id)
            )
            """;

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
            // Index for faster lookups by zone id
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_zone_auths ON zoneauths(zone_id)");
        }
    }

    @Override
    public Map<String, List<String>> loadAll() throws Exception {
        Map<String, List<String>> result = new HashMap<>();
        String sql = "SELECT * FROM zoneauths";

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                ZoneAuthorization auth = mapToZoneAuthorization(rs);
                result.computeIfAbsent(auth.zoneId(), k -> new ArrayList<>()).add(auth.playerId());
            }
        }
        return result;
    }

    @Override
    public List<String> findByZone(String zoneId) throws Exception {
        String sql = "SELECT * FROM zoneauths WHERE zone_id = ?";
        List<String> result = new ArrayList<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, zoneId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapToZoneAuthorization(rs).playerId());
                }
            }
        }
        return result;
    }

    @Override
    public void save(ZoneAuthorization zoneAuthorization) throws Exception {
        // Upsert logic (Insert or Replace)
        String sql = """
            INSERT INTO zoneauths (id, zone_id, player_id)
            VALUES (?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                zone_id=excluded.zone_id,
                player_id=excluded.player_id
            """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, zoneAuthorization.internalId());
            stmt.setString(2, zoneAuthorization.zoneId());
            stmt.setString(3, zoneAuthorization.playerId());

            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(String zoneId) throws Exception {
        String sql = "DELETE FROM zoneauths WHERE zone_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, zoneId);
            stmt.executeUpdate();
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.atWarning().log("Error closing zoneauths db: " + e.getMessage());
            }
        }
    }

    private ZoneAuthorization mapToZoneAuthorization(ResultSet rs) throws SQLException {
        return new ZoneAuthorization(
                rs.getString("id"),
                rs.getString("zone_id"),
                rs.getString("player_id")
        );
    }
}
