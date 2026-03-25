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

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.core.error.DocumentNotFoundException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import ts3dns.server.TS3DNSServer;

public class TS3DNSClusterMaster extends Thread {
    public Collection bucket;
    public String cb_ma_table;
    
    // Q-6: add @Override annotation
    @Override
    public void run() 
    {
        Map<String, Object> lock = new HashMap<>();
        // B-6: use isInterrupted() for clean shutdown support
        while(!Thread.currentThread().isInterrupted()) {
            GetResult foundResult;
            try {
                foundResult = TS3DNSServer.bucket.get("slaves");
            } catch (DocumentNotFoundException e) {
                return;
            }
            JsonObject content = foundResult.contentAsObject();
            // Q-1: use typed Map
            Map<String, Object> data = content.toMap();
            
            for (Iterator<Map.Entry<String, Object>> it = data.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Object> entry = it.next();
                if("null".equals(entry.getKey()) || "null".equals(entry.getValue()) || "".equals(entry.getKey().trim()))
                    continue;
                
                if(!lock.containsKey(entry.getKey())) {
                    Map<String, Object> slave = TS3DNSServer.getSlave(entry.getKey());
                    String id = entry.getKey().replace("slave_", "");
                    TS3DNSCluster.log(TS3DNSClusterMaster.class.getName(), Level.INFO,
                            "Slave server with id: '" + id + "' is detected!", false);
                    TS3DNSCluster.log(TS3DNSClusterMaster.class.getName(), Level.INFO,
                            "Slave server '" + slave.get("ip") + ":" + slave.get("port") + "' is online!", false);
                    lock.put(entry.getKey(), entry);
                }
                
                Map<String, Object> slave = TS3DNSServer.getSlave(entry.getKey());
                int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);

                if(((int)slave.get("time") + 2) <= currentTimestamp) {
                    lock.remove(entry.getKey());
                    String id = entry.getKey().replace("slave_", "");
                    TS3DNSCluster.log(TS3DNSClusterMaster.class.getName(), Level.INFO,
                            "Slave server with id: '" + id + "' is offline!", false);

                    try {
                        GetResult updResult = TS3DNSServer.bucket.get("slaves");
                        JsonObject content_bu = updResult.contentAsObject();
                        content_bu.removeKey(entry.getKey());
                        bucket.replace("slaves", content_bu);
                    } catch (DocumentNotFoundException e) {
                        // nothing to update
                    }
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                // B-6: restore interrupt flag and exit loop
                Thread.currentThread().interrupt();
                break;
            }
        }    
    }
}
