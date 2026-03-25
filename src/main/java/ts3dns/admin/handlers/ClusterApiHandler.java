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
import ts3dns.cluster.TS3DNSCluster;
import ts3dns.cluster.TS3DNSClusterServer;

import java.io.IOException;

/**
 * REST API handler for cluster status information.
 *
 * <pre>
 * GET /api/cluster – returns current node and cluster information
 * </pre>
 */
public class ClusterApiHandler extends BaseHandler {

    public ClusterApiHandler(SessionManager sessionManager) {
        super(sessionManager);
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

        JsonObject info = new JsonObject();

        // Node information
        info.addProperty("version", TS3DNSCluster.VERSION);
        info.addProperty("machine_id", TS3DNSClusterServer.machine_id);
        info.addProperty("dns_port", TS3DNSClusterServer.port);
        info.addProperty("is_master",
                Boolean.parseBoolean(TS3DNSCluster.getProperty("default_master_server")));
        info.addProperty("is_slave",
                Boolean.parseBoolean(TS3DNSCluster.getProperty("default_slave_server")));

        // Couchbase cluster
        JsonObject cb = new JsonObject();
        cb.addProperty("enabled", TS3DNSClusterServer.cb_enabled);
        if (TS3DNSClusterServer.cb_enabled) {
            cb.addProperty("host", TS3DNSCluster.getProperty("couchbase_host"));
            cb.addProperty("bucket", TS3DNSCluster.getProperty("couchbase_bucket"));
            cb.addProperty("machine_id", TS3DNSClusterServer.cb_machine_id);
        }
        info.add("couchbase", cb);

        // Admin UI configuration
        JsonObject adminInfo = new JsonObject();
        adminInfo.addProperty("port", getAdminPort());
        info.add("admin_ui", adminInfo);

        // Config summary (non-sensitive)
        JsonObject cfg = new JsonObject();
        cfg.addProperty("default_ip", TS3DNSCluster.getProperty("default_ip_for_dns"));
        cfg.addProperty("default_port", TS3DNSCluster.getProperty("default_port_for_dns"));
        cfg.addProperty("debug", Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug")));
        cfg.addProperty("send_messages",
                Boolean.parseBoolean(TS3DNSCluster.getProperty("default_send_messages")));
        info.add("config", cfg);

        sendJson(exchange, 200, GSON.toJson(info));
    }

    private int getAdminPort() {
        String portStr = TS3DNSCluster.getProperty("admin_ui_port");
        if (portStr != null) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return 8080;
    }
}
