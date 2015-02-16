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
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.cluster.TS3DNSCluster;
import ts3dns.cluster.TS3DNSClusterServer;
import ts3dns.database.MySQLDatabaseHandler;

public class TS3DNSClient extends Thread {
    private final Socket client;
    private BufferedReader input;
    private PrintStream output;
    private String search_dns;
    private final MySQLDatabaseHandler mysql;
    private String query;
    private String ip;

    public TS3DNSClient(Socket client, MySQLDatabaseHandler mysql, String default_ip) {
        this.client = client;
        this.mysql = mysql;
        this.search_dns = "";
        this.input = null;
        this.output = null;
        this.ip = default_ip;
    }
    
    public void run() {
        try {
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Get connection from: ")).append(client.getInetAddress().getHostAddress()).toString());
            //Get DNS
            input = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            search_dns = readUntilEnd();
            
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Search for DNS: ")).append(search_dns).toString());
            if(!TS3DNSClusterServer.existsCache(search_dns)) {
                Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Search DNS in MySQL")).toString());
                query = "SELECT `ip` FROM `dns` WHERE `dns` = ? AND (`machine-id` = 0 OR `machine-id` = ?) LIMIT 1;";
                ResultSet rs;
                try (PreparedStatement stmt = this.mysql.prepare(query)) {
                    stmt.setString(1, search_dns);
                    stmt.setInt(2, TS3DNSClusterServer.machine_id);
                    rs = stmt.executeQuery();
                    while(rs.next()) {
                        ip = rs.getString("ip");
                        TS3DNSClusterServer.setCache(search_dns, ip);
                        Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Found: ")).append(ip).append(" for DNS: ").append(search_dns).toString());
                    }
                    stmt.close(); rs.close();
                }
            } else {
                ip = TS3DNSClusterServer.getCache(search_dns);
                Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Found: ")).append(ip).append(" for DNS: ").append(search_dns).append(" in Cache").toString());
            }
            
            //Send IP
            output = new PrintStream(client.getOutputStream(), true, "UTF-8");
            output.print(ip);
            output.flush();
            
            try {
                if(input != null)
                    input.close();
                
                if(output != null)
                    output.close();
          
                client.close();
            } catch(Exception exception) {
                Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
            }
        } catch (SocketException exception) {
            Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
        } catch (UnsupportedEncodingException exception) {
            Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
        } catch (IOException | SQLException exception) {
            Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
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
        } catch(Exception exception) {
            Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
            return "";
        }
    }
}
