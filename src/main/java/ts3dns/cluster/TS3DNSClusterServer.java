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

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.client.TS3DNSClient;
import ts3dns.client.TS3Updater;
import ts3dns.database.MySQLDatabaseHandler;
import ts3dns.server.TS3DNSServer;

public class TS3DNSClusterServer {
    private ServerSocket server;
    private java.net.Socket client;
    private MySQLDatabaseHandler mysql;
    private String default_ip;
    private String default_port;
    private String failback_ip;
    private String failback_port;
    private String failback;
    // S-3: use ConcurrentHashMap to avoid race conditions under concurrent access
    private static final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private static Cluster cluster = null;
    private static Collection bucket = null;
    private static String cb_table;
    private static String cb_ma_table;
    private static String cb_bucket;
    private static String cb_username;
    private static String cb_password;
    public static int cb_machine_id = 0;
    public static int port = 41144;
    public static boolean cb_enabled = false;
    private static boolean is_master = false;
    private static boolean is_slave = false;
    public static int machine_id = 0;
    public TS3DNSServer lvserver = null;
    public TS3DNSClusterMaster lvmaster = null;
    public TS3DNSClusterSlave lvslaveserver = null;
    public TS3DNSClusterMasterWatchdog lvmasterwatchdog = null;
    // S-3: use ConcurrentHashMap to avoid race conditions
    public static Map<Integer, Integer> lock_update = new ConcurrentHashMap<>();
    // P-1: thread pool instead of creating a new OS thread per connection
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    
    public TS3DNSClusterServer() {
        try {
            is_master = Boolean.parseBoolean(TS3DNSCluster.getProperty("default_master_server"));
            is_slave = Boolean.parseBoolean(TS3DNSCluster.getProperty("default_slave_server"));
            cb_enabled = Boolean.parseBoolean(TS3DNSCluster.getProperty("couchbase_enable")); //Couchbase Cluster
            port = Integer.parseInt(TS3DNSCluster.getProperty("default_server_port"));
            machine_id = Integer.parseInt(TS3DNSCluster.getProperty("default_machine_id"));
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                    "Server has Machine-ID: " + machine_id, false);
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                    "Waiting for connections on port " + port, false);
            
