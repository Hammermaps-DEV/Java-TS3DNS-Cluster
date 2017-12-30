# Java-TS3DNS-Cluster

Hier handelt es sich um einen in Java geschriebenen Domain Name System Server für TeamSpeak 3 (TS3-DNS).
Dieser Server ist MySQL und Couchbase Server gestützt, er kann in mehreren Nodes ausgeführt werden.
Mit einem Load Balancer ist es dann möglich einen relativ ausfall sicheren DNS Cluster aufzubauen.

Zu beachten ist, das es zurzeit nur für einen Master und beliebig viele Slaves ausgelegt ist.
Für den Betrieb als Cluster ist ein Couchbase Server notwendig.

Alle Salves können die DNS in eine IP auflösen und die TeamSpeak 3 Clients bedienen.
Der Master kann zusätzlich den Status aller TeamSpeak 3 Server Instanzen abrufen und bei einem Ausfall alle Notes benachrichtigen.
Die Notes werden dann die Failback Funktion verwenden um alle Clients auf einen oder mehrere Ersatzserver umzuleiten.
( Im kleinen Testumfeld ausprobiert )

Die DNS Abfragen sind gecached um die MySQL Datenbank zu entlasten.
Darum kann es bei einem Ausfall einige Sekunden dauern, bis der Failback greift.

Der Master wird zudem die Datenbank regelmäßig aktualisieren, das bedeutet Ihr könnt 
'Server Namen, Aktuelle Slots, Maximale Slots und V-Server ID' abrufen, sofern ihr das braucht :)

## Installation:

**Vorbereitung:**
1. Einen lauffähigen MySQL Server, TeamSpeak 3 Server + SSA Account, Couchbase Server (Optional)
2. Die Datei release\ts3dns.sql in euren MySQL Server einspielen (Navicat oder PhpMyAdmin etc.)
3. Die Datensätze in der MySQL Datenbank beliebig ändern:

### Die Tabelle 'servers':
```
ID: Die ID ist die, die in der Tabelle 'dns' unter 'server-id' verwendet werden soll.
IP: Die Adresse der TeamSpeak 3 Hauptinstanz.
Port: Der Server Query Port des Servers.
Username und Passwort: Der SSA Account der TeamSpeak 3 Hauptinstanz.
```

### Die Tabelle 'dns':
```
dns: Der Hostname / Domain die aufgelöst werden soll.
name: Der Name des virtuellen TeamSpeak Servers auf Port xxx (Wird automatisch eingetragen wenn Ihr den DNS startet)
ip: Die Ip des virtuellen TeamSpeak Servers (Meistens der selbe wie die Hauptinstanz)
port: Der Voice Port des virtuellen TeamSpeak Servers.
default: Soll der virtuelle Server als Standard verwendet werden? (Wenn zbs. eine DNS nicht vorhanden ist) 
machine-id: Ist die Indent ID für den DNS Server, 0 bedeutet alle Instanzen, >= 1 wird nur auf Instanzen des DNS Servers bereitgestellt der die selbe machine id hat (Sehe Config)
server-id: Die ID des Servers in der Tabelle 'servers'.
vserver-id: Die ID des virtuellen TS3 Servers. (Wird automatisch eingetragen wenn Ihr den DNS startet)
slots: Wie viele Slots die virtuelle TS3 Instanz hat (Wird automatisch eingetragen wenn Ihr den DNS startet)
active_slots: Wie viele Slots werden gerade auf der virtuellen TS3 Instanz verwendet (Wird automatisch eingetragen wenn Ihr den DNS startet)
lastused: Der Timestamp der letzten Verwendung dieser DNS.
usecount: Wie oft wurde diese DNS angefragt?
failback_ip: IP des Faliback Servers
failback_port: Port des Faliback Servers
failback: 1/0 ist die Faliback Funktion verfügbar?
```

### 4. Die TS3DNS-Cluster.cfg bearbeiten.
```
Die 'default_ip_for_dns' ist eine IP Adresse die zbs. auf einen Faliback Server Zeigt, wenn überhaupt keine DNS Einträge vorhanden sein sollten. 
Der 'default_port_for_dns' ist der Port des Faliback Server der in 'default_ip_for_dns' eingetragen ist.

'default_server_port & default_server_ip' Ist die Adresse an dem der Server laufen soll, (Standard: 41144, 0.0.0.0 )
'default_machine_id' die Machine ID sehe "Tabelle 'dns' -> machine-id" bitte auf 0 lassen wenn ihr nur einen Server betreiben wollt.

Die 'MySQL Database' Einstellungen sollten klar sein was dort rein muss...

Für Cluster:
Der Master hat immer 'default_master_server' auf 'true' und 'default_slave_server' auf 'false'
Die Slaves 'default_master_server' auf 'false' und 'default_slave_server' immer auf 'true'
( 'Global Couchbase Cache' ist notwendig! )
```

Wenn alles konfiguriert ist, kann der Server mit 'java -jar Java-TS3DNS-Cluster-Maven-1.0-jar-with-dependencies.jar' gestartet werden.
Es ist kein Anti-Flood oder sonstiges im Server eingebaut, das müsst Ihr über andere Software machen oder es noch einbauen.

Das ganze ist nicht perfekt da es mein erstes Java Projekt ist.
Wenn Ihr das ganze verwendet oder helfen möchtet, würde ich mich sehr über eine Nachricht oder Pull requests freuen.
Ich wünsche euch viel Erfolg und Spaß damit.

**Der Code steht unter GNU General Public License Version 3**
