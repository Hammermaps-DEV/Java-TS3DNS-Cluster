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

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import ts3dns.server.TS3DNSServer;

public class TS3DNSClusterMaster extends Thread {
    public Bucket bucket;
    public String cb_ma_table;
    private static JsonDocument found = null;
    
    public void run() 
    {
        Map lock = new HashMap();
        while (true) {
            found = TS3DNSServer.bucket.get("slaves");
            if(found == null) { return; }
            JsonObject content = found.content();
            Map data = content.toMap();
            
            Map<String, Object> map = data; Map slave = null;
            for (Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Object> entry = it.next();
                if("null".equals(entry.getKey()) || "null".equals(entry.getValue()) || "".equals(entry.getKey().trim()))
                    continue;
                
                if(!lock.containsKey(entry.getKey())) {
                    slave = TS3DNSServer.getSlave(entry.getKey());
                    String id = entry.getKey().replace("slave_", "");
                    TS3DNSCluster.log(TS3DNSClusterMaster.class.getName(), Level.INFO,
                            (new StringBuilder("Slave server with id: '"+id+"' is detected!")).toString(),false);
                    TS3DNSCluster.log(TS3DNSClusterMaster.class.getName(), Level.INFO,
                            (new StringBuilder("Slave server '"+slave.get("ip")+":"+slave.get("port")+"' is online!")).toString(),false);
                    lock.put(entry.getKey(), entry);
                }
                
                slave = TS3DNSServer.getSlave(entry.getKey());
                int currentTimestamp = (int)(System.currentTimeMillis() / 1000L);

                if(((int)slave.get("time")+2) <= currentTimestamp) {
                    lock.remove(entry.getKey());
                    String id = entry.getKey().replace("slave_", "");
                    TS3DNSCluster.log(TS3DNSClusterMaster.class.getName(), Level.INFO,
                            (new StringBuilder("Slave server with id: '"+id+"' is offline!")).toString(),false);

                    found = TS3DNSServer.bucket.get("slaves");
                    if(found != null) { 
                        JsonObject content_bu = found.content();
                        content_bu.removeKey(entry.getKey().toString());
                        bucket.replace(JsonDocument.create("slaves", content_bu));
                    }
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) { }
        }    
    }
}
