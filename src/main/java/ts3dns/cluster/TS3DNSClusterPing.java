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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.client.TS3Updater;
import ts3dns.database.MySQLDatabaseHandler;
import ts3dns.server.TS3DNSServer;

public class TS3DNSClusterPing extends Thread {
    private MySQLDatabaseHandler mysql = null;
    private TS3DNSClusterServer common = null;
    private final int timeout = 1000;
    private int port = 0;
    private int id = 0;
    private String ip = "";
    private String query = "";
    private String user = "";
    private String pw = "";
    private TS3Updater update;

    public TS3DNSClusterPing(TS3DNSClusterServer common, MySQLDatabaseHandler mysql, String ip, int port, int id, String user, String pw) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.mysql = mysql;
        this.user = user;
        this.pw = pw;
        this.common = common;
        
        //Update the Database over TS3 API
        update = new TS3Updater(mysql, ip, port, id, this.user, this.pw);
    }

    @Override
    public void run() {
        // B-4: use try-with-resources for Socket to prevent resource leak on exception
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            
            query = "UPDATE `servers` SET `online` = 1 WHERE `id` = ?;";
            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSClusterPing.class.getName(), Level.INFO,
                        "Check TeamSpeak 3 Master Server: '" + ip + ":" + port + "' is Online", false);
            }
            
            //Update the Database over TS3 API
            if(!TS3DNSClusterServer.lock_update.containsKey(id) && !update.runned) {
                update.start();
            }
            
            int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
            if(TS3DNSClusterServer.lock_update.containsKey(id)) {
                if((TS3DNSClusterServer.lock_update.get(id) + 10) <= currentTimestamp) {
                    TS3DNSClusterServer.lock_update.remove(id);
                }
            }
            
            //Check is 0 to 1 (send MSG)
            Map<String, Object> msd = TS3DNSServer.getMaster("sid_" + id);
            int is_online = Integer.parseInt(msd.get("online").toString());
            // Q-5: fix typo: massages -> messages
            if(is_online == 0 && Boolean.parseBoolean(TS3DNSCluster.getProperty("default_send_messages")))  {
                TS3DNSServer.setMaster("sid_" + id, 1);
                this.common.sendMSG("[color=green][b][### Proxy ###] => TeaSpeak Instance: '" + ip + ":" + port + "' is Online[/b][/color]");
            }
        } catch (IOException ex) {
            query = "UPDATE `servers` SET `online` = 0 WHERE `id` = ?;";
            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSClusterPing.class.getName(), Level.INFO,
                        "Check TeamSpeak 3 Master Server: '" + ip + ":" + port + "' is Offline", false);
            }
            
            //Check is 1 to 0 (send MSG)
            Map<String, Object> msd = TS3DNSServer.getMaster("sid_" + id);
            int is_online = Integer.parseInt(msd.get("online").toString());
            // Q-5: fix typo: massages -> messages
            if(is_online == 1 && Boolean.parseBoolean(TS3DNSCluster.getProperty("default_send_messages")))  {
                TS3DNSServer.setMaster("sid_" + id, 0);
                this.common.sendMSG("[color=red][b][### Proxy ###] => TeaSpeak Instance: '" + ip + ":" + port + "' is Offline![/b][/color]");
            }
        }
        
        // B-3: no redundant stmt.close(); try-with-resources handles it
        try (PreparedStatement stmt = this.mysql.prepare(query)) {
            stmt.setInt(1, this.id);
            // P-6: use executeUpdate() for UPDATE/DML statements
            stmt.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(TS3DNSClusterPing.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
