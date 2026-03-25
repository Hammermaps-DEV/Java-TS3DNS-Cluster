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
 * REST API handler for DNS entry management.
 *
 * <pre>
 * GET    /api/dns        – list all DNS entries
 * POST   /api/dns        – create a new DNS entry
 * PUT    /api/dns/{id}   – update a DNS entry
 * DELETE /api/dns/{id}   – delete a DNS entry
 * </pre>
 */
public class DnsApiHandler extends BaseHandler {

    private final MySQLDatabaseHandler mysql;

    public DnsApiHandler(SessionManager sessionManager, MySQLDatabaseHandler mysql) {
        super(sessionManager);
        this.mysql = mysql;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthenticated(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String idStr = extractIdFromPath(exchange, "/api/dns");

        try {
            if ("GET".equalsIgnoreCase(method) && (idStr == null || idStr.isEmpty())) {
                handleList(exchange);
            } else if ("POST".equalsIgnoreCase(method) && (idStr == null || idStr.isEmpty())) {
                handleCreate(exchange);
            } else if ("PUT".equalsIgnoreCase(method) && idStr != null && !idStr.isEmpty()) {
                handleUpdate(exchange, idStr);
            } else if ("DELETE".equalsIgnoreCase(method) && idStr != null && !idStr.isEmpty()) {
                handleDelete(exchange, idStr);
            } else {
                sendMethodNotAllowed(exchange);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Database error in DnsApiHandler", e);
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    private synchronized void handleList(HttpExchange exchange) throws IOException, SQLException {
        String query = "SELECT `id`, `dns`, `ip`, `port`, `server-id`, `failback_ip`, `failback_port`,"
                + " `failback`, `default`, `machine-id`, `lastused`, `usecount`, `active_slots`,"
                + " `slots`, `name`, `vserver-id` FROM `dns` ORDER BY `id` ASC;";

        JsonArray arr = new JsonArray();
        try (PreparedStatement stmt = mysql.prepare(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                JsonObject row = new JsonObject();
                row.addProperty("id", rs.getInt("id"));
                row.addProperty("dns", rs.getString("dns"));
                row.addProperty("ip", rs.getString("ip"));
                row.addProperty("port", rs.getString("port"));
                row.addProperty("server_id", rs.getString("server-id"));
                row.addProperty("failback_ip", rs.getString("failback_ip"));
                row.addProperty("failback_port", rs.getString("failback_port"));
                row.addProperty("failback", rs.getInt("failback"));
                row.addProperty("is_default", rs.getInt("default"));
                row.addProperty("machine_id", rs.getInt("machine-id"));
                row.addProperty("lastused", rs.getLong("lastused"));
                row.addProperty("usecount", rs.getLong("usecount"));
                row.addProperty("active_slots", rs.getInt("active_slots"));
                row.addProperty("slots", rs.getInt("slots"));
                row.addProperty("name", rs.getString("name"));
                row.addProperty("vserver_id", rs.getString("vserver-id"));
                arr.add(row);
            }
        }
        sendJson(exchange, 200, GSON.toJson(arr));
    }

    private synchronized void handleCreate(HttpExchange exchange) throws IOException, SQLException {
        JsonObject body = parseJsonBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body");
            return;
        }

        String dns    = getString(body, "dns");
        String ip     = getString(body, "ip");
        String port   = getString(body, "port");
        if (dns == null || dns.trim().isEmpty() || ip == null || port == null) {
            sendError(exchange, 400, "Fields dns, ip and port are required");
            return;
        }

        String query = "INSERT INTO `dns` (`dns`, `ip`, `port`, `server-id`, `failback_ip`, `failback_port`,"
                + " `failback`, `default`, `machine-id`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement stmt = mysql.prepare(query)) {
            stmt.setString(1, dns.trim());
            stmt.setString(2, ip.trim());
            stmt.setString(3, port.trim());
            stmt.setString(4, nvl(getString(body, "server_id"), "0"));
            stmt.setString(5, nvl(getString(body, "failback_ip"), ""));
            stmt.setString(6, nvl(getString(body, "failback_port"), "9987"));
            stmt.setInt(7, parseBool(getString(body, "failback")));
            stmt.setInt(8, parseBool(getString(body, "is_default")));
            stmt.setInt(9, parseIntSafe(getString(body, "machine_id"), 0));
            stmt.executeUpdate();
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        sendJson(exchange, 201, GSON.toJson(resp));
    }

    private synchronized void handleUpdate(HttpExchange exchange, String idStr) throws IOException, SQLException {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid id");
            return;
        }

        JsonObject body = parseJsonBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body");
            return;
        }

        String query = "UPDATE `dns` SET `dns`=?, `ip`=?, `port`=?, `server-id`=?, `failback_ip`=?,"
                + " `failback_port`=?, `failback`=?, `default`=?, `machine-id`=? WHERE `id`=?;";
        try (PreparedStatement stmt = mysql.prepare(query)) {
            stmt.setString(1, nvl(getString(body, "dns"), ""));
            stmt.setString(2, nvl(getString(body, "ip"), ""));
            stmt.setString(3, nvl(getString(body, "port"), "9987"));
            stmt.setString(4, nvl(getString(body, "server_id"), "0"));
            stmt.setString(5, nvl(getString(body, "failback_ip"), ""));
            stmt.setString(6, nvl(getString(body, "failback_port"), "9987"));
            stmt.setInt(7, parseBool(getString(body, "failback")));
            stmt.setInt(8, parseBool(getString(body, "is_default")));
            stmt.setInt(9, parseIntSafe(getString(body, "machine_id"), 0));
            stmt.setInt(10, id);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                sendError(exchange, 404, "DNS entry not found");
                return;
            }
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    private synchronized void handleDelete(HttpExchange exchange, String idStr) throws IOException, SQLException {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid id");
            return;
        }

        try (PreparedStatement stmt = mysql.prepare("DELETE FROM `dns` WHERE `id`=?;")) {
            stmt.setInt(1, id);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                sendError(exchange, 404, "DNS entry not found");
                return;
            }
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String nvl(String value, String defaultValue) {
        return (value != null) ? value : defaultValue;
    }

    private static int parseBool(String value) {
        if (value == null) return 0;
        return ("true".equalsIgnoreCase(value) || "1".equals(value)) ? 1 : 0;
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
