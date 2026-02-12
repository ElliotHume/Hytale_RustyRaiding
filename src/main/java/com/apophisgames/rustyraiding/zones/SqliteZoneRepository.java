package com.apophisgames.rustyraiding.zones;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite implementation of IZoneRepository.
 */
public class SqliteZoneRepository implements IZoneRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path dataDirectory;
    private Connection connection;

    public SqliteZoneRepository(@Nonnull Path dataDirectory) {
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
            Path dbPath = dataDirectory.resolve("zones.db");
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString();
            connection = DriverManager.getConnection(url);
            LOGGER.atInfo().log("Connected to zones database: " + dbPath);
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
            CREATE TABLE IF NOT EXISTS zones (
                id TEXT PRIMARY KEY,
                zone_name TEXT NOT NULL,
                world_name TEXT NOT NULL,
                min_x DOUBLE NOT NULL,
                min_y DOUBLE NOT NULL,
                min_z DOUBLE NOT NULL,
                max_x DOUBLE NOT NULL,
                max_y DOUBLE NOT NULL,
                max_z DOUBLE NOT NULL,
                UNIQUE(zone_name, world_name)
            )
            """;

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
            // Index for faster lookups by world
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_zones_world ON zones(world_name)");
        }
    }

    @Override
    public Map<String, List<Zone>> loadAll() throws Exception {
        Map<String, List<Zone>> result = new HashMap<>();
        String sql = "SELECT * FROM zones";

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Zone zone = mapToZone(rs);
                result.computeIfAbsent(zone.worldName(), k -> new ArrayList<>()).add(zone);
            }
        }
        return result;
    }

    @Override
    public List<Zone> findByWorld(String worldName) throws Exception {
        String sql = "SELECT * FROM zones WHERE world_name = ?";
        List<Zone> result = new ArrayList<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, worldName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapToZone(rs));
                }
            }
        }
        return result;
    }

    @Override
    public Optional<Zone> findByName(String worldName, String zoneName) throws Exception {
        String sql = "SELECT * FROM zones WHERE world_name = ? AND zone_name = ?";
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, worldName);
            stmt.setString(2, zoneName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapToZone(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void save(Zone zone) throws Exception {
        // Upsert logic (Insert or Replace)
        String sql = """
            INSERT INTO zones (id, zone_name, world_name, min_x, min_y, min_z, max_x, max_y, max_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                zone_name=excluded.zone_name,
                world_name=excluded.world_name,
                min_x=excluded.min_x,
                min_y=excluded.min_y,
                min_z=excluded.min_z,
                max_x=excluded.max_x,
                max_y=excluded.max_y,
                max_z=excluded.max_z
            """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, zone.internalId());
            stmt.setString(2, zone.zoneName());
            stmt.setString(3, zone.worldName());
            stmt.setDouble(4, zone.min().x);
            stmt.setDouble(5, zone.min().y);
            stmt.setDouble(6, zone.min().z);
            stmt.setDouble(7, zone.max().x);
            stmt.setDouble(8, zone.max().y);
            stmt.setDouble(9, zone.max().z);

            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(String zoneId) throws Exception {
        String sql = "DELETE FROM zones WHERE id = ?";
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
                LOGGER.atWarning().log("Error closing zones db: " + e.getMessage());
            }
        }
    }

    private Zone mapToZone(ResultSet rs) throws SQLException {
        return new Zone(
            rs.getString("id"),
            rs.getString("zone_name"),
            rs.getString("world_name"),
            new Vector3d(rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z")),
            new Vector3d(rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"))
        );
    }
}
