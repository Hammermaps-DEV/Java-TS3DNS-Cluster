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

package ts3dns.admin.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import ts3dns.admin.SessionManager;
import ts3dns.cluster.FloodProtection;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * REST API handler for Anti-Flood configuration and IP ban management.
 *
 * <pre>
 * GET    /api/flood/settings       – get current flood-protection settings
 * PUT    /api/flood/settings       – update settings at runtime
 * GET    /api/flood/stats          – flood statistics (blocked count, top IPs …)
 * GET    /api/flood/bans           – list all IP bans
 * POST   /api/flood/bans           – ban an IP address
 * DELETE /api/flood/bans/{ip}      – remove an IP ban
 * </pre>
 */
public class FloodApiHandler extends BaseHandler {

    public FloodApiHandler(SessionManager sessionManager) {
        super(sessionManager);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthenticated(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        String method = exchange.getRequestMethod();
        String path   = exchange.getRequestURI().getPath();

        try {
            if (path.equals("/api/flood/settings")) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetSettings(exchange);
                } else if ("PUT".equalsIgnoreCase(method)) {
                    handlePutSettings(exchange);
                } else {
                    sendMethodNotAllowed(exchange);
                }
            } else if (path.equals("/api/flood/stats")) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGetStats(exchange);
                } else {
                    sendMethodNotAllowed(exchange);
                }
            } else if (path.equals("/api/flood/bans")) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleListBans(exchange);
                } else if ("POST".equalsIgnoreCase(method)) {
                    handleAddBan(exchange);
                } else {
                    sendMethodNotAllowed(exchange);
                }
            } else if (path.startsWith("/api/flood/bans/")) {
                if ("DELETE".equalsIgnoreCase(method)) {
                    handleDeleteBan(exchange, path);
                } else {
                    sendMethodNotAllowed(exchange);
                }
            } else {
                sendError(exchange, 404, "Not found");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error in FloodApiHandler", e);
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------------

    private void handleGetSettings(HttpExchange exchange) throws IOException {
        FloodProtection fp = FloodProtection.getInstance();
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled",                   fp.isEnabled());
        obj.addProperty("max_requests_per_window",   fp.getMaxRequestsPerWindow());
        obj.addProperty("window_seconds",            fp.getWindowSeconds());
        obj.addProperty("auto_ban_threshold",        fp.getAutoBanThreshold());
        obj.addProperty("auto_ban_duration_seconds", fp.getAutoBanDurationSeconds());
        sendJson(exchange, 200, GSON.toJson(obj));
    }

    private void handlePutSettings(HttpExchange exchange) throws IOException {
        JsonObject body = parseJsonBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body");
            return;
        }
        FloodProtection fp = FloodProtection.getInstance();
        if (body.has("enabled"))
            fp.setEnabled(body.get("enabled").getAsBoolean());
        if (body.has("max_requests_per_window"))
            fp.setMaxRequestsPerWindow(body.get("max_requests_per_window").getAsInt());
        if (body.has("window_seconds"))
            fp.setWindowSeconds(body.get("window_seconds").getAsInt());
        if (body.has("auto_ban_threshold"))
            fp.setAutoBanThreshold(body.get("auto_ban_threshold").getAsInt());
        if (body.has("auto_ban_duration_seconds"))
            fp.setAutoBanDurationSeconds(body.get("auto_ban_duration_seconds").getAsInt());

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        sendJson(exchange, 200, GSON.toJson(resp));
    }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    private void handleGetStats(HttpExchange exchange) throws IOException {
        FloodProtection fp = FloodProtection.getInstance();
        JsonObject obj = new JsonObject();
        obj.addProperty("total_blocked", fp.getTotalBlocked());
        obj.addProperty("tracked_ips",   fp.getTrackedIpCount());
        obj.addProperty("banned_ips",    fp.getBannedIpCount());

        JsonObject topBlockedJson = new JsonObject();
        fp.getTopBlockedIps(10).forEach((k, v) -> topBlockedJson.addProperty(k, v));
        obj.add("top_blocked_ips", topBlockedJson);

        sendJson(exchange, 200, GSON.toJson(obj));
    }

    // -----------------------------------------------------------------------
    // Bans CRUD
    // -----------------------------------------------------------------------

    private void handleListBans(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> bans = FloodProtection.getInstance().getBanList();
        JsonArray arr = new JsonArray();
        for (Map<String, Object> ban : bans) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id",        (Number) ban.get("id"));
            obj.addProperty("ip",        (String) ban.get("ip"));
            obj.addProperty("reason",    (String) ban.get("reason"));
            obj.addProperty("banned_at", (Number) ban.get("banned_at"));
            obj.addProperty("expires_at",(Number) ban.get("expires_at"));
            arr.add(obj);
        }
        sendJson(exchange, 200, GSON.toJson(arr));
    }

    private void handleAddBan(HttpExchange exchange) throws IOException {
        JsonObject body = parseJsonBody(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid JSON body");
            return;
        }
        String ip = getString(body, "ip");
        if (ip == null || ip.trim().isEmpty()) {
            sendError(exchange, 400, "Field 'ip' is required");
            return;
        }
        String reason   = getString(body, "reason");
        int    duration = body.has("duration_seconds")
                ? body.get("duration_seconds").getAsInt() : 3600;

        FloodProtection.getInstance().banIp(ip.trim(), reason, duration);

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        sendJson(exchange, 201, GSON.toJson(resp));
    }

    private void handleDeleteBan(HttpExchange exchange, String path) throws IOException {
        // path = /api/flood/bans/{ip}  (IP may be URL-encoded)
        String encodedIp = path.substring("/api/flood/bans/".length());
        if (encodedIp.isEmpty()) {
            sendError(exchange, 400, "IP address required in path");
            return;
        }
        String ip = URLDecoder.decode(encodedIp, StandardCharsets.UTF_8.name());
        FloodProtection.getInstance().unbanIp(ip);

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        sendJson(exchange, 200, GSON.toJson(resp));
    }
}
