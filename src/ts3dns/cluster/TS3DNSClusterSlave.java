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

import java.util.logging.Level;
import static ts3dns.cluster.TS3DNSCluster.properties;
import ts3dns.server.TS3DNSServer;

public class TS3DNSClusterSlave extends Thread {
    
    public void run() 
    {
        String slave_id = new StringBuilder("slave_").append(TS3DNSClusterServer.machine_id).toString();
        TS3DNSCluster.log(TS3DNSCluster.class.getName(), Level.INFO,(new StringBuilder("Register Slave with ID:")).append(TS3DNSClusterServer.machine_id).toString(),false);
        while (true) {
            int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
            TS3DNSServer.setSlave(slave_id, currentTimestamp);
            if(Boolean.parseBoolean(properties.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSCluster.class.getName(), Level.INFO,(new StringBuilder("Update Slave with ID:")).append(TS3DNSClusterServer.machine_id).append(" / ").append(currentTimestamp).toString(),false);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) { }
        }    
    }
}