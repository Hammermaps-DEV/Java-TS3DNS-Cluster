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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages HTTP session tokens for the Admin UI.
 * Sessions expire after 30 minutes of inactivity.
 */
public class SessionManager {

    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public SessionManager() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "admin-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
    }

    /** Creates a new session and returns the session token. */
    public String createSession() {
        String token = UUID.randomUUID().toString();
        sessions.put(token, System.currentTimeMillis());
        return token;
    }

    /**
     * Checks whether the token is valid.
     * Refreshes the session TTL on successful validation.
     */
    public boolean isValid(String token) {
        if (token == null || !sessions.containsKey(token)) {
            return false;
        }
        long timestamp = sessions.get(token);
        if (System.currentTimeMillis() - timestamp > SESSION_TIMEOUT_MS) {
            sessions.remove(token);
            return false;
        }
        sessions.put(token, System.currentTimeMillis()); // refresh TTL
        return true;
    }

    /** Invalidates/removes a session token. */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Shuts down the background cleanup thread. */
    public void shutdown() {
        scheduler.shutdown();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> now - e.getValue() > SESSION_TIMEOUT_MS);
    }
}
