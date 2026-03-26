/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.

 * Product: Java TS3-DNS Server Cluster
 * Version: 1.0 Beta
 * Autor: Hammermaps.de Development Team
 * Homepage: http://www.hammermaps.de
 */

package ts3dns.cluster;

import ts3dns.database.MySQLDatabaseHandler;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Singleton that provides per-IP rate-limiting (flood protection) and IP-ban
 * management for the TS3-DNS server.
 *
 * <p>A fixed-window counter tracks requests per IP. When the configured limit
 * is exceeded, the connection is dropped and the block counter for that IP is
 * incremented. If the block counter reaches the auto-ban threshold the IP is
 * automatically banned and the ban is persisted in the {@code flood_bans}
 * MySQL table so it survives server restarts.
 *
 * <p>Settings are loaded from the config file at startup and can be updated
 * at runtime via the Admin UI without restarting the server.
 *
 * <p>Relevant config keys: {@code flood_protection_enabled},
 * {@code flood_max_requests}, {@code flood_window_seconds},
 * {@code flood_auto_ban_threshold}, {@code flood_auto_ban_duration}.
 */
public class FloodProtection {

    private static final FloodProtection INSTANCE = new FloodProtection();

    // IP -> [requestCount, windowStartMs]  (fixed-window rate tracker)
    private final ConcurrentHashMap<String, long[]> requestTracker = new ConcurrentHashMap<>();

    // In-memory ban cache: IP -> expiry epoch-seconds (0 = permanent)
    private final ConcurrentHashMap<String, Long> bannedIps = new ConcurrentHashMap<>();

    // Block count per IP since startup (resets on restart)
    private final ConcurrentHashMap<String, Long> blockCountPerIp = new ConcurrentHashMap<>();

    // Total requests blocked since startup
    private final AtomicLong totalBlocked = new AtomicLong(0);

    // Runtime settings – volatile for visibility across threads
    private volatile boolean enabled = true;
    private volatile int maxRequestsPerWindow = 10;
    private volatile int windowSeconds = 1;
    private volatile int autoBanThreshold = 100; // 0 = disabled
    private volatile int autoBanDurationSeconds = 3600; // 0 = permanent

    private MySQLDatabaseHandler mysql;

    private FloodProtection() {}

    public static FloodProtection getInstance() {
        return INSTANCE;
    }

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    /**
     * Initialises flood protection with a database connection.
     * Creates the {@code flood_bans} table if it does not exist, loads
     * settings from the config file, and pre-loads active bans into memory.
     */
    public void init(MySQLDatabaseHandler mysql) {
        this.mysql = mysql;
        createTableIfNeeded();
        loadSettings();
        loadBansFromDb();
        TS3DNSCluster.log(FloodProtection.class.getName(), Level.INFO,
                "Flood protection initialised (enabled=" + enabled
                        + ", max=" + maxRequestsPerWindow + "/" + windowSeconds + "s)", false);
    }

