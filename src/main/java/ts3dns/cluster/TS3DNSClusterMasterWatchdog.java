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
package ts3dns.cluster;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.core.error.DocumentNotFoundException;
import java.util.Map;
import java.util.logging.Level;
import ts3dns.database.MySQLDatabaseHandler;
import ts3dns.server.TS3DNSServer;

/**
 * Master/Master failover watchdog.
 *
 * <p>Every master node runs this thread. It continuously writes a heartbeat
 * into the shared Couchbase document {@value #MASTERS_DOC} and determines
 * which master should currently be "active" by picking the one with the
 * <em>lowest {@code machine_id}</em> whose heartbeat is still fresh.
 *
 * <p>When this node wins the election it promotes itself to active master and
 * starts {@link TS3DNSServer} (TS3 availability checker) and
 * {@link TS3DNSClusterMaster} (slave watcher). When another node with a lower
 * {@code machine_id} appears (e.g. a crashed master coming back online) this
 * node demotes itself and stops those threads so that only one active master
 * runs at a time.
 */
public class TS3DNSClusterMasterWatchdog extends Thread {

    /** Couchbase document key that stores all master heartbeats. */
    public static final String MASTERS_DOC = "masters";

    /** Fallback heartbeat-expiry timeout in seconds. */
    private static final int DEFAULT_HEARTBEAT_TIMEOUT = 5;

    private final Collection bucket;
    private final String cb_ma_table;
    private final TS3DNSClusterServer common;
    private final MySQLDatabaseHandler mysql;

    /** Key of this node inside the masters document, e.g. {@code "master_2"}. */
    private final String myKey;

    private boolean isActive = false;

    // Sub-threads managed by this watchdog (non-null only while this node is active)
    private TS3DNSServer lvserver = null;
    private TS3DNSClusterMaster lvmaster = null;

    public TS3DNSClusterMasterWatchdog(Collection bucket, String cb_ma_table,
                                       TS3DNSClusterServer common, MySQLDatabaseHandler mysql) {
        this.bucket = bucket;
        this.cb_ma_table = cb_ma_table;
        this.common = common;
        this.mysql = mysql;
        this.myKey = "master_" + TS3DNSClusterServer.machine_id;
    }

