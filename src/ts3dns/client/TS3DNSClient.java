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

package ts3dns.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;
import ts3dns.cluster.TS3DNSCluster;

public class TS3DNSClient extends Thread {
    private final Socket client;
    private BufferedReader input;
    private PrintStream output;
    private String search_dns;

    public TS3DNSClient(Socket client) {
        this.client = client;
        this.search_dns = "";
        this.input = null;
        this.output = null;
    }
    
    public void run() {
        try {
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Get connection from: ")).append(client.getInetAddress().getHostAddress()).toString());
            //Get DNS
            input = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            search_dns = readUntilEnd();
            
            Logger.getLogger(TS3DNSCluster.class.getName()).log(Level.INFO, (new StringBuilder("Search for DNS: ")).append(search_dns).toString());

            //Send IP
            output = new PrintStream(client.getOutputStream(), true, "UTF-8");
            output.print("123.123.123.123:1234");
            output.flush();
            
            try {
                if(input != null)
                    input.close();
                
                if(output != null)
                    output.close();
          
                client.close();
            } catch(Exception exception) {
                Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
            }
        } catch (SocketException exception) {
            Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
        } catch (UnsupportedEncodingException exception) {
            Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
        } catch (IOException exception) {
            Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
    
    private String readUntilEnd() {
        try {
            StringBuilder sb = new StringBuilder();
            do {
                sb.append((char)input.read());
            }
            while(input.ready());
            return sb.toString();
        } catch(Exception exception) {
            Logger.getLogger(TS3DNSClient.class.getName()).log(Level.SEVERE, null, exception);
            return "";
        }
    }
}
