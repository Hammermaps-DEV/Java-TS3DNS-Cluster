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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.client.TS3DNSClient;

public class TS3DNSClusterServer {
    private ServerSocket server;
    private java.net.Socket client;
    private TS3DNSClient tS3DNSClient;
    
    public TS3DNSClusterServer() {
        try {
            Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Waiting for connections on port ")).append(Integer.toString(TS3DNSCluster.port)).toString());
            server = new ServerSocket(TS3DNSCluster.port); //Listen Server
            server.setSoTimeout(1000); // Set Timeout
        } catch (IOException ex) {
            Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void start() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                client = server.accept();
                if(client.isConnected()) {
                    Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.INFO, (new StringBuilder("Connected ")).append(client.getInetAddress()).toString());
                    client.setSoTimeout(3000); //Set Client Timeout
                    tS3DNSClient = new TS3DNSClient(client);
                    tS3DNSClient.start();
                }
            }
            catch(SocketTimeoutException ex) { } 
            catch (IOException ex) {
                Logger.getLogger(TS3DNSClusterServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
