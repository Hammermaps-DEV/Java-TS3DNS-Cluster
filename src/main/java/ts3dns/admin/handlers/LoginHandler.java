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

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import ts3dns.admin.SessionManager;
import ts3dns.cluster.TS3DNSCluster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Handles POST /api/login and POST /api/logout.
 *
 * Login:  validates username+password from config; on success sets HttpOnly session cookie.
 * Logout: invalidates the session token and clears the cookie.
 */
public class LoginHandler extends BaseHandler {

    public LoginHandler(SessionManager sessionManager) {
        super(sessionManager);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equalsIgnoreCase(method) && "/api/login".equals(path)) {
            handleLogin(exchange);
        } else if ("POST".equalsIgnoreCase(method) && "/api/logout".equals(path)) {
            handleLogout(exchange);
        } else if ("GET".equalsIgnoreCase(method) && "/api/me".equals(path)) {
            handleMe(exchange);
        } else {
            sendMethodNotAllowed(exchange);
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        JsonObject body = parseJsonBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid request body");
            return;
        }

        String username = getString(body, "username");
        String password = getString(body, "password");

        if (username == null || password == null) {
            sendError(exchange, 400, "Username and password are required");
            return;
        }

        String cfgUser = TS3DNSCluster.getProperty("admin_ui_username");
        String cfgPass = TS3DNSCluster.getProperty("admin_ui_password");

        if (cfgUser == null || cfgPass == null) {
            sendError(exchange, 500, "Admin UI credentials not configured");
            return;
        }

        // Use constant-time comparison to prevent timing attacks
        if (constantTimeEquals(cfgUser, username) && constantTimeEquals(cfgPass, password)) {
            String token = sessionManager.createSession();
            exchange.getResponseHeaders().add("Set-Cookie",
                    "ADMIN_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Strict");
            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            sendJson(exchange, 200, GSON.toJson(resp));
        } else {
            sendError(exchange, 401, "Invalid username or password");
        }
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String token = getSessionToken(exchange);
        if (token != null) {
            sessionManager.invalidate(token);
        }
        exchange.getResponseHeaders().add("Set-Cookie",
                "ADMIN_SESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        if (!isAuthenticated(exchange)) {
            sendUnauthorized(exchange);
            return;
        }
        JsonObject resp = new JsonObject();
        resp.addProperty("authenticated", true);
        resp.addProperty("username", TS3DNSCluster.getProperty("admin_ui_username"));
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    /**
     * Constant-time string comparison to prevent timing side-channel attacks.
     * Uses MessageDigest.isEqual which runs in constant time.
     */
    private boolean constantTimeEquals(String a, String b) {
        try {
            byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
            byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
            // Pad to same length before comparing to avoid length-based timing leaks
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] aHash = md.digest(aBytes);
            md.reset();
            byte[] bHash = md.digest(bBytes);
            return MessageDigest.isEqual(aHash, bHash);
        } catch (NoSuchAlgorithmException e) {
            return a.equals(b);
        }
    }
}
