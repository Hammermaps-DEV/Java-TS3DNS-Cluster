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
 * REST API handler for TeamSpeak 3 server management.
 *
 * <pre>
 * GET    /api/servers        – list all servers
 * POST   /api/servers        – add a server
 * PUT    /api/servers/{id}   – update a server
 * DELETE /api/servers/{id}   – remove a server
 * </pre>
 */
public class ServersApiHandler extends BaseHandler {

    private final MySQLDatabaseHandler mysql;

    public ServersApiHandler(SessionManager sessionManager, MySQLDatabaseHandler mysql) {
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
        String idStr = extractIdFromPath(exchange, "/api/servers");

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
            LOG.log(Level.SEVERE, "Database error in ServersApiHandler", e);
            sendError(exchange, 500, "Database error: " + e.getMessage());
        }
    }

    private synchronized void handleList(HttpExchange exchange) throws IOException, SQLException {
        String query = "SELECT `id`, `ip`, `port`, `username`, `online` FROM `servers` ORDER BY `id` ASC;";
        JsonArray arr = new JsonArray();
        try (PreparedStatement stmt = mysql.prepare(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                JsonObject row = new JsonObject();
                row.addProperty("id", rs.getInt("id"));
                row.addProperty("ip", rs.getString("ip"));
                row.addProperty("port", rs.getString("port"));
                row.addProperty("username", rs.getString("username"));
                row.addProperty("online", rs.getInt("online"));
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

        String ip       = getString(body, "ip");
        String port     = getString(body, "port");
        String username = getString(body, "username");
        String password = getString(body, "password");

        if (ip == null || port == null || username == null || password == null) {
            sendError(exchange, 400, "Fields ip, port, username and password are required");
            return;
        }

        String query = "INSERT INTO `servers` (`ip`, `port`, `username`, `password`, `online`) VALUES (?, ?, ?, ?, 0);";
        try (PreparedStatement stmt = mysql.prepare(query)) {
            stmt.setString(1, ip.trim());
            stmt.setString(2, port.trim());
            stmt.setString(3, username.trim());
            stmt.setString(4, password);
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

        // Build dynamic update: only change password when explicitly supplied
        String password = getString(body, "password");
        String query;
        if (password != null && !password.isEmpty()) {
            query = "UPDATE `servers` SET `ip`=?, `port`=?, `username`=?, `password`=? WHERE `id`=?;";
        } else {
            query = "UPDATE `servers` SET `ip`=?, `port`=?, `username`=? WHERE `id`=?;";
        }

        try (PreparedStatement stmt = mysql.prepare(query)) {
            stmt.setString(1, nvl(getString(body, "ip"), ""));
            stmt.setString(2, nvl(getString(body, "port"), "10011"));
            stmt.setString(3, nvl(getString(body, "username"), ""));
            if (password != null && !password.isEmpty()) {
                stmt.setString(4, password);
                stmt.setInt(5, id);
            } else {
                stmt.setInt(4, id);
            }
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                sendError(exchange, 404, "Server not found");
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

        try (PreparedStatement stmt = mysql.prepare("DELETE FROM `servers` WHERE `id`=?;")) {
            stmt.setInt(1, id);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                sendError(exchange, 404, "Server not found");
                return;
            }
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    private static String nvl(String value, String defaultValue) {
        return (value != null) ? value : defaultValue;
    }
}
