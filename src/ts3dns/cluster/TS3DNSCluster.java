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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TS3DNSCluster {
    public static Properties properties = null;
    public static final int port = 41144;
    public static final String VERSION = "TS3DNS Cluster 1.0 Alpha";
    public static String configFile = "TS3DNS-Cluster.cfg";
    private static TS3DNSClusterServer server;
    
    public static void main(String[] args) {
        Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Start ")).append(VERSION).toString());
        if(loadConfig()) {
            server = new TS3DNSClusterServer();
            server.start();
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Running ")).append(VERSION).toString());
        } else {
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.CONFIG, (new StringBuilder("Error while loading configuration from ")).append(configFile).toString());
        }
    }
    
    //Load Config
    private static boolean loadConfig() {
        boolean retValue = false;
        File confFile = new File(configFile);
        if(!confFile.isFile()) { return retValue; }
        properties = new Properties();
        try {
            properties.load(new FileInputStream(confFile));
            //logFilePath = properties.getProperty("xxxx");
            
            if(!properties.isEmpty() && properties != null) {
                retValue = true;
            }
        } catch (IOException exception) {
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.SEVERE, null, exception);
        }
        
        return retValue;
    }
}
