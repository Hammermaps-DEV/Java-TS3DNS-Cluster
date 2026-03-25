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

import java.util.logging.Level;
import ts3dns.server.TS3DNSServer;

public class TS3DNSClusterSlave extends Thread {
    
    // Q-6: add @Override annotation
    @Override
    public void run() 
    {
        String slave_id = "slave_" + TS3DNSClusterServer.machine_id;
        TS3DNSCluster.log(TS3DNSCluster.class.getName(), Level.INFO,
                "Register Slave with ID:" + TS3DNSClusterServer.cb_machine_id, false);
        // B-6: use isInterrupted() for clean shutdown support
        while(!Thread.currentThread().isInterrupted()) {
            int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);
            TS3DNSServer.setSlave(slave_id, currentTimestamp);
            if(Boolean.parseBoolean(TS3DNSCluster.getProperty("default_debug"))) {
                TS3DNSCluster.log(TS3DNSCluster.class.getName(), Level.INFO,
                        "Update Slave with ID:" + TS3DNSClusterServer.cb_machine_id + " / " + currentTimestamp, false);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                // B-6: restore interrupt flag and exit loop
                Thread.currentThread().interrupt();
                break;
            }
        }    
    }
}