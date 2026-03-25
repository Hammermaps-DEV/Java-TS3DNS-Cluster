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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ts3dns.admin.SessionManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for all Admin UI HTTP handlers.
 * Provides common utilities: session validation, JSON response helpers, body parsing.
 */
public abstract class BaseHandler implements HttpHandler {

    protected static final Gson GSON = new Gson();
    protected static final Logger LOG = Logger.getLogger(BaseHandler.class.getName());

    protected final SessionManager sessionManager;

    protected BaseHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    // -----------------------------------------------------------------------
    // Session / auth helpers
    // -----------------------------------------------------------------------

    /** Extracts the ADMIN_SESSION cookie value from the request. */
    protected String getSessionToken(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("ADMIN_SESSION=")) {
                return trimmed.substring("ADMIN_SESSION=".length());
            }
        }
        return null;
    }

    /** Returns true when a valid session token is present in the request. */
    protected boolean isAuthenticated(HttpExchange exchange) {
        return sessionManager.isValid(getSessionToken(exchange));
    }

    // -----------------------------------------------------------------------
    // Response helpers
    // -----------------------------------------------------------------------

    protected void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    protected void sendUnauthorized(HttpExchange exchange) throws IOException {
        sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
    }

    protected void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendError(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        sendJson(exchange, status, GSON.toJson(err));
    }

    protected void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "Method Not Allowed");
    }

    // -----------------------------------------------------------------------
    // Request helpers
    // -----------------------------------------------------------------------

    /** Reads the full request body as a UTF-8 string (Java 8 compatible). */
    protected String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toString("UTF-8");
        }
    }

    /** Parses the request body as a JSON object; returns null on error. */
    protected JsonObject parseJsonBody(HttpExchange exchange) {
        try {
            String body = readBody(exchange);
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            LOG.log(Level.WARNING, "Failed to parse JSON request body: " + e.getMessage());
            return null;
        }
    }

    /** Extracts the last path segment (used as an ID) from the request URI. */
    protected String extractIdFromPath(HttpExchange exchange, String basePath) {
        String path = exchange.getRequestURI().getPath();
        if (path.length() > basePath.length()) {
            return path.substring(basePath.length()).replaceAll("^/+", "");
        }
        return null;
    }

    /** Returns the string value of a JSON field or null if absent/null. */
    protected String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }
}
