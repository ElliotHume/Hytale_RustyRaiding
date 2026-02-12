package com.apophisgames.rustyraiding.reinforcedblocks;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * SQLite implementation of IZoneRepository.
 */
public class SqliteReinforcedBlockRepository implements IReinforcedBlockRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path dataDirectory;
    private Connection connection;

    public SqliteReinforcedBlockRepository(@Nonnull Path dataDirectory) {
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
            Path dbPath = dataDirectory.resolve("reinforcedblocks.db");
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString();
            connection = DriverManager.getConnection(url);
            LOGGER.atInfo().log("Connected to reinforced blocks database: " + dbPath);
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
            CREATE TABLE IF NOT EXISTS reinforcedblocks (
                id TEXT PRIMARY KEY,
                world_name TEXT NOT NULL,
                reinforcement INTEGER NOT NULL,
                pos_x INTEGER NOT NULL,
                pos_y INTEGER NOT NULL,
                pos_z INTEGER NOT NULL
            )
            """;

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
            // Index for faster lookups by world name
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reinforced_worlds ON reinforcedblocks(world_name)");
        }
    }

    @Override
    public Map<String, Map<String, ReinforcedBlock>> loadAll() throws Exception {
        Map<String, Map<String, ReinforcedBlock>> result = new HashMap<>();
        String sql = "SELECT * FROM reinforcedblocks";

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                ReinforcedBlock block = mapToReinforcedBlock(rs);
                result.computeIfAbsent(block.worldName(), k -> new HashMap<>()).put(block.internalId(), block);
            }
        }
        return result;
    }

    @Override
    public Map<String, ReinforcedBlock> findByWorld(String worldName) throws Exception {
        String sql = "SELECT * FROM reinforcedblocks WHERE world_name = ?";
        Map<String, ReinforcedBlock> result = new HashMap<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, worldName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ReinforcedBlock block = mapToReinforcedBlock(rs);
                    result.put(block.internalId(), block);
                }
            }
        }
        return result;
    }

    @Override
    public Optional<ReinforcedBlock> findByPosition(String worldName, Vector3i position) throws Exception {
        String sql = "SELECT * FROM reinforcedblocks WHERE world_name = ? AND pos_x = ? AND pos_y = ? AND pos_z = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, worldName);
            stmt.setInt(2, position.x);
            stmt.setInt(3, position.y);
            stmt.setInt(4, position.z);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapToReinforcedBlock(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Map<String, ReinforcedBlock> findInArea(String worldName, Vector3i boundsMin, Vector3i boundsMax) throws Exception {
        String sql = """
            SELECT * FROM reinforcedblocks WHERE world_name = ?
            AND pos_x BETWEEN ? AND ?
            AND pos_y BETWEEN ? AND ?
            AND pos_z BETWEEN ? AND ?
            """;
        Map<String, ReinforcedBlock> result = new HashMap<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, worldName);
            stmt.setInt(2, boundsMin.x);
            stmt.setInt(3, boundsMax.x);
            stmt.setInt(4, boundsMin.y);
            stmt.setInt(5, boundsMax.y);
            stmt.setInt(6, boundsMin.z);
            stmt.setInt(7, boundsMax.z);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ReinforcedBlock block = mapToReinforcedBlock(rs);
                    result.put(block.internalId(), block);
                }
            }
        }
        return result;
    }

    @Override
    public void save(ReinforcedBlock reinforcedBlock) throws Exception {
        // Upsert logic (Insert or Replace)
        String sql = """
            INSERT INTO reinforcedblocks (id, world_name, reinforcement, pos_x, pos_y, pos_z)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                world_name=excluded.world_name,
                reinforcement=excluded.reinforcement,
                pos_x=excluded.pos_x,
                pos_y=excluded.pos_y,
                pos_z=excluded.pos_z
            """;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, reinforcedBlock.internalId());
            stmt.setString(2, reinforcedBlock.worldName());
            stmt.setInt(3, reinforcedBlock.reinforcement());
            stmt.setInt(4, reinforcedBlock.position().x);
            stmt.setInt(5, reinforcedBlock.position().y);
            stmt.setInt(6, reinforcedBlock.position().z);

            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(String internalId) throws Exception {
        String sql = "DELETE FROM reinforcedblocks WHERE id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, internalId);
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(String worldName, Vector3i position) throws Exception {
        String sql = "DELETE FROM reinforcedblocks WHERE world_name = ? AND pos_x = ? AND pos_y = ? AND pos_z = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, worldName);
            stmt.setInt(2, position.x);
            stmt.setInt(3, position.y);
            stmt.setInt(4, position.z);
            stmt.executeUpdate();
        }
    }

    @Override
    public void deleteInArea(String worldName, Vector3i boundsMin, Vector3i boundsMax) throws Exception {
        String sql = """
            DELETE FROM reinforcedblocks WHERE world_name = ?
            AND pos_x BETWEEN ? AND ?
            AND pos_y BETWEEN ? AND ?
            AND pos_z BETWEEN ? AND ?
            """;
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, worldName);
            stmt.setInt(2, boundsMin.x);
            stmt.setInt(3, boundsMax.x);
            stmt.setInt(4, boundsMin.y);
            stmt.setInt(5, boundsMax.y);
            stmt.setInt(6, boundsMin.z);
            stmt.setInt(7, boundsMax.z);
            stmt.executeUpdate();
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.atWarning().log("Error closing reinforcedblocks db: " + e.getMessage());
            }
        }
    }

    private ReinforcedBlock mapToReinforcedBlock(ResultSet rs) throws SQLException {
        return new ReinforcedBlock(
                rs.getString("id"),
                rs.getString("world_name"),
                new Vector3i(rs.getInt("pos_x"), rs.getInt("pos_y"), rs.getInt("pos_z")),
                rs.getInt("reinforcement")
        );
    }
}
