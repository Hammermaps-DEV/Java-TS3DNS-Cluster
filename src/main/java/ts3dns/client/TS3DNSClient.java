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

package ts3dns.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.cluster.TS3DNSCluster;
import ts3dns.cluster.TS3DNSClusterServer;
import ts3dns.database.MySQLDatabaseHandler;
import ts3dns.server.TS3DNSServer;

public class TS3DNSClient extends Thread {
    private final Socket client;
    private BufferedReader input;
    private PrintStream output;
    private String search_dns;
    private final MySQLDatabaseHandler mysql;
    private String query;
    private String ip;
    private String port;
    private String sid;
    private String default_ip;
    private String default_port;
    private String failback_ip;
    private String failback_port;
    private String failback;

    public TS3DNSClient(Socket client, MySQLDatabaseHandler mysql, String default_ip, String default_port) {
        this.client = client;
        this.mysql = mysql;
        this.search_dns = "";
        this.input = null;
        this.output = null;
        this.default_ip = default_ip;
        this.default_port = default_port;
        this.ip = default_ip;
        this.port = default_port;
    }

    @Override
    public void run() {
        // B-1: use try-with-resources to guarantee socket/stream closure
        try (Socket sock = this.client) {
            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,
                        "Get connection from: " + sock.getInetAddress().getHostAddress(), false);
            }
            
            //Get DNS
            sock.setSoTimeout(50); //Set Timeout
            input = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
            search_dns = readUntilEnd();

            // S-4: validate DNS name format; S-5 length already enforced in readUntilEnd()
            if(search_dns.length() <= 3 || !search_dns.matches("^[a-zA-Z0-9][a-zA-Z0-9\\-\\.]{1,253}[a-zA-Z0-9]$")) {
                if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                    TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO, "No DNS!", false);
                }
                return;
            }
            
            // S-7: sanitise user input before writing to logs to prevent log injection
            String safeDns = search_dns.replaceAll("[\r\n\t]", "_");
            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,
                        "Search IP/Port for DNS: '" + safeDns + "'", false);
            }
            
            if(!TS3DNSClusterServer.existsCache(search_dns)) {
                if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                    TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,
                            "DNS: '" + safeDns + "' not in Cache! Search in database..", false);
                }
                
                query = "SELECT `ip`,`port`,`server-id`,`failback_ip`,`failback_port`,`failback` FROM `dns` WHERE (`dns` = ? OR `dns` = '*') AND (`machine-id` = ? OR `machine-id` = 0) ORDER BY `id` DESC LIMIT 1;";
                // B-2: use try-with-resources for ResultSet
                try (PreparedStatement stmt = this.mysql.prepare(query)) {
                    stmt.setString(1, search_dns);
                    stmt.setInt(2, TS3DNSClusterServer.machine_id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while(rs.next()) {
                            ip = rs.getString("ip");
                            port = rs.getString("port");
                            sid = rs.getString("server-id");
                            failback_ip = rs.getString("failback_ip");
                            failback_port = rs.getString("failback_port");
                            failback = rs.getString("failback");
                            TS3DNSClusterServer.setCache(search_dns, ip, port, sid, failback_ip, failback_port, failback);
                            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,
                                        "Found: '" + ip + ":" + port + "' for DNS: '" + safeDns + "'", false);
                            }
                        }
                    }
                }
            } else {
                //In Cache
                if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                    TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO, "Get IP from Cache", false);
                }
                
                Map<String, Object> data = TS3DNSClusterServer.getCache(search_dns);
                ip = data.get("ip").toString();
                port = data.get("port").toString();
                sid = data.get("sid").toString();
                failback_ip = data.get("fback_ip").toString();
                failback_port = data.get("fback_port").toString();
                failback = data.get("fback").toString();
                if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                    TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,
                            "Found: " + ip + ":" + port + " for DNS: " + safeDns + " in Cache", false);
                }
            }

            //Check Server
            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_master_server")) || 
                    Boolean.parseBoolean(TS3DNSCluster.getProperty("default_slave_server"))) {
                Map<String, Object> msd = null;
                
                //Master Server Select
                if(TS3DNSServer.existsMaster("sid_" + sid)) {
                    //1.Master
                    msd = TS3DNSServer.getMaster("sid_" + sid);
                    
                    int is_online = Integer.parseInt(msd.get("online").toString());
                    int has_failback = Integer.parseInt(failback);
                    if((is_online != 1) && (has_failback == 1)) { //Master 1. ist offline
                        if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,
                                    "Server: " + ip + ":" + port + " is Offline! Failback to: "
                                    + failback_ip + ":" + failback_port, false);
                        }

                        // S-6: use = instead of LIKE to avoid unintended wildcard matching
                        query = "SELECT `online`,`port`,`ip` FROM `servers` WHERE `ip` = ?;";
                        int is_online_fb = 0;
                        // B-2: use try-with-resources for ResultSet
                        try (PreparedStatement stmt = this.mysql.prepare(query)) {
                            stmt.setString(1, failback_ip);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if(rs.next()) { 
                                    is_online_fb = Integer.parseInt(rs.getString("online"));
                                    ip = rs.getString("ip");
                                    port = rs.getString("port");
                                }
                            }
                        }
                     
                        if(is_online_fb != 0) {
                            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,
                                        "Failback Server: " + ip + ":" + port + " is Online! Failback to: "
                                        + failback_ip + ":" + failback_port, false);
                            }
                            ip = failback_ip; port = failback_port;
                        } else {
                            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,
                                        "Failback Server: " + failback_ip + ":" + failback_port
                                        + " is Offline! Send default: " + default_ip + ":" + default_port, false);
                            }
                            ip = default_ip; port = default_port;
                        }
                    }
                }
            }
            
            //Update Stats
            query = "UPDATE `dns` SET `lastused` = ?, `usecount` = (usecount+1) WHERE `ip` = ? AND `port` = ? AND `dns` = ?;";
            try (PreparedStatement stmt_update = this.mysql.prepare(query)) {
                int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
                stmt_update.setInt(1, currentTimestamp);
                stmt_update.setString(2, ip);
                stmt_update.setString(3, port);
                stmt_update.setString(4, search_dns);
                // P-6: use executeUpdate() for UPDATE/DML statements
                stmt_update.executeUpdate();
            } catch (SQLException ex) {
                Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, ex);
            }

            //Send IP
            output = new PrintStream(sock.getOutputStream(), true, "UTF-8");
            output.print(ip + ":" + port);
            output.flush();
        } catch (SocketException exception) {
            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE, exception.getMessage(), true);
        } catch (UnsupportedEncodingException exception) {
            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE, exception.getMessage(), true);
        } catch (IOException | SQLException exception) {
            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE, exception.getMessage(), true);
        }
    }
    
    private String readUntilEnd() {
        try {
            StringBuilder sb = new StringBuilder();
            do {
                sb.append((char)input.read());
                // S-5: limit input to 255 chars to prevent DoS / heap exhaustion
                if(sb.length() >= 255) break;
            }
            while(input.ready());
            return sb.toString();
        } catch(IOException exception) {
            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE, exception.getMessage(), true);
            return "";
        }
    }
}