    @Override
    public void run() {
        TS3DNSCluster.log(getClass().getName(), Level.INFO,
                "Master Watchdog started for machine-id " + TS3DNSClusterServer.machine_id, false);

        while (!Thread.currentThread().isInterrupted()) {
            int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);

            // 1. Publish own heartbeat so other nodes can see us
            writeHeartbeat(currentTimestamp);

            // 2. Determine which master should be active (lowest machine_id with fresh heartbeat)
            int activeMachineId = findActiveMasterMachineId();
            boolean shouldBeActive = (activeMachineId == TS3DNSClusterServer.machine_id);

            if (shouldBeActive && !isActive) {
                promoteToActive();
            } else if (!shouldBeActive && isActive) {
                demoteToIdle(activeMachineId);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Clean up before exiting
        if (isActive) {
            demoteToIdle(-1);
        }
        removeHeartbeat();

        TS3DNSCluster.log(getClass().getName(), Level.INFO,
                "Master Watchdog stopped for machine-id " + TS3DNSClusterServer.machine_id, false);
    }

    /** Writes (or updates) this node's heartbeat entry in the masters document. */
    private void writeHeartbeat(int timestamp) {
        try {
            GetResult found = bucket.get(MASTERS_DOC);
            JsonObject content = found.contentAsObject();
            JsonObject myData = JsonObject.create()
                    .put("time", timestamp)
                    .put("ip", TS3DNSCluster.getProperty("default_server_ip"))
                    .put("port", TS3DNSCluster.getProperty("default_server_port"));
            content.put(myKey, myData);
            bucket.replace(MASTERS_DOC, content);
        } catch (DocumentNotFoundException e) {
            TS3DNSCluster.log(getClass().getName(), Level.WARNING,
                    "Masters document not found while writing heartbeat", false);
        }
    }

    /** Removes this node's heartbeat entry from the masters document on clean shutdown. */
    private void removeHeartbeat() {
        try {
            GetResult found = bucket.get(MASTERS_DOC);
            JsonObject content = found.contentAsObject();
            content.removeKey(myKey);
            bucket.replace(MASTERS_DOC, content);
        } catch (DocumentNotFoundException e) {
            // nothing to remove
        }
    }

    /**
     * Scans the masters document and returns the {@code machine_id} of the node
     * that should be the active master: the one with the lowest {@code machine_id}
     * whose heartbeat is still within the configured timeout window.
     *
     * @return the winning {@code machine_id}, or {@code -1} if no fresh heartbeat
     *         exists at all (can happen briefly on first startup before this node
     *         writes its own heartbeat)
     */
    private int findActiveMasterMachineId() {
        int timeout = DEFAULT_HEARTBEAT_TIMEOUT;
        String configTimeout = TS3DNSCluster.getProperty("default_master_failover_timeout");
        if (configTimeout != null && !configTimeout.isEmpty()) {
            try { timeout = Integer.parseInt(configTimeout); } catch (NumberFormatException ignored) {}
        }

        int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
        int lowestMachineId = Integer.MAX_VALUE;

        try {
            GetResult found = bucket.get(MASTERS_DOC);
            JsonObject content = found.contentAsObject();
            Map<String, Object> masters = content.toMap();

            for (Map.Entry<String, Object> entry : masters.entrySet()) {
                String key = entry.getKey();
                if ("null".equals(key) || key.trim().isEmpty()) continue;
                if (!key.startsWith("master_")) continue;

                Object value = entry.getValue();
                if (!(value instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) value;

                Object timeObj = data.get("time");
                if (timeObj == null) continue;

                int time = Integer.parseInt(timeObj.toString());
                if ((time + timeout) > currentTimestamp) {
                    try {
                        int machineId = Integer.parseInt(key.substring("master_".length()));
                        if (machineId < lowestMachineId) {
                            lowestMachineId = machineId;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (DocumentNotFoundException e) {
            TS3DNSCluster.log(getClass().getName(), Level.WARNING,
                    "Masters document not found while reading heartbeats", false);
        }

        return lowestMachineId == Integer.MAX_VALUE ? -1 : lowestMachineId;
    }

    /** Transitions this node to active master: starts TS3DNSServer and TS3DNSClusterMaster. */
    private void promoteToActive() {
        isActive = true;
        TS3DNSCluster.log(getClass().getName(), Level.INFO,
                "Master node " + TS3DNSClusterServer.machine_id + " promoted to ACTIVE master!", false);

        // Start TS3 server availability checker
        lvserver = new TS3DNSServer(common, mysql);
        lvserver.start();
        TS3DNSCluster.log(getClass().getName(), Level.INFO,
                "Teamspeak 3 - DNS Master Server started!", false);

        // Start slave watcher
        lvmaster = new TS3DNSClusterMaster();
        lvmaster.bucket = bucket;
        lvmaster.cb_ma_table = cb_ma_table;
        lvmaster.start();
        TS3DNSCluster.log(getClass().getName(), Level.INFO,
                "Master Slave-Checker started!", false);

        // Expose references so TS3DNSClusterServer.shutdown() can reach them
        common.lvmaster = lvmaster;
        common.lvserver = lvserver;
    }

    /**
     * Transitions this node to idle: stops TS3DNSServer and TS3DNSClusterMaster.
     *
     * @param newActiveMachineId the machine_id of the new active master, or -1 on shutdown
     */
    private void demoteToIdle(int newActiveMachineId) {
        isActive = false;
        if (newActiveMachineId >= 0) {
            TS3DNSCluster.log(getClass().getName(), Level.INFO,
                    "Master node " + TS3DNSClusterServer.machine_id
                    + " demoted to IDLE (active master is machine-id " + newActiveMachineId + ").", false);
        } else {
            TS3DNSCluster.log(getClass().getName(), Level.INFO,
                    "Master node " + TS3DNSClusterServer.machine_id + " demoted to IDLE.", false);
        }

        if (lvserver != null) {
            lvserver.interrupt();
            lvserver = null;
        }
        if (lvmaster != null) {
            lvmaster.interrupt();
            lvmaster = null;
        }
        common.lvmaster = null;
        common.lvserver = null;
    }

    /** Gracefully stops this watchdog thread. */
    public void shutdown() {
        interrupt();
    }
}
