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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.admin.AdminUI;

public class TS3DNSCluster {
    // Q-7: private with getter to prevent external mutation
    private static Properties properties = null;
    public static final String VERSION = "TS3-DNS Cluster 1.0 Beta";
    public static String configFile = "TS3DNS-Cluster.cfg";
    private static TS3DNSClusterServer server;

    // Q-7: public getter for individual property values
    public static String getProperty(String key) {
        return properties != null ? properties.getProperty(key) : null;
    }

    public static void main(String[] args) {
        // Q-4: support command-line config file path
        if (args.length > 0) {
            configFile = args[0];
        }
        if(loadConfig()) {
            TS3DNSCluster.log(TS3DNSCluster.class.getName(), Level.INFO, "Start " + VERSION, false);
            try {
                server = new TS3DNSClusterServer();

                // Start Admin UI if enabled
                final AdminUI adminUI = startAdminUI();

                Runtime runtime = Runtime.getRuntime();
                // B-5: renamed stop() to shutdown()
                runtime.addShutdownHook(new Thread(() -> {
                    server.shutdown();
                    if (adminUI != null) {
                        adminUI.shutdown();
                    }
                }));
                server.start();
                TS3DNSCluster.log(TS3DNSCluster.class.getName(), Level.INFO, "Running " + VERSION, false);
            } catch (SQLException ex) {
                TS3DNSCluster.log(TS3DNSCluster.class.getName(), Level.SEVERE, ex.toString(), true);
            }
        } else {
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.CONFIG,
                    "Error while loading configuration from " + configFile);
        }
    }

    /** Starts the Admin UI if {@code admin_ui_enabled=true}; returns the instance or null. */
    private static AdminUI startAdminUI() {
        String enabled = getProperty("admin_ui_enabled");
        if (!"true".equalsIgnoreCase(enabled)) {
            log(TS3DNSCluster.class.getName(), Level.INFO, "Admin UI is disabled.", false);
            return null;
        }
        try {
            AdminUI adminUI = new AdminUI();
            adminUI.start();
            return adminUI;
        } catch (Exception ex) {
            log(TS3DNSCluster.class.getName(), Level.SEVERE,
                    "Failed to start Admin UI: " + ex.getMessage(), true);
            return null;
        }
    }
    
    //Load Config
    private static boolean loadConfig() {
        boolean retValue = false;
        File confFile = new File(configFile);
        if(!confFile.isFile()) { 
            System.out.println("Config file:'" + configFile + "' not found!"); 
            return retValue; 
        }
        properties = new Properties();
        // S-11: use try-with-resources to avoid FileInputStream resource leak
        try (FileInputStream fis = new FileInputStream(confFile)) {
            properties.load(fis);
            // S-10: check null before calling isEmpty()
            if(properties != null && !properties.isEmpty()) {
                retValue = true;
            }
        } catch (IOException exception) {
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.SEVERE, null, exception);
        }
        
        return retValue;
    }
    
    // Q-2: use java.util.logging with proper level filtering
    public static void log(String classn, Level lvl, String msg, boolean error) {
        if(error || (properties != null && Boolean.parseBoolean(properties.getProperty("default_debug", "false")))) {
            Logger.getLogger(classn).log(lvl, msg);
        }
    }
}