    private void createTableIfNeeded() {
        String sql = "CREATE TABLE IF NOT EXISTS `flood_bans` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
                + "`ip` VARCHAR(45) NOT NULL,"
                + "`reason` VARCHAR(255) DEFAULT '',"
                + "`banned_at` BIGINT NOT NULL,"
                + "`expires_at` BIGINT DEFAULT 0,"
                + "UNIQUE KEY `uq_ip` (`ip`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (PreparedStatement stmt = mysql.prepare(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            TS3DNSCluster.log(FloodProtection.class.getName(), Level.WARNING,
                    "Could not create flood_bans table: " + e.getMessage(), false);
        }
    }

    /** Reads flood-related keys from the application configuration. */
    public void loadSettings() {
        String val;
        val = TS3DNSCluster.getProperty("flood_protection_enabled");
        if (val != null) enabled = Boolean.parseBoolean(val.trim());

        val = TS3DNSCluster.getProperty("flood_max_requests");
        if (val != null) { try { maxRequestsPerWindow = Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {} }

        val = TS3DNSCluster.getProperty("flood_window_seconds");
        if (val != null) { try { windowSeconds = Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {} }

        val = TS3DNSCluster.getProperty("flood_auto_ban_threshold");
        if (val != null) { try { autoBanThreshold = Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {} }

        val = TS3DNSCluster.getProperty("flood_auto_ban_duration");
        if (val != null) { try { autoBanDurationSeconds = Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {} }
    }

    /** Loads all stored bans from the database into the in-memory cache. */
    public void loadBansFromDb() {
        if (mysql == null) return;
        bannedIps.clear();
        try (PreparedStatement stmt = mysql.prepare("SELECT `ip`, `expires_at` FROM `flood_bans`;");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                bannedIps.put(rs.getString("ip"), rs.getLong("expires_at"));
            }
        } catch (SQLException e) {
            TS3DNSCluster.log(FloodProtection.class.getName(), Level.WARNING,
                    "Could not load flood bans from DB: " + e.getMessage(), false);
        }
    }

    // -----------------------------------------------------------------------
    // Rate-limit / ban check
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when the given IP is allowed to proceed,
     * {@code false} when it must be dropped (banned or rate-limited).
     */
    public boolean isAllowed(String ip) {
        if (!enabled) return true;

        // 1. Check ban list
        Long banExpiry = bannedIps.get(ip);
        if (banExpiry != null) {
            long nowSec = System.currentTimeMillis() / 1000L;
            if (banExpiry == 0L || nowSec < banExpiry) {
                return false; // still banned
            }
            // Ban expired – clean up
            bannedIps.remove(ip);
            removeBanFromDb(ip);
        }

        // 2. Fixed-window rate limit
        long now = System.currentTimeMillis();
        long windowMs = (long) windowSeconds * 1000L;
        long[] slot = requestTracker.compute(ip, (k, v) -> {
            if (v == null || (now - v[1]) >= windowMs) {
                return new long[]{1L, now};
            }
            v[0]++;
            return v;
        });

        if (slot[0] > maxRequestsPerWindow) {
            totalBlocked.incrementAndGet();
            long blockCount = blockCountPerIp.merge(ip, 1L, Long::sum);
            // Auto-ban when threshold is reached
            if (autoBanThreshold > 0 && blockCount >= autoBanThreshold) {
                banIp(ip, "Auto-banned (flood)", autoBanDurationSeconds);
            }
            return false;
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // Ban management
    // -----------------------------------------------------------------------

    /**
     * Bans an IP address and persists the ban to the database.
     *
     * @param ip              IPv4 or IPv6 address string
     * @param reason          human-readable reason (may be null)
     * @param durationSeconds ban lifetime; 0 means permanent
     */
    public void banIp(String ip, String reason, int durationSeconds) {
        long nowSec = System.currentTimeMillis() / 1000L;
        long expiresAt = (durationSeconds <= 0) ? 0L : (nowSec + durationSeconds);
        bannedIps.put(ip, expiresAt);
        persistBan(ip, reason, nowSec, expiresAt);
    }

    /** Removes a ban for the given IP address from memory and the database. */
    public void unbanIp(String ip) {
        bannedIps.remove(ip);
        blockCountPerIp.remove(ip);
        removeBanFromDb(ip);
    }

    private void persistBan(String ip, String reason, long bannedAt, long expiresAt) {
        if (mysql == null) return;
        String sql = "INSERT INTO `flood_bans` (`ip`,`reason`,`banned_at`,`expires_at`)"
                + " VALUES (?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE `reason`=?, `banned_at`=?, `expires_at`=?;";
        try (PreparedStatement stmt = mysql.prepare(sql)) {
            stmt.setString(1, ip);
            stmt.setString(2, reason != null ? reason : "");
            stmt.setLong(3, bannedAt);
            stmt.setLong(4, expiresAt);
            stmt.setString(5, reason != null ? reason : "");
            stmt.setLong(6, bannedAt);
            stmt.setLong(7, expiresAt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            TS3DNSCluster.log(FloodProtection.class.getName(), Level.WARNING,
                    "Could not persist flood ban for " + ip + ": " + e.getMessage(), false);
        }
    }

    private void removeBanFromDb(String ip) {
        if (mysql == null) return;
        try (PreparedStatement stmt = mysql.prepare("DELETE FROM `flood_bans` WHERE `ip` = ?;")) {
            stmt.setString(1, ip);
            stmt.executeUpdate();
        } catch (SQLException e) {
            TS3DNSCluster.log(FloodProtection.class.getName(), Level.WARNING,
                    "Could not remove flood ban for " + ip + ": " + e.getMessage(), false);
        }
    }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    /** Returns a snapshot of the top-N most-blocked IPs ordered by block count. */
    public Map<String, Long> getTopBlockedIps(int limit) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(blockCountPerIp.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        Map<String, Long> result = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Long> e : entries) {
            if (count++ >= limit) break;
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    /** Returns all bans currently stored in the database. */
    public List<Map<String, Object>> getBanList() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (mysql == null) return result;
        String sql = "SELECT `id`,`ip`,`reason`,`banned_at`,`expires_at`"
                + " FROM `flood_bans` ORDER BY `banned_at` DESC;";
        try (PreparedStatement stmt = mysql.prepare(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("ip", rs.getString("ip"));
                row.put("reason", rs.getString("reason"));
                row.put("banned_at", rs.getLong("banned_at"));
                row.put("expires_at", rs.getLong("expires_at"));
                result.add(row);
            }
        } catch (SQLException e) {
            TS3DNSCluster.log(FloodProtection.class.getName(), Level.WARNING,
                    "Could not load ban list: " + e.getMessage(), false);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Settings getters / setters (used by Admin UI)
    // -----------------------------------------------------------------------

    public boolean isEnabled()                   { return enabled; }
    public void setEnabled(boolean v)             { enabled = v; }

    public int getMaxRequestsPerWindow()          { return maxRequestsPerWindow; }
    public void setMaxRequestsPerWindow(int v)    { maxRequestsPerWindow = Math.max(1, v); }

    public int getWindowSeconds()                 { return windowSeconds; }
    public void setWindowSeconds(int v)           { windowSeconds = Math.max(1, v); }

    public int getAutoBanThreshold()              { return autoBanThreshold; }
    public void setAutoBanThreshold(int v)        { autoBanThreshold = Math.max(0, v); }

    public int getAutoBanDurationSeconds()        { return autoBanDurationSeconds; }
    public void setAutoBanDurationSeconds(int v)  { autoBanDurationSeconds = Math.max(0, v); }

    public long getTotalBlocked()                 { return totalBlocked.get(); }
    public int  getTrackedIpCount()               { return requestTracker.size(); }
    public int  getBannedIpCount()                { return bannedIps.size(); }
}
