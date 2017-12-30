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
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.cluster.TS3DNSCluster;
import static ts3dns.cluster.TS3DNSCluster.properties;
import ts3dns.cluster.TS3DNSClusterPing;
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
        this.ip = default_ip;
        this.port = default_port;
    }

    public void run() {
        try {
            if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("Get connection from: ")).append(client.getInetAddress().getHostAddress()).toString(),false);
            }
            
            //Get DNS
            client.setSoTimeout(50); //Set Timeout
            input = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));

            Scanner scanner = new Scanner(input);
            while (scanner.hasNextLine()) {
              search_dns = scanner.nextLine();
           }

            //Check has Domain
            if(search_dns.length() == 0) {
                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("No DNS!")).toString(),false);
                try {
                    if(input != null)
                        input.close();

                    if(output != null)
                        output.close();

                    client.close();
                } catch(IOException exception) {}
                
                return;
            }
            
            if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("Search IP/Port for DNS: '")).append(search_dns).append("'").toString(),false);
            }
            
            if(!TS3DNSClusterServer.existsCache(search_dns)) {
                if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                    TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("DNS: '")).append(search_dns).append("' not in Cache! Search in database..").toString(),false);
                }
                
                query = "SELECT `ip`,`port`,`server-id`,`failback_ip`,`failback_port`,`failback` FROM `dns` WHERE (`dns` = ? OR `dns` = '*') AND (`machine-id` = ? OR `machine-id` = 0) ORDER BY `id` DESC LIMIT 1;";
                ResultSet rs;
                try (PreparedStatement stmt = this.mysql.prepare(query)) {
                    stmt.setString(1, search_dns);
                    stmt.setInt(2, TS3DNSClusterServer.machine_id);
                    rs = stmt.executeQuery();
                        while(rs.next()) {
                            ip = rs.getString("ip");
                            port = rs.getString("port");
                            sid = rs.getString("server-id");
                            failback_ip = rs.getString("failback_ip");
                            failback_port = rs.getString("failback_port");
                            failback = rs.getString("failback");
                            TS3DNSClusterServer.setCache(search_dns, ip, port, sid, failback_ip, failback_port, failback);
                            if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("Found: '")).
                                append(ip).append(":").append(port).append("' for DNS: '").append(search_dns).append("'").toString(),false);
                            }
                       }
                    
                    stmt.close(); 
                    rs.close();
                } 
            } else {
                if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                    TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("Get IP from Cache")).toString(),false);
                }
                
                Map data = TS3DNSClusterServer.getCache(search_dns);
                ip = data.get("ip").toString();
                port = data.get("port").toString();
                sid = data.get("sid").toString();
                failback_ip = data.get("fback_ip").toString();
                failback_port = data.get("fback_port").toString();
                failback = data.get("fback").toString();
                if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                    TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("Found: ")).
                    append(ip).append(":").append(port).append(" for DNS: ").append(search_dns).append(" in Cache").toString(),false);
                }
            }
            
            //Check Server
            if(Boolean.parseBoolean(properties.getProperty("default_master_server")) || 
                    Boolean.parseBoolean(properties.getProperty("default_slave_server"))) {
                Map msd = null;
                if(TS3DNSServer.existsMaster((new StringBuilder("sid_").append(sid)).toString())) {
                    msd = TS3DNSServer.getMaster((new StringBuilder("sid_").append(sid)).toString());
                    if(msd.get("online").equals("off") && Boolean.parseBoolean(failback)) {
                        if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("Server: ")).
                            append(ip).append(":").append(port).append(" is Offline! Failback to: ").append(failback_ip).append(":").append(failback_port).toString(),false);
                        }

                        ip = failback_ip; port = failback_port;
                    } else if(msd.get("online").equals("off")) {
                        //Check default Server
                        query = "SELECT `dns`,`ip`,`port`,`server-id`,`failback_ip`,`failback_port`,`failback` FROM `dns` WHERE `default` = 1 AND (`machine-id` = 0 OR `machine-id` = ?) ORDER BY `id` LIMIT 1;";
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
                                sid = rs.getString("server-id");
                            }

                            stmt.close(); rs.close();
                            if(TS3DNSServer.existsMaster((new StringBuilder("sid_").append(sid)).toString())) {
                                msd = TS3DNSServer.getMaster((new StringBuilder("sid_").append(sid)).toString());
                                if(msd.get("online").equals("off") && Boolean.parseBoolean(failback)) {
                                    if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                                        TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("Server: ")).
                                        append(ip).append(":").append(port).append(" is Offline! Failback to: ").append(failback_ip).append(":").append(failback_port).toString(),false);
                                    }

                                    ip = failback_ip; port = failback_port;
                                } else if(msd.get("online").equals("off")) {
                                    if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                                        TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.INFO,(new StringBuilder("Failback Server: ")).
                                        append(failback_ip).append(":").append(failback_port).append(" is Offline! Send default: ").append(default_ip).append(":").append(default_port).toString(),false);
                                    }

                                    ip = default_ip; port = default_port;
                                }
                            }
                        }
                    }
                }
            }
            
            //Update Stats
            query = "UPDATE `dns` SET `lastused` = ?, `usecount` = (usecount+1) WHERE `ip` = ? AND `port` = ? AND `dns` = ?;";
            try (PreparedStatement stmt_update = this.mysql.prepare(query)) {
                int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);

                //Insert to SQL-Query
                stmt_update.setInt(1, currentTimestamp);
                stmt_update.setString(2, ip);
                stmt_update.setString(3, port);
                stmt_update.setString(4, search_dns);
                stmt_update.executeQuery();
                stmt_update.close();
            } catch (SQLException ex) {
                Logger.getLogger(TS3DNSClusterPing.class.getName()).log(Level.SEVERE, null, ex);
            }

            //Send IP
            output = new PrintStream(client.getOutputStream(), true, "UTF-8");
            output.print((new StringBuilder(ip).append(":").append(port)));
            output.flush();
            
            try {
                if(input != null)
                    input.close();
                
                if(output != null)
                    output.close();
          
                client.close();
            } catch(IOException exception) {
                TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE,exception.getMessage(),true);
            }
        } catch (SocketException exception) {
            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE,exception.getMessage(),true);
        } catch (UnsupportedEncodingException exception) {
            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE,exception.getMessage(),true);
        } catch (IOException | SQLException exception) {
            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE,exception.getMessage(),true);
        }
    }
    
    private String readUntilEnd() {
        try {
            StringBuilder sb = new StringBuilder();
            do {
                sb.append((char)input.read());
            }
            while(input.ready());
            return sb.toString();
        } catch(IOException exception) {
            TS3DNSCluster.log(TS3DNSClient.class.getName(), Level.SEVERE,exception.getMessage(),true);
            return "";
        }
    }
}
