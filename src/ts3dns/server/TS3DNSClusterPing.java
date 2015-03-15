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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.database.MySQLDatabaseHandler;

public class TS3DNSClusterPing extends Thread {
    private MySQLDatabaseHandler mysql = null;
    private final int timeout = 1000;
    private int port = 0;
    private int id = 0;
    private String ip = "";
    private Socket socket = null;
    private String query = "";

    public TS3DNSClusterPing(MySQLDatabaseHandler mysql, String ip, int port, int id) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.mysql = mysql;
    }

    @Override
    public void run() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), timeout);
            socket.close();
            query = "UPDATE `servers` SET `online` = 1 WHERE `id` = ?;";
            //TS3DNSCluster.log(TS3DNSClusterPing.class.getName(), Level.INFO,(new StringBuilder("Check TS3 Server: ").append(ip).append(":").append(port).append(" is Online")).toString(),false);
            TS3DNSServer.setMaster((new StringBuilder("sid_").append(id)).toString(), "on");
        } catch (Exception ex) {
            query = "UPDATE `servers` SET `online` = 0 WHERE `id` = ?;";
            //TS3DNSCluster.log(TS3DNSClusterPing.class.getName(), Level.INFO,(new StringBuilder("Check TS3 Server: ").append(ip).append(":").append(port).append(" is Offline")).toString(),false);
            TS3DNSServer.setMaster((new StringBuilder("sid_").append(id)).toString(), "off");
        }
        
        try (PreparedStatement stmt = this.mysql.prepare(query)) {
            stmt.setInt(1, this.id);
            stmt.executeQuery();
            stmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(TS3DNSClusterPing.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
