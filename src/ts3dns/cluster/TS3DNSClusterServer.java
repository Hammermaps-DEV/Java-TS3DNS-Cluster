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
import java.util.logging.Logger;
import ts3dns.client.TS3DNSClient;
import ts3dns.database.MySQLDatabaseHandler;

public class TS3DNSClusterServer {
    private ServerSocket server;
    private java.net.Socket client;
    private TS3DNSClient tS3DNSClient;
    private MySQLDatabaseHandler mysql;
    private String default_ip;
    private static final Map cache = new HashMap();
    private static CouchbaseCluster cluster = null;
    private static Bucket bucket = null;
    private static JsonDocument found = null;
    private static String cb_table = "";
    private static int port = 41144;
    private static boolean cb_enabled = false;
    public static int machine_id = 0;
    
    public TS3DNSClusterServer() {
        try {
            cb_enabled = Boolean.parseBoolean(TS3DNSCluster.properties.getProperty("couchbase_enable")); //Couchbase Cluster
            port = Integer.parseInt(TS3DNSCluster.properties.getProperty("default_server_port"));
            machine_id = Integer.parseInt(TS3DNSCluster.properties.getProperty("default_machine_id"));
            Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Server has Machine-ID: ")).append(Integer.toString(machine_id)).toString());
            Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Waiting for connections on port ")).append(Integer.toString(port)).toString());
            if(!TS3DNSCluster.properties.getProperty("default_server_ip").matches("0.0.0.0")) {
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Server Listen on IP: ")).append(TS3DNSCluster.properties.getProperty("default_server_ip")).toString());
                server = new ServerSocket();
                server.bind(new InetSocketAddress(TS3DNSCluster.properties.getProperty("default_server_ip"), port));
            } else {
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Server Listen on all IPs")).toString());
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
            
            //Couchbase Cluster
            if(cb_enabled) {
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Couchbase Server Support is enabled!")).toString());
                String cb_host = TS3DNSCluster.properties.getProperty("couchbase_host");
                String cb_bucket = TS3DNSCluster.properties.getProperty("couchbase_bucket");
                cb_table = TS3DNSCluster.properties.getProperty("couchbase_table");
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Connect to Couchbase Server: '")).append(cb_host).append("'").toString());
                cluster = CouchbaseCluster.create(cb_host);
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Use Couchbase Server Bucket: '")).append(cb_bucket).append("'").toString());
                bucket = cluster.openBucket(cb_bucket);
                JsonObject content = JsonObject.create().put("null", "null");
                if(bucket.get("dnscache") == null) {
                    bucket.insert(JsonDocument.create(cb_table, content));
                }
            } else {
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Couchbase Server Support is disabled!")).toString());
            }
        } catch (IOException ex) {
            Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void start() throws SQLException {
        //Set default IP
        String query = "SELECT `ip` FROM `dns` WHERE `default` = 1 AND (`machine-id` = 0 OR `machine-id` = ?) LIMIT 1;";
        ResultSet rs;
        try (PreparedStatement stmt = this.mysql.prepare(query)) {
            stmt.setInt(1, TS3DNSClusterServer.machine_id);
            rs = stmt.executeQuery();
            if(rs.next()) { 
                default_ip = rs.getString("ip");
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Set default IP: ")).append(default_ip).append(" for DNS Lookup").toString());
            }
            stmt.close(); rs.close();
        }
        
        while(!Thread.currentThread().isInterrupted()) {
            try {
                client = server.accept();
                if(client.isConnected()) {
                    Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Connected ")).append(client.getInetAddress()).toString());
                    client.setSoTimeout(3000); //Set Client Timeout
                    tS3DNSClient = new TS3DNSClient(client,mysql,default_ip);
                    tS3DNSClient.start();
                }
            }
            catch(SocketTimeoutException ex) { } 
            catch (IOException ex) {
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        cluster.disconnect();
    }
    
    //Cache Functions
    public static String getCache(String key) {
        Map data = (Map)cache.get(key);
        return (String)data.get("data").toString();
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
                if(Integer.parseInt(data.get("mid").toString()) == 0 || Integer.parseInt(data.get("mid").toString()) == machine_id) {
                    Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Get TSDNS IP from Couchbase Cluster: ")).append(data.get("data").toString()).toString());
                    TS3DNSClusterServer.setCache(key,data.get("data").toString());
                }
            }
            
            return cache.containsKey(key);
        }
        
        Map data = (Map)cache.get(key);
        int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
        if(((int)data.get("time")+5) <= currentTimestamp) return false;
                
        return cache.containsKey(key);
    }
    
    public static void setCache(String key, String input) {
        int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
        Map data = new HashMap();
        data.put("data", input);
        data.put("time", currentTimestamp);
        data.put("mid", machine_id);

        //Update Couchbase Cluster
        if(cb_enabled) {
            found = bucket.get(cb_table);
            JsonObject content = found.content();
            content.put(key, data);
            Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Update TSDNS to Couchbase Cluster: Update -> ")).append(input).toString());
            bucket.replace(JsonDocument.create(cb_table, content));
        }
        
        if(cache.containsKey(key)) {
            cache.remove(key);
        }
        
        cache.put(key, data);
    }
}
