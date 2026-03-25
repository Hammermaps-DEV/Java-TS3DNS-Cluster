/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Product: Java TS3-DNS Server Cluster - Admin UI
 * Version: 1.0 Beta
 * Autor: Hammermaps.de Development Team
 * Homepage: http://www.hammermaps.de
 */

package ts3dns.admin.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import ts3dns.admin.SessionManager;
import ts3dns.database.MySQLDatabaseHandler;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * REST API handler for DNS usage statistics.
 *
 * <pre>
 * GET /api/stats – aggregated statistics
 *   {
 *     total_dns:      &lt;int&gt;,
 *     total_servers:  &lt;int&gt;,
 *     total_queries:  &lt;long&gt;,
 *     online_servers: &lt;int&gt;,
 *     top_domains:    [ {dns, usecount, lastused, active_slots, slots, ip, port}, ... ]
 *   }
 * </pre>
 */
public class StatsApiHandler extends BaseHandler {

    private final MySQLDatabaseHandler mysql;

    public StatsApiHandler(SessionManager sessionManager, MySQLDatabaseHandler mysql) {
        super(sessionManager);
        this.mysql = mysql;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthenticated(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            JsonObject stats = buildStats();
            sendJson(exchange, 200, GSON.toJson(stats));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Database error in StatsApiHandler", e);
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    private synchronized JsonObject buildStats() throws SQLException {
        JsonObject stats = new JsonObject();

        // Total DNS entries
        try (PreparedStatement stmt = mysql.prepare("SELECT COUNT(*) AS cnt FROM `dns`;");
             ResultSet rs = stmt.executeQuery()) {
            stats.addProperty("total_dns", rs.next() ? rs.getLong("cnt") : 0);
        }

        // Total servers
        try (PreparedStatement stmt = mysql.prepare("SELECT COUNT(*) AS cnt FROM `servers`;");
             ResultSet rs = stmt.executeQuery()) {
            stats.addProperty("total_servers", rs.next() ? rs.getLong("cnt") : 0);
        }

        // Online servers
        try (PreparedStatement stmt = mysql.prepare("SELECT COUNT(*) AS cnt FROM `servers` WHERE `online`=1;");
             ResultSet rs = stmt.executeQuery()) {
            stats.addProperty("online_servers", rs.next() ? rs.getLong("cnt") : 0);
        }

        // Total queries (sum of usecount)
        try (PreparedStatement stmt = mysql.prepare("SELECT SUM(`usecount`) AS total FROM `dns`;");
             ResultSet rs = stmt.executeQuery()) {
            stats.addProperty("total_queries", rs.next() ? rs.getLong("total") : 0);
        }

        // Top 10 most accessed domains
        String topQuery = "SELECT `dns`, `usecount`, `lastused`, `active_slots`, `slots`, `ip`, `port`"
                + " FROM `dns` ORDER BY `usecount` DESC LIMIT 10;";
        JsonArray topDomains = new JsonArray();
        try (PreparedStatement stmt = mysql.prepare(topQuery);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                JsonObject row = new JsonObject();
                row.addProperty("dns", rs.getString("dns"));
                row.addProperty("usecount", rs.getLong("usecount"));
                row.addProperty("lastused", rs.getLong("lastused"));
                row.addProperty("active_slots", rs.getInt("active_slots"));
                row.addProperty("slots", rs.getInt("slots"));
                row.addProperty("ip", rs.getString("ip"));
                row.addProperty("port", rs.getString("port"));
                topDomains.add(row);
            }
        }
        stats.add("top_domains", topDomains);

        // Recently accessed domains (last 10)
        String recentQuery = "SELECT `dns`, `usecount`, `lastused`, `ip`, `port`"
                + " FROM `dns` WHERE `lastused` > 0 ORDER BY `lastused` DESC LIMIT 10;";
        JsonArray recentDomains = new JsonArray();
        try (PreparedStatement stmt = mysql.prepare(recentQuery);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                JsonObject row = new JsonObject();
                row.addProperty("dns", rs.getString("dns"));
                row.addProperty("usecount", rs.getLong("usecount"));
                row.addProperty("lastused", rs.getLong("lastused"));
                row.addProperty("ip", rs.getString("ip"));
                row.addProperty("port", rs.getString("port"));
                recentDomains.add(row);
            }
        }
        stats.add("recent_domains", recentDomains);

        return stats;
    }
}