            if(!TS3DNSCluster.getProperty("default_server_ip").matches("0.0.0.0")) {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                        "Server Listen on IP: " + TS3DNSCluster.getProperty("default_server_ip"), false);
                server = new ServerSocket();
                server.bind(new InetSocketAddress(TS3DNSCluster.getProperty("default_server_ip"), port));
            } else {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                        "Server Listen on all IPs", false);
                server = new ServerSocket(port);
            }
            
            server.setSoTimeout(1000); // Set Timeout
            String hostname = TS3DNSCluster.getProperty("mysql_host");
            String username = TS3DNSCluster.getProperty("mysql_user");
            String password = TS3DNSCluster.getProperty("mysql_pass");
            String database = TS3DNSCluster.getProperty("mysql_db");
            int sql_port = Integer.parseInt(TS3DNSCluster.getProperty("mysql_port"));
            mysql = new MySQLDatabaseHandler(hostname, sql_port, username, password, database);
            default_ip = TS3DNSCluster.getProperty("default_ip_for_dns");
            default_port = TS3DNSCluster.getProperty("default_port_for_dns");

            // Initialise flood / rate-limit protection
            FloodProtection.getInstance().init(mysql);
            
            //Couchbase Cluster
            if(cb_enabled) {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                        "Couchbase Server Support is enabled!", false);
                String cb_host = TS3DNSCluster.getProperty("couchbase_host");
                cb_bucket = TS3DNSCluster.getProperty("couchbase_bucket");
                cb_table = TS3DNSCluster.getProperty("couchbase_table");
                cb_ma_table = TS3DNSCluster.getProperty("couchbase_master_table");
                cb_machine_id = Integer.parseInt(TS3DNSCluster.getProperty("couchbase_machine_id"));
                cb_username = TS3DNSCluster.getProperty("couchbase_username");
                cb_password = TS3DNSCluster.getProperty("couchbase_password");
                
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                        "Connect to Couchbase Server: '" + cb_host + "'", false);
                cluster = Cluster.connect(cb_host, cb_username, cb_password);
                
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                        "Use Couchbase Server Bucket: '" + cb_bucket + "'", false);
                bucket = cluster.bucket(cb_bucket).defaultCollection();

                JsonObject content = JsonObject.create().put("null", "null");
                if(!bucket.exists(cb_table).exists()) {
                    bucket.insert(cb_table, content);
                }
            } else {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                        "Couchbase Server Support is disabled!", false);
            }
        } catch (IOException ex) {
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.SEVERE, ex.getMessage(), true);
        }
    }
    
    public void start() throws SQLException {
        //Set default IP
        String query = "SELECT `dns`,`ip`,`port`,`server-id`,`failback_ip`,`failback_port`,`failback` FROM `dns` WHERE `default` = 1 AND (`machine-id` = 0 OR `machine-id` = ?) LIMIT 1;";
        // B-2: use try-with-resources for ResultSet
        try (PreparedStatement stmt = this.mysql.prepare(query)) {
            stmt.setInt(1, TS3DNSClusterServer.machine_id);
            try (ResultSet rs = stmt.executeQuery()) {
                if(rs.next()) { 
                    default_ip = rs.getString("ip");
                    default_port = rs.getString("port");
                    failback_ip = rs.getString("failback_ip");
                    failback_port = rs.getString("failback_port");
                    failback = rs.getString("failback");
                    TS3DNSClusterServer.setCache(rs.getString("dns"), default_ip, default_port,
                            rs.getString("server-id"), failback_ip, failback_port, failback);
                    TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                            "Set default IP: " + default_ip + ":" + default_port + " for DNS Lookup", false);
                }
            }
        }
        
        if((is_master || is_slave) && !cb_enabled) {
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                    "Couchbase Server Support is disabled, no Master-Server Support!", false);
        }
        
        if((is_master || is_slave) && cb_enabled) {
            bucket = cluster.bucket(cb_bucket).defaultCollection();
            JsonObject content = JsonObject.create().put("null", "null");
            //Master Table
            if(!bucket.exists(cb_ma_table).exists()) {
                bucket.insert(cb_ma_table, content);
            }
            
            //Start Teamspeak 3 Server checker
            if(is_slave) {
                TS3DNSServer.cb_ma_table = cb_ma_table;
                TS3DNSServer.bucket = bucket;
                lvslaveserver = new TS3DNSClusterSlave();
                lvslaveserver.start();
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                        "Teamspeak 3 - DNS Slave Server started!", false);
            } else {
                TS3DNSServer.cb_ma_table = cb_ma_table;
                TS3DNSServer.bucket = bucket;
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                        "Teamspeak 3 - DNS Master Watchdog starting!", false);

                // Initialise the shared masters heartbeat document
                if(!bucket.exists(TS3DNSClusterMasterWatchdog.MASTERS_DOC).exists()) {
                    bucket.insert(TS3DNSClusterMasterWatchdog.MASTERS_DOC,
                            JsonObject.create().put("null", "null"));
                }

                // Start watchdog – it owns the TS3DNSServer and TS3DNSClusterMaster lifecycle
                lvmasterwatchdog = new TS3DNSClusterMasterWatchdog(bucket, cb_ma_table, this, this.mysql);
                lvmasterwatchdog.start();
            }
        }
        
        //Status Updates for Database – only start directly when not managed by the master watchdog
        if (!(is_master && cb_enabled)) {
            lvserver = new TS3DNSServer(this, this.mysql); 
            lvserver.start();
        }
        
        while(!Thread.currentThread().isInterrupted()) {
            try {
                client = server.accept();
                if(client.isConnected()) {
                    String clientIp = client.getInetAddress().getHostAddress();
                    if (!FloodProtection.getInstance().isAllowed(clientIp)) {
                        if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                                    "Flood/Ban: blocked connection from " + clientIp, false);
                        }
                        try { client.close(); } catch (IOException ignored) {}
                        continue;
                    }
                    if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                        TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                                "Connected " + client.getInetAddress(), false);
                    }
                    client.setSoTimeout(2000); //Set Client Timeout
                    // P-1: submit to thread pool instead of creating a new thread per connection
                    threadPool.submit(new TS3DNSClient(client, mysql, default_ip, default_port));
                }
            }
            catch(SocketTimeoutException ex) {} catch (IOException ex) {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.SEVERE, ex.getMessage(), true);
            }
        }
    }
    
    // B-5: renamed from stop() to shutdown() to avoid confusion with Thread.stop()
    public void shutdown() {
        TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO, "Stop Server..", false);
        // Stop master watchdog first so it demotes cleanly before Couchbase disconnects
        if (lvmasterwatchdog != null) {
            lvmasterwatchdog.shutdown();
        }
        try {
            mysql.close();
        } catch (SQLException ex) {
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.WARNING,
                    "Error closing MySQL connection: " + ex.getMessage(), false);
        }
        // S-9: guard against NPE when Couchbase is disabled
        if(cluster != null) {
            cluster.disconnect();
        }
        // P-1: shut down the thread pool gracefully
        threadPool.shutdown();
    }
    
    //Cache Functions
    // Q-1: return typed Map
    public static Map<String, Object> getCache(String key) {
        return cache.get(key);
    }
    
    public static boolean existsCache(String key) {
        //Check Couchbase Cluster
        if(!cache.containsKey(key)) {
            if(cb_enabled) {
                GetResult found;
                try {
                    found = bucket.get(cb_table);
                } catch (DocumentNotFoundException e) {
                    return false;
                }
                if(!found.contentAsObject().containsKey(key)) { return false; }
                JsonObject content = found.contentAsObject().getObject(key);
                Map<String, Object> data = content.toMap();
                if(data == null) { return false; }
                if(Integer.parseInt(data.get("mid").toString()) == 0 || Integer.parseInt(data.get("mid").toString()) == machine_id) {
                    if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                        TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                                "Get TSDNS IP from Couchbase Cluster: " + data.get("ip") + ":" + data.get("port"), false);
                    }
                    TS3DNSClusterServer.setCache(key, data.get("ip").toString(), data.get("port").toString(),
                            data.get("sid").toString(), data.get("fback_ip").toString(),
                            data.get("fback_port").toString(), data.get("fback").toString());
                }
            }
            
            return cache.containsKey(key);
        }
        
        Map<String, Object> data = cache.get(key);
        if(data == null) { return false; }
        int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
        // P-4: remove expired cache entries to prevent memory leak
        if(((int)data.get("time") + 10) <= currentTimestamp) {
            cache.remove(key);
            return false;
        }
        return true;
    }
    
    public static void setCache(String key, String ip, String port, String sid, String fback_ip, String fback_port, String failback) {
        int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
        // Q-1: use typed Map
        Map<String, Object> data = new HashMap<>();
        data.put("ip", ip);
        data.put("port", port);
        data.put("sid", sid);
        data.put("fback_ip", fback_ip);
        data.put("fback_port", fback_port);
        data.put("fback", failback);
        data.put("time", currentTimestamp);
        data.put("mid", machine_id);

        //Update Couchbase Cluster
        if(cb_enabled) {
            try {
                GetResult foundResult = bucket.get(cb_table);
                JsonObject content = foundResult.contentAsObject();
                content.put(key, data);
                if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                    TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,
                            "Update TSDNS to Couchbase Cluster: Update -> " + ip + ":" + port, false);
                }
                bucket.replace(cb_table, content);
            } catch (DocumentNotFoundException e) {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.WARNING,
                        "Couchbase document '" + cb_table + "' not found during setCache", false);
            }
        }
        
        cache.put(key, data);
    }
    
    public void sendMSG(String msg) { 
        String query = "SELECT * FROM `servers` WHERE `online` = 1;";
        TS3Query tsquery;
        TS3Api api;
        // B-2: use try-with-resources for ResultSet
        try (PreparedStatement stmt = this.mysql.prepare(query);
             ResultSet rs = stmt.executeQuery()) {
            TS3Config config;
            while(rs.next()) {
                config = new TS3Config();
                config.setHost(rs.getString("ip"));
                config.setQueryPort(Integer.parseInt(rs.getString("port")));
                config.setCommandTimeout(15000);

                if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                    config.setEnableCommunicationsLogging(true);
                }

                tsquery = new TS3Query(config);
                tsquery.connect();

                api = tsquery.getApi();
                api.login(rs.getString("username"), rs.getString("password"));
                api.selectVirtualServerById(1);
                api.broadcast(msg);

                //Logout
                api.logout();
                tsquery.exit();
            }
        } catch (SQLException ex) {
            Logger.getLogger(TS3Updater.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
