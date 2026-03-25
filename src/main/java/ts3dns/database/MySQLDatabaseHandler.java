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

package ts3dns.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import ts3dns.cluster.TS3DNSCluster;

public class MySQLDatabaseHandler {
    private Connection connection = null;
    // S-1: keep URL without credentials so it is safe to log
    private String url = "jdbc:mariadb://localhost:3306/project";
    private String user = "";
    private String pass = "";
    
    public MySQLDatabaseHandler(String host, int port, String user, String pass, String db) {
        // S-1: credentials are stored separately, not embedded in the URL
        this.url = "jdbc:mariadb://" + host + ":" + port + "/" + db;
        this.user = user;
        this.pass = pass;
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            TS3DNSCluster.log(MySQLDatabaseHandler.class.getName(), Level.SEVERE, ex.getMessage(), true);
        }
    }

    // S-8: fix inverted isClosed() condition – connection was never closed before
    public void close() throws SQLException {
        if(connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
    
    private void getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            // S-1: log URL without credentials
            TS3DNSCluster.log(MySQLDatabaseHandler.class.getName(), Level.INFO,
                    "Connect to MySQL Server: " + url, false);
            // S-1: pass credentials as separate parameters, not in URL
            connection = DriverManager.getConnection(url, user, pass);
        }
    }

    public PreparedStatement prepare(String query, String dns) throws SQLException {
        this.getConnection();
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, dns);
        return stmt;
    }
    
    public PreparedStatement prepare(String query) throws SQLException {
        this.getConnection();
        return connection.prepareStatement(query);
    }
}
