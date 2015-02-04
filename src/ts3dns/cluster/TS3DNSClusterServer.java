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

import java.io.IOException;
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
    
    public TS3DNSClusterServer() {
        try {
            Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Waiting for connections on port ")).append(Integer.toString(TS3DNSCluster.port)).toString());
            server = new ServerSocket(TS3DNSCluster.port); //Listen Server
            server.setSoTimeout(1000); // Set Timeout
            String hostname = TS3DNSCluster.properties.getProperty("mysql_host");
            String username = TS3DNSCluster.properties.getProperty("mysql_user");
            String password = TS3DNSCluster.properties.getProperty("mysql_pass");
            String database = TS3DNSCluster.properties.getProperty("mysql_db");
            int port = Integer.parseInt(TS3DNSCluster.properties.getProperty("mysql_port"));
            mysql = new MySQLDatabaseHandler(hostname,port,username,password,database);
            default_ip = "000.000.000.000:0000";
        } catch (IOException ex) {
            Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void start() throws SQLException {
        //Set default IP
        String query = "SELECT `ip` FROM `dns` WHERE `default` = 1 LIMIT 1;";
        ResultSet rs;
        try (PreparedStatement stmt = this.mysql.prepare(query)) {
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
    }
    
    //Cache Functions
    public static String getCache(String key) {
        Map data = (Map)cache.get(key);
        return (String)data.get("data").toString();
    }
    
    public static boolean existsCache(String key) {
        if(!cache.containsKey(key)) return false;
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
        if(cache.containsKey(key)) {
            cache.remove(key);
        }
        
        cache.put(key, data);
    }
}
