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
package ts3dns.server;

import ts3dns.cluster.TS3DNSClusterPing;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.core.error.DocumentNotFoundException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import ts3dns.cluster.TS3DNSCluster;
import ts3dns.cluster.TS3DNSClusterServer;
import ts3dns.database.MySQLDatabaseHandler;

public class TS3DNSServer extends Thread {
    private final MySQLDatabaseHandler mysql;
    public static Collection bucket = null;
    public static String cb_ma_table = "";
    public TS3DNSClusterServer common = null;
    
    public TS3DNSServer(TS3DNSClusterServer common, MySQLDatabaseHandler mysql) {
        TS3DNSCluster.log(TS3DNSServer.class.getName(), Level.INFO,
                "Start Teamspeak 3 - DNS Master Server", false);
        this.mysql = mysql;
        this.common = common;
    }

    @Override
    public void run() {
        // B-6: use isInterrupted() instead of while(true) for clean shutdown support
        while(!Thread.currentThread().isInterrupted()) {
            //Get all Servers
            String query = "SELECT `ip`,`port`,`id`,`username`,`password` FROM `servers`;";
            // B-2/B-3: use try-with-resources; no redundant stmt.close()
            try (PreparedStatement stmt = mysql.prepare(query);
                 ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    String ip = rs.getString("ip");
                    int port = rs.getInt("port");
                    int id = rs.getInt("id");
                    String pw = rs.getString("password");
                    String user = rs.getString("username");
                   
                    TS3DNSClusterPing ping = new TS3DNSClusterPing(this.common, mysql, ip, port, id, user, pw);
                    if(!TS3DNSServer.existsMaster("sid_" + id)) {
                        TS3DNSServer.setMaster("sid_" + id, 0);
                    }

                    ping.start();
                    try {
                        Thread.sleep(4000); //8 Sek
                    } catch (InterruptedException ex) {
                        // B-6: restore interrupt flag and stop the loop
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (SQLException ex) {
                TS3DNSCluster.log(TS3DNSServer.class.getName(), Level.SEVERE, ex.getMessage(), true);
            }
        }    
    }
    
    //Master-Server Functions
    // Q-1: use typed Map
    public static Map<String, Object> getMaster(String sid) { //Server
        if(TS3DNSClusterServer.cb_enabled) {
            Map<String, Object> map = new HashMap<>();
            try {
                GetResult found = TS3DNSServer.bucket.get(TS3DNSServer.cb_ma_table);
                if(!found.contentAsObject().containsKey(sid)) { return map; }
                JsonObject content = found.contentAsObject().getObject(sid);
                map = content.toMap();
            } catch (DocumentNotFoundException e) {
                // document not found, return empty map
            }
            return map;
        }
        
        return new HashMap<>();
    }
    
    // Q-1: use typed Map
    public static Map<String, Object> getSlave(String sid) { //Client
        if(TS3DNSClusterServer.cb_enabled) {
            Map<String, Object> map = new HashMap<>();
            try {
                GetResult found = TS3DNSServer.bucket.get("slaves");
                if(!found.contentAsObject().containsKey(sid)) { return map; }
                JsonObject content = found.contentAsObject().getObject(sid);
                map = content.toMap();
            } catch (DocumentNotFoundException e) {
                // document not found, return empty map
            }
            return map;
        }
        
        return new HashMap<>();
    }
    
    public static boolean existsMaster(String sid) { //Server
        if(TS3DNSClusterServer.cb_enabled) {
            try {
                GetResult found = TS3DNSServer.bucket.get(TS3DNSServer.cb_ma_table);
                if(!found.contentAsObject().containsKey(sid)) { return false; }
                JsonObject content = found.contentAsObject().getObject(sid);
                Map<String, Object> data = content.toMap();
                return data != null;
            } catch (DocumentNotFoundException e) {
                return false;
            }
        }
        
        return false;
    }
    
    public static boolean existsSlave(String sid) { //Client
        if(TS3DNSClusterServer.cb_enabled) {
            try {
                GetResult found = TS3DNSServer.bucket.get("slaves");
                if(!found.contentAsObject().containsKey(sid)) { return false; }
                JsonObject content = found.contentAsObject().getObject(sid);
                Map<String, Object> data = content.toMap();
                return data != null;
            } catch (DocumentNotFoundException e) {
                return false;
            }
        }
        
        return false;
    }
    
    public static void setMaster(String sid, int online) { //Server
        if(TS3DNSClusterServer.cb_enabled) {
            Map<String, Object> data = new HashMap<>();
            data.put("online", online);
            try {
                GetResult found = TS3DNSServer.bucket.get(TS3DNSServer.cb_ma_table);
                JsonObject content = found.contentAsObject();
                content.put(sid, data);
                bucket.replace(TS3DNSServer.cb_ma_table, content);
            } catch (DocumentNotFoundException e) {
                // document not found, cannot update
            }
        }
    }
    
    public static void setSlave(String sid, int time) { //Client
        if(TS3DNSClusterServer.cb_enabled) {
            Map<String, Object> data = new HashMap<>();
            data.put("time", time);
            data.put("ip", TS3DNSCluster.getProperty("default_server_ip"));
            data.put("port", TS3DNSCluster.getProperty("default_server_port"));
            try {
                GetResult found = TS3DNSServer.bucket.get("slaves");
                JsonObject content = found.contentAsObject();
                content.put(sid, data);
                bucket.replace("slaves", content);
            } catch (DocumentNotFoundException e) {
                // document not found, cannot update
            }
        }
    }
}