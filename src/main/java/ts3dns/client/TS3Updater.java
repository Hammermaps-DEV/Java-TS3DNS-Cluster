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

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.cluster.TS3DNSCluster;
import static ts3dns.cluster.TS3DNSCluster.properties;
import ts3dns.cluster.TS3DNSClusterServer;
import ts3dns.database.MySQLDatabaseHandler;

public class TS3Updater extends Thread {
    private MySQLDatabaseHandler mysql = null;
    private int port = 0;
    private int sid = 0;
    private String ip = "";
    private String user = "";    
    private String pw = "";
    private String query;
    public boolean runned = false;

    public TS3Updater(MySQLDatabaseHandler mysql, String ip, int port, int sid, String user, String pw) {
        this.sid = sid;
        this.ip = ip;
        this.port = port;
        this.mysql = mysql;
        this.user = user;
        this.pw = pw;
        this.runned = false;
    }

    @Override
    public void run() {
        if("".equals(this.user) || "".equals(this.pw) || this.sid <= 0 || "".equals(this.ip))
            return;
        
        this.runned = true;
        
        //Update Server Name
        final TS3Config config = new TS3Config();
        config.setHost(ip);
        config.setQueryPort(((int)port));
        config.setCommandTimeout(15000);
        
        if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
            config.setEnableCommunicationsLogging(true);
        }
        
        final TS3Query tsquery = new TS3Query(config);
        tsquery.connect();

        final TS3Api api = tsquery.getApi();
        api.login(this.user, this.pw);
        
        if(!"".equals(api.getVersion().getBuild())) {
            query = "SELECT `port`,`id` FROM `dns` WHERE `server-id` = ? AND (`machine-id` = ? OR `machine-id` = 0);";
            ResultSet rs;
            try (PreparedStatement stmt = this.mysql.prepare(query)) {
                stmt.setInt(1, this.sid);
                stmt.setInt(2, TS3DNSClusterServer.machine_id);
                rs = stmt.executeQuery();
                while(rs.next()) {
                    //Select Server
                    if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                        TS3DNSCluster.log(TS3Updater.class.getName(), Level.INFO,(new StringBuilder("Update TS3 Server for ID: ").append(this.sid).append(" [").append(ip).append(":").append(rs.getString("port")).append(" ]")).toString(),false);
                    }

                    api.selectVirtualServerByPort(Integer.parseInt(rs.getString("port")));
                    query = "UPDATE `dns` SET `active_slots` = ?, `slots` = ?, `name` = ?, `vserver-id` = ? WHERE `id` = ?;";
                        try (PreparedStatement stmt_update = this.mysql.prepare(query)) {
                        int ClientsOnline = api.getServerInfo().getClientsOnline();
                        int MaxClients = api.getServerInfo().getMaxClients();
                        String ServerName = api.getServerInfo().getName();
                        if(ClientsOnline != 0) {
                            ClientsOnline = (ClientsOnline-1);
                        }

                        if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                            TS3DNSCluster.log(TS3Updater.class.getName(), Level.INFO,(new StringBuilder("Update TS3 Server for ID: ").append(this.sid).
                                    append(" [ClientsOnline=").append(ClientsOnline).append(",").
                                    append(" MaxClients=").append(MaxClients).append(",").
                                    append(" ServerName=").append(ServerName).append("]")).toString(),false);
                        }

                        //Insert to SQL-Query
                        stmt_update.setInt(1, ClientsOnline);
                        stmt_update.setInt(2, MaxClients);
                        stmt_update.setString(3,  ServerName);
                        stmt_update.setInt(4,  api.getServerInfo().getId());
                        stmt_update.setInt(5, Integer.parseInt(rs.getString("id")));
                        stmt_update.executeQuery();
                        stmt_update.close();

                        //Lock
                        int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
                        TS3DNSClusterServer.lock_update.put(this.sid, currentTimestamp);

                    } catch (SQLException ex) {
                        Logger.getLogger(TS3Updater.class.getName()).log(Level.SEVERE, null, ex);
                    }         
                }

                stmt.close(); 
                rs.close();
            } catch (SQLException ex) {
                Logger.getLogger(TS3Updater.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        //Logout
        api.logout();
        tsquery.exit();
        this.runned = false;
    }
}
