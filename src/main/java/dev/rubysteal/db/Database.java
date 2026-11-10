package dev.rubysteal.db;

import dev.rubysteal.RubyStealPlugin;
import dev.rubysteal.model.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite (default) or MySQL storage. Every operation runs on one dedicated
 * thread so the main server thread never blocks on I/O.
 */
public final class Database {
    public record HistoryEntry(long timestamp, String event, String detail) {}

    @FunctionalInterface
    private interface SqlTask<T> {
        T run(Connection c) throws Exception;
    }

    private static final String COLS = "uuid,name,level,xp,kills,deaths,item_serial,created,last_seen";

    private final RubyStealPlugin plugin;
    private final boolean mysql;
    private final String url;
    private final String user;
    private final String pass;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> new Thread(r, "RubySteal-DB"));
    private Connection conn;

    public Database(RubyStealPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("database");
        String type = s == null ? "sqlite" : s.getString("type", "sqlite");
        this.mysql = type.equalsIgnoreCase("mysql");
        if (mysql) {
            String params = s.getString("mysql.parameters", "useSSL=false&autoReconnect=true&characterEncoding=utf8");
            this.url = "jdbc:mysql://" + s.getString("mysql.host", "localhost") + ":" + s.getInt("mysql.port", 3306)
                    + "/" + s.getString("mysql.database", "rubysteal") + "?" + params;
            this.user = s.getString("mysql.username", "root");
            this.pass = s.getString("mysql.password", "");
        } else {
            plugin.getDataFolder().mkdirs();
            String file = s == null ? "rubysteal.db" : s.getString("sqlite.file", "rubysteal.db");
            this.url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), file).getAbsolutePath();
            this.user = null;
            this.pass = null;
        }
    }

    public boolean isMysql() {
        return mysql;
    }

    private Connection c() throws SQLException {
        if (conn == null || conn.isClosed() || (mysql && !conn.isValid(2))) {
            try {
                conn = mysql ? DriverManager.getConnection(url, user, pass) : DriverManager.getConnection(url);
            } catch (SQLException ex) {
                conn = null;
                throw ex;
            }
        }
        return conn;
    }

    private <T> CompletableFuture<T> run(SqlTask<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        try {
            exec.execute(() -> {
                try {
                    f.complete(task.run(c()));
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Database error", t);
                    f.completeExceptionally(t);
                }
            });
        } catch (RuntimeException rejected) {
            f.completeExceptionally(rejected);
        }
        return f;
    }

    public void init() throws Exception {
        try {
            Class.forName(mysql ? "com.mysql.cj.jdbc.Driver" : "org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // Paper ships both drivers; DriverManager will report a clear error if missing.
        }
        run(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS players ("
                        + "uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL, level INT NOT NULL, xp INT NOT NULL,"
                        + "kills INT NOT NULL, deaths INT NOT NULL, item_serial VARCHAR(36), created BIGINT NOT NULL,"
                        + "last_seen BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS cooldowns ("
                        + "uuid VARCHAR(36) NOT NULL, ability VARCHAR(48) NOT NULL, expires BIGINT NOT NULL,"
                        + "PRIMARY KEY (uuid, ability))");
                String id = mysql ? "BIGINT PRIMARY KEY AUTO_INCREMENT" : "INTEGER PRIMARY KEY AUTOINCREMENT";
                st.executeUpdate("CREATE TABLE IF NOT EXISTS history (id " + id + ", uuid VARCHAR(36) NOT NULL,"
                        + "ts BIGINT NOT NULL, event VARCHAR(32) NOT NULL, detail VARCHAR(255) NOT NULL)");
                try {
                    st.executeUpdate(mysql ? "CREATE INDEX idx_history_uuid ON history (uuid)"
                            : "CREATE INDEX IF NOT EXISTS idx_history_uuid ON history (uuid)");
                } catch (SQLException ignored) {
                    // index already exists (MySQL has no IF NOT EXISTS)
                }
                try {
                    st.executeUpdate("CREATE INDEX idx_players_name ON players (name)");
                } catch (SQLException ignored) {
                    // index already exists
                }
            }
            return null;
        }).get(30, TimeUnit.SECONDS);
    }

    private static PlayerData read(ResultSet rs) throws SQLException {
        PlayerData d = new PlayerData(UUID.fromString(rs.getString(1)), rs.getString(2));
        d.level = rs.getInt(3);
        d.xp = rs.getInt(4);
        d.kills = rs.getInt(5);
        d.deaths = rs.getInt(6);
        d.serial = rs.getString(7);
        d.created = rs.getLong(8);
        d.lastSeen = rs.getLong(9);
        return d;
    }

    private static void loadCooldowns(Connection c, PlayerData d) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT ability,expires FROM cooldowns WHERE uuid=?")) {
            ps.setString(1, d.uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                long now = System.currentTimeMillis();
                while (rs.next()) {
                    long exp = rs.getLong(2);
                    if (exp > now) d.cooldowns.put(rs.getString(1), exp);
                }
            }
        }
    }

    /** Completes with null when the player has no record yet. */
    public CompletableFuture<PlayerData> load(UUID id) {
        return run(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM players WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    PlayerData d = read(rs);
                    loadCooldowns(c, d);
                    return d;
                }
            }
        });
    }

    public CompletableFuture<PlayerData> findByName(String name) {
        return run(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + COLS + " FROM players WHERE LOWER(name)=LOWER(?) ORDER BY last_seen DESC LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    PlayerData d = read(rs);
                    loadCooldowns(c, d);
                    return d;
                }
            }
        });
    }

    public CompletableFuture<List<String>> namesLike(String prefix, int limit) {
        return run(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name FROM players WHERE LOWER(name) LIKE ? ORDER BY last_seen DESC LIMIT ?")) {
                ps.setString(1, prefix.toLowerCase().replace("%", "") + "%");
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1));
                }
            }
            return out;
        });
    }

    public CompletableFuture<Void> save(PlayerData d) {
        return run(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("REPLACE INTO players (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, d.uuid.toString());
                    ps.setString(2, d.name);
                    ps.setInt(3, d.level);
                    ps.setInt(4, d.xp);
                    ps.setInt(5, d.kills);
                    ps.setInt(6, d.deaths);
                    ps.setString(7, d.serial);
                    ps.setLong(8, d.created);
                    ps.setLong(9, d.lastSeen);
                    ps.executeUpdate();
                }
                try (PreparedStatement del = c.prepareStatement("DELETE FROM cooldowns WHERE uuid=?")) {
                    del.setString(1, d.uuid.toString());
                    del.executeUpdate();
                }
                long now = System.currentTimeMillis();
                try (PreparedStatement ins = c.prepareStatement("INSERT INTO cooldowns (uuid,ability,expires) VALUES (?,?,?)")) {
                    for (var e : d.cooldowns.entrySet()) {
                        if (e.getValue() <= now) continue;
                        ins.setString(1, d.uuid.toString());
                        ins.setString(2, e.getKey());
                        ins.setLong(3, e.getValue());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(auto);
            }
            return null;
        });
    }

    public CompletableFuture<List<PlayerData>> top(int limit) {
        return run(c -> {
            List<PlayerData> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS
                    + " FROM players ORDER BY level DESC, xp DESC, kills DESC, created ASC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(read(rs));
                }
            }
            return out;
        });
    }

    public void addHistory(UUID id, String event, String detail) {
        run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO history (uuid,ts,event,detail) VALUES (?,?,?,?)")) {
                ps.setString(1, id.toString());
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, event);
                ps.setString(4, detail.length() > 250 ? detail.substring(0, 250) : detail);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<HistoryEntry>> history(UUID id, int limit) {
        return run(c -> {
            List<HistoryEntry> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ts,event,detail FROM history WHERE uuid=? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, id.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new HistoryEntry(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }
            return out;
        });
    }

    /** SQLite: consistent file copy (VACUUM INTO). MySQL: YAML export. Old backups are pruned. */
    public CompletableFuture<Void> backup(File dir, int keep) {
        return run(c -> {
            dir.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            if (!mysql) {
                File f = new File(dir, "rubysteal-" + stamp + ".db");
                try (Statement st = c.createStatement()) {
                    st.execute("VACUUM INTO '" + f.getAbsolutePath().replace("'", "''") + "'");
                }
            } else {
                YamlConfiguration y = new YamlConfiguration();
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT " + COLS + " FROM players")) {
                    while (rs.next()) {
                        String p = "players." + rs.getString(1) + ".";
                        y.set(p + "name", rs.getString(2));
                        y.set(p + "level", rs.getInt(3));
                        y.set(p + "xp", rs.getInt(4));
                        y.set(p + "kills", rs.getInt(5));
                        y.set(p + "deaths", rs.getInt(6));
                        y.set(p + "item_serial", rs.getString(7));
                        y.set(p + "created", rs.getLong(8));
                        y.set(p + "last_seen", rs.getLong(9));
                    }
                }
                y.save(new File(dir, "rubysteal-" + stamp + ".yml"));
            }
            File[] files = dir.listFiles((d, n) -> n.startsWith("rubysteal-"));
            if (files != null && files.length > keep) {
                Arrays.sort(files, Comparator.comparing(File::getName).reversed());
                for (int i = keep; i < files.length; i++) files[i].delete();
            }
            return null;
        });
    }

    /** Lets queued writes finish, then closes the connection. */
    public void close() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, TimeUnit.SECONDS)) exec.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
            // shutting down
        }
    }
          }
