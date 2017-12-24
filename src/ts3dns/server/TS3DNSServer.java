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
package ts3dns.server;

import ts3dns.cluster.TS3DNSClusterPing;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
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
    public static Bucket bucket = null;
    private static JsonDocument found = null;
    public static String cb_ma_table = "";
    
    public TS3DNSServer(MySQLDatabaseHandler mysql) {
        TS3DNSCluster.log(TS3DNSServer.class.getName(), Level.INFO,(new StringBuilder("Start Teamspeak 3 - DNS Master Server")).toString(),false);
        this.mysql = mysql;
    }

    public void run() {
        while (true) {
            //Get all Servers
            String query = "SELECT `ip`,`port`,`id`,`username`,`password` FROM `servers`;";
            try (PreparedStatement stmt = mysql.prepare(query); ResultSet rs = stmt.executeQuery()) {
                while(rs.first() || rs.next()) {
                    String ip = rs.getString("ip");
                    int port = rs.getInt("port");
                    int id = rs.getInt("id");
                    String pw = rs.getString("password");
                    String user = rs.getString("username");
                    TS3DNSClusterPing ping = new TS3DNSClusterPing(mysql,ip,port,id,user,pw);
                    if(!TS3DNSServer.existsMaster((new StringBuilder("sid_").append(id)).toString())) {
                        TS3DNSServer.setMaster((new StringBuilder("sid_").append(id)).toString(), "off");
                    }

                    ping.start();
                    try {
                        Thread.sleep(4000); //8 Sek
                    } catch (InterruptedException ex) { }
                }
                stmt.close(); 
            } catch (SQLException ex) {
                TS3DNSCluster.log(TS3DNSServer.class.getName(), Level.SEVERE,ex.getMessage(),true);
            }
        }    
    }
    
    //Master-Server Functions
    public static Map getMaster(String sid) { //Server
        if(TS3DNSClusterServer.cb_enabled) {
            Map map = new HashMap();
            found = TS3DNSServer.bucket.get(TS3DNSServer.cb_ma_table);
            if(found == null) { return map; }
            if(!found.content().containsKey(sid)) { return map; }
            JsonObject content = found.content().getObject(sid);
            map = content.toMap();
            return map;
        }
        
        return new HashMap();
    }
    
     public static Map getSlave(String sid) { //Client
        if(TS3DNSClusterServer.cb_enabled) {
            Map map = new HashMap();
            found = TS3DNSServer.bucket.get("slaves");
            if(found == null) { return map; }
            if(!found.content().containsKey(sid)) { return map; }
            JsonObject content = found.content().getObject(sid);
            map = content.toMap();
            return map;
        }
        
        return new HashMap();
    }
    
    public static boolean existsMaster(String sid) { //Server
        if(TS3DNSClusterServer.cb_enabled) {
            found = TS3DNSServer.bucket.get(TS3DNSServer.cb_ma_table);
            if(found == null) { return false; }
            if(!found.content().containsKey(sid)) { return false; }
            JsonObject content = found.content().getObject(sid);
            Map data = content.toMap();
            return data != null;
        }
        
        return false;
    }
    
    public static boolean existsSlave(String sid) { //Client
        if(TS3DNSClusterServer.cb_enabled) {
            found = TS3DNSServer.bucket.get("slaves");
            if(found == null) { return false; }
            if(!found.content().containsKey(sid)) { return false; }
            JsonObject content = found.content().getObject(sid);
            Map data = content.toMap();
            return data != null;
        }
        
        return false;
    }
    
    public static void setMaster(String sid, String online) { //Server
        if(TS3DNSClusterServer.cb_enabled) {
            Map data = new HashMap();
            data.put("online", online);
            found = TS3DNSServer.bucket.get(TS3DNSServer.cb_ma_table);
            if(found != null) { 
                JsonObject content = found.content();
                content.put(sid, data);
                bucket.replace(JsonDocument.create(TS3DNSServer.cb_ma_table, content));
            }
        }
    }
    
    public static void setSlave(String sid, int time) { //Client
        if(TS3DNSClusterServer.cb_enabled) {
            Map data = new HashMap();
            data.put("time", time);
            data.put("ip", TS3DNSCluster.properties.getProperty("default_server_ip"));
            data.put("port", TS3DNSCluster.properties.getProperty("default_server_port"));
            found = TS3DNSServer.bucket.get("slaves");
            if(found != null) { 
                JsonObject content = found.content();
                content.put(sid.toString(), data);
                bucket.replace(JsonDocument.create("slaves", content));
            }
        }
    }
}