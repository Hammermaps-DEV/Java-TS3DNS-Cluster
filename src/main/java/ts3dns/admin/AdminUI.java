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

package ts3dns.admin;

import com.sun.net.httpserver.HttpServer;
import ts3dns.admin.handlers.ClusterApiHandler;
import ts3dns.admin.handlers.DnsApiHandler;
import ts3dns.admin.handlers.LoginHandler;
import ts3dns.admin.handlers.ServersApiHandler;
import ts3dns.admin.handlers.StaticFileHandler;
import ts3dns.admin.handlers.StatsApiHandler;
import ts3dns.cluster.TS3DNSCluster;
import ts3dns.database.MySQLDatabaseHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedded HTTP server for the TS3-DNS Cluster Admin UI.
 *
 * <p>Starts on the port configured as {@code admin_ui_port} (default 8080) and exposes:
 * <ul>
 *   <li>Static files (login.html, index.html, JS) under {@code /}</li>
 *   <li>REST API under {@code /api/}</li>
 * </ul>
 *
 * <p>Enable or disable via {@code admin_ui_enabled=true|false} in TS3DNS-Cluster.cfg.
 *
 * <p>Secured with username/password from {@code admin_ui_username} / {@code admin_ui_password}.
 */
public class AdminUI {

    private static final Logger LOG = Logger.getLogger(AdminUI.class.getName());

    private final HttpServer httpServer;
    private final MySQLDatabaseHandler mysql;
    private final SessionManager sessionManager;

    public AdminUI() throws IOException {
        int adminPort = getConfigInt("admin_ui_port", 8080);
        String bindIp  = TS3DNSCluster.getProperty("admin_ui_bind");

        sessionManager = new SessionManager();

        // Create a dedicated MySQL connection for the Admin UI
        String mysqlHost = TS3DNSCluster.getProperty("mysql_host");
        String mysqlUser = TS3DNSCluster.getProperty("mysql_user");
        String mysqlPass = TS3DNSCluster.getProperty("mysql_pass");
        String mysqlDb   = TS3DNSCluster.getProperty("mysql_db");
        int    mysqlPort = getConfigInt("mysql_port", 3306);
        mysql = new MySQLDatabaseHandler(mysqlHost, mysqlPort, mysqlUser, mysqlPass, mysqlDb);

        // Create HTTP server
        InetSocketAddress addr;
        if (bindIp != null && !bindIp.isEmpty() && !"0.0.0.0".equals(bindIp)) {
            addr = new InetSocketAddress(bindIp, adminPort);
        } else {
            addr = new InetSocketAddress(adminPort);
        }
        httpServer = HttpServer.create(addr, 0);

        // Register handlers
        LoginHandler loginHandler    = new LoginHandler(sessionManager);
        DnsApiHandler dnsHandler     = new DnsApiHandler(sessionManager, mysql);
        ServersApiHandler srvHandler = new ServersApiHandler(sessionManager, mysql);
        StatsApiHandler statsHandler = new StatsApiHandler(sessionManager, mysql);
        ClusterApiHandler clsHandler = new ClusterApiHandler(sessionManager);
        StaticFileHandler staticHandler = new StaticFileHandler(sessionManager, "/");

        httpServer.createContext("/api/login",   loginHandler);
        httpServer.createContext("/api/logout",  loginHandler);
        httpServer.createContext("/api/me",      loginHandler);
        httpServer.createContext("/api/dns",     dnsHandler);
        httpServer.createContext("/api/servers", srvHandler);
        httpServer.createContext("/api/stats",   statsHandler);
        httpServer.createContext("/api/cluster", clsHandler);
        httpServer.createContext("/",            staticHandler);

        httpServer.setExecutor(Executors.newCachedThreadPool());

        LOG.log(Level.INFO, "Admin UI configured on port " + adminPort);
    }

    /** Starts the HTTP server. */
    public void start() {
        httpServer.start();
        LOG.log(Level.INFO, "Admin UI started on http://"
                + httpServer.getAddress().getHostString()
                + ":" + httpServer.getAddress().getPort() + "/");
    }

    /** Stops the HTTP server and releases resources. */
    public void shutdown() {
        LOG.log(Level.INFO, "Shutting down Admin UI...");
        httpServer.stop(2);
        sessionManager.shutdown();
        try {
            mysql.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error closing Admin UI MySQL connection: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int getConfigInt(String key, int defaultValue) {
        String value = TS3DNSCluster.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
