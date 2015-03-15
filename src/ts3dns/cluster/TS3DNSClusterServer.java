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
 * Version: 1.0 Alpha
 * Autor: Hammermaps.de Development Team
 * Homepage: http://www.hammermaps.de
 */

package ts3dns.cluster;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import ts3dns.client.TS3DNSClient;
import static ts3dns.cluster.TS3DNSCluster.properties;
import ts3dns.database.MySQLDatabaseHandler;
import ts3dns.server.TS3DNSServer;

public class TS3DNSClusterServer {
    private ServerSocket server;
    private java.net.Socket client;
    private TS3DNSClient tS3DNSClient;
    private MySQLDatabaseHandler mysql;
    private String default_ip;
    private String default_port;
    private String failback_ip;
    private String failback_port;
    private String failback;
    private static final Map cache = new HashMap();
    private static CouchbaseCluster cluster = null;
    private static Bucket bucket = null;
    private static JsonDocument found = null;
    private static String cb_table = "";
    private static String cb_ma_table = "";
    private static String cb_bucket = "";
    private static int port = 41144;
    private static boolean cb_enabled = false;
    private static boolean is_master = false;
    private static boolean is_slave = false;
    public static int machine_id = 0;
    public TS3DNSServer lvserver = null;
    
    public TS3DNSClusterServer() {
        try {
            is_master = Boolean.parseBoolean(TS3DNSCluster.properties.getProperty("default_master_server"));
            is_slave = Boolean.parseBoolean(TS3DNSCluster.properties.getProperty("default_slave_server"));
            cb_enabled = Boolean.parseBoolean(TS3DNSCluster.properties.getProperty("couchbase_enable")); //Couchbase Cluster
            port = Integer.parseInt(TS3DNSCluster.properties.getProperty("default_server_port"));
            machine_id = Integer.parseInt(TS3DNSCluster.properties.getProperty("default_machine_id"));
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Server has Machine-ID: ")).append(Integer.toString(machine_id)).toString(),false);
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Waiting for connections on port ")).append(Integer.toString(port)).toString(),false);
            if(!TS3DNSCluster.properties.getProperty("default_server_ip").matches("0.0.0.0")) {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Server Listen on IP: ")).append(TS3DNSCluster.properties.getProperty("default_server_ip")).toString(),false);
                server = new ServerSocket();
                server.bind(new InetSocketAddress(TS3DNSCluster.properties.getProperty("default_server_ip"), port));
            } else {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Server Listen on all IPs")).toString(),false);
                server = new ServerSocket(port);
            }
            
            server.setSoTimeout(1000); // Set Timeout
            String hostname = TS3DNSCluster.properties.getProperty("mysql_host");
            String username = TS3DNSCluster.properties.getProperty("mysql_user");
            String password = TS3DNSCluster.properties.getProperty("mysql_pass");
            String database = TS3DNSCluster.properties.getProperty("mysql_db");
            int sql_port = Integer.parseInt(TS3DNSCluster.properties.getProperty("mysql_port"));
            mysql = new MySQLDatabaseHandler(hostname,sql_port,username,password,database);
            default_ip = TS3DNSCluster.properties.getProperty("default_ip_for_dns");
            default_port = TS3DNSCluster.properties.getProperty("default_port_for_dns");
            
            //Couchbase Cluster
            if(cb_enabled) {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Couchbase Server Support is enabled!")).toString(),false);
                String cb_host = TS3DNSCluster.properties.getProperty("couchbase_host");
                cb_bucket = TS3DNSCluster.properties.getProperty("couchbase_bucket");
                cb_table = TS3DNSCluster.properties.getProperty("couchbase_table");
                cb_ma_table = TS3DNSCluster.properties.getProperty("couchbase_master_table");
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Connect to Couchbase Server: '")).append(cb_host).append("'").toString(),false);
                cluster = CouchbaseCluster.create(cb_host);
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Use Couchbase Server Bucket: '")).append(cb_bucket).append("'").toString(),false);
                bucket = cluster.openBucket(cb_bucket);
                JsonObject content = JsonObject.create().put("null", "null");
                if(bucket.get(cb_table) == null) {
                    bucket.insert(JsonDocument.create(cb_table, content));
                }
            } else {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Couchbase Server Support is disabled!")).toString(),false);
            }
        } catch (IOException ex) {
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.SEVERE,ex.getMessage(),true);
        }
    }
    
    public void start() throws SQLException {
        //Set default IP
        String query = "SELECT `dns`,`ip`,`port`,`server-id`,`failback_ip`,`failback_port`,`failback` FROM `dns` WHERE `default` = 1 AND (`machine-id` = 0 OR `machine-id` = ?) LIMIT 1;";
        ResultSet rs;
        try (PreparedStatement stmt = this.mysql.prepare(query)) {
            stmt.setInt(1, TS3DNSClusterServer.machine_id);
            rs = stmt.executeQuery();
            if(rs.next()) { 
                default_ip = rs.getString("ip");
                default_port = rs.getString("port");
                failback_ip = rs.getString("failback_ip");
                failback_port = rs.getString("failback_port");
                failback = rs.getString("failback");
                TS3DNSClusterServer.setCache(rs.getString("dns"), default_ip, default_port, rs.getString("server-id"), failback_ip, failback_port, failback);
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Set default IP: ")).
                append(default_ip).append(":").append(default_port).append(" for DNS Lookup").toString(),false);
            }
            stmt.close(); rs.close();
        }
        
        if((is_master || is_slave) && !cb_enabled) {
            TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Couchbase Server Support is disabled, no Master-Server Support!")).toString(),false);
        }
        
        if((is_master || is_slave) && cb_enabled) {
            bucket = cluster.openBucket(cb_bucket);
            JsonObject content = JsonObject.create().put("null", "null");
            if(bucket.get(cb_ma_table) == null) {
                bucket.insert(JsonDocument.create(cb_ma_table, content));
            }
            
            //Start Teamspeak 3 Server checker
            if(is_slave) {
                TS3DNSServer.cb_ma_table = cb_ma_table;
                TS3DNSServer.bucket = bucket;
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Teamspeak 3 - DNS Slave Server started!")).toString(),false);
            } else {
                TS3DNSServer.cb_ma_table = cb_ma_table;
                TS3DNSServer.bucket = bucket;
                lvserver = new TS3DNSServer(this.mysql); lvserver.start();
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Teamspeak 3 - DNS Master Server started!")).toString(),false);
            }
        }
        
        while(!Thread.currentThread().isInterrupted()) {
            try {
                client = server.accept();
                if(client.isConnected()) {
                    if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                        TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Connected ")).append(client.getInetAddress()).toString(),false);
                    }
                    client.setSoTimeout(3000); //Set Client Timeout
                    tS3DNSClient = new TS3DNSClient(client,mysql,default_ip,default_port);
                    tS3DNSClient.start();
                }
            }
            catch(SocketTimeoutException ex) {} catch (IOException ex) {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.SEVERE,ex.getMessage(),true);
            }
        }
        
        cluster.disconnect();
    }
    
    //Cache Functions
    public static Map getCache(String key) {
        return (Map)cache.get(key);
    }
    
    public static boolean existsCache(String key) {
        //Check Couchbase Cluster
        if(!cache.containsKey(key)) {
            if(cb_enabled) {
                found = bucket.get(cb_table);
                if(found == null) { return false; }
                if(!found.content().containsKey(key)) { return false; }
                JsonObject content = found.content().getObject(key);
                Map data = content.toMap();
                if(data == null) { return false; }
                if(Integer.parseInt(data.get("mid").toString()) == 0 || Integer.parseInt(data.get("mid").toString()) == machine_id) {
                    if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                        TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Get TSDNS IP from Couchbase Cluster: ")).
                        append(data.get("ip").toString()).append(":").append(data.get("port").toString()).toString(),false);
                    }
                    
                    TS3DNSClusterServer.setCache(key,data.get("ip").toString(),data.get("port").toString(),data.get("sid").toString(),data.get("fback_ip").toString(),data.get("fback_port").toString(),data.get("fback").toString());
                }
            }
            
            return cache.containsKey(key);
        }
        
        Map data = (Map)cache.get(key);
        if(data == null) { return false; }
        int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
        if(((int)data.get("time")+5) <= currentTimestamp) return false;
        return cache.containsKey(key);
    }
    
    public static void setCache(String key, String ip, String port, String sid, String fback_ip, String fback_port, String failback ) {
        int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
        Map data = new HashMap();
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
            found = bucket.get(cb_table);
            JsonObject content = found.content();
            content.put(key, data);
            if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSClusterServer.class.getName(), Level.INFO,(new StringBuilder("Update TSDNS to Couchbase Cluster: Update -> ")).
                append(ip).append(":").append(port).toString(),false);
            }
            bucket.replace(JsonDocument.create(cb_table, content));
        }
        
        if(cache.containsKey(key)) {
            cache.remove(key);
        }
        
        cache.put(key, data);
    }
}
