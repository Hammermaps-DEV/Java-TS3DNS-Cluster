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

import com.sun.net.httpserver.HttpExchange;
import ts3dns.admin.SessionManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Serves static files (HTML, JS, CSS) from the classpath under /admin/.
 * Maps URL paths starting with the configured prefix to classpath resources.
 *
 * <p>Example: GET /index.html → classpath:/admin/index.html</p>
 */
public class StaticFileHandler extends BaseHandler {

    private final String urlPrefix;

    public StaticFileHandler(SessionManager sessionManager, String urlPrefix) {
        super(sessionManager);
        this.urlPrefix = urlPrefix;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            sendMethodNotAllowed(exchange);
            return;
        }

        URI uri = exchange.getRequestURI();
        String requestPath = uri.getPath();

        // Redirect root to index.html
        if ("/".equals(requestPath) || requestPath.isEmpty()) {
            if (isAuthenticated(exchange)) {
                sendRedirect(exchange, "/index.html");
            } else {
                sendRedirect(exchange, "/login.html");
            }
            return;
        }

        // Protect index.html – require authentication
        if ("/index.html".equals(requestPath) && !isAuthenticated(exchange)) {
            sendRedirect(exchange, "/login.html");
            return;
        }

        // Resolve to classpath resource
        String resourcePath = "/admin" + requestPath;

        // Security: prevent path traversal
        if (resourcePath.contains("..") || resourcePath.contains("//")) {
            sendError(exchange, 400, "Bad Request");
            return;
        }

        try (InputStream is = StaticFileHandler.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                byte[] notFound = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, notFound.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound);
                }
                return;
            }

            String contentType = getContentType(requestPath);
            byte[] data = readStream(is);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, data.length);

            if ("GET".equalsIgnoreCase(method)) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            } else {
                exchange.close();
            }
        }
    }

    private byte[] readStream(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=UTF-8";
        if (path.endsWith(".css"))  return "text/css; charset=UTF-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".ico"))  return "image/x-icon";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        return "application/octet-stream";
    }
}
