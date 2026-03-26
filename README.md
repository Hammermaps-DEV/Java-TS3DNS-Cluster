# Java-TS3DNS-Cluster

Ein in Java 21 geschriebener DNS-Server für TeamSpeak 3 (TS3-DNS) mit Cluster-Unterstützung, webbasierter Admin-Oberfläche und integriertem Anti-Flood-Schutz.

Der Server ist MySQL/MariaDB-gestützt und kann optional mit einem Couchbase-Cache als verteilter Cluster betrieben werden. Mit einem vorgeschalteten Load Balancer lässt sich ein ausfallsicherer DNS-Cluster aufbauen.

## Inhaltsverzeichnis

1. [Features](#1-features)
2. [Voraussetzungen](#2-voraussetzungen)
3. [Schnellstart](#3-schnellstart)
4. [Datenbankstruktur](#4-datenbankstruktur)
5. [Konfiguration](#5-konfiguration)
6. [Server starten](#6-server-starten)
7. [Admin UI](#7-admin-ui)
8. [Anti-Flood-Schutz](#8-anti-flood-schutz)
9. [Cluster-Betrieb (Master / Slave)](#9-cluster-betrieb-master--slave)
10. [Lizenz](#10-lizenz)

---

## 1. Features

- **DNS-Auflösung** von Hostnamen/Domains auf TeamSpeak 3 Serveradressen (IP + Voice-Port)
- **Datenbank-Cache** – DNS-Abfragen werden gecacht, um die Datenbank zu entlasten
- **Automatisches Server-Monitoring** – der Master fragt regelmäßig alle TS3-Instanzen ab und aktualisiert Servername, Slot-Zahl und aktive Verbindungen in der Datenbank
- **Failback-Funktion** – bei einem Serverausfall werden alle Nodes benachrichtigt und leiten Clients automatisch auf einen Ersatzserver um
- **Cluster-Modus** – ein Master-Node und beliebig viele Slave-Nodes; erfordert optionalen Couchbase-Cache
- **Admin UI** – webbasierte Verwaltungsoberfläche (Standard: Port 8080) mit REST-API für DNS-Einträge, Server, Cluster-Status und Flood-Verwaltung
- **Anti-Flood-Schutz** – konfigurierbares Rate-Limiting pro IP, automatisches Bannen und persistente Ban-Verwaltung via MySQL-Tabelle `flood_bans`
- **Java 21** – moderne Laufzeitumgebung

---

## 2. Voraussetzungen

| Komponente | Mindestversion | Hinweis |
|---|---|---|
| Java (JRE/JDK) | 21 | Zum Starten des JARs |
| MySQL / MariaDB | 10.2+ | Pflicht |
| TeamSpeak 3 Server | aktuell | Mit ServerQuery-Account (SSA) |
| Couchbase Server | 7.x | **Optional** – nur für Cluster-Betrieb |

---

## 3. Schnellstart

1. **Datenbank anlegen** – Zunächst die Datenbank erstellen, danach das Schema importieren:
   ```bash
   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ts3dns CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   mysql -u root -p ts3dns < release/ts3dns.sql
   ```

2. **Konfigurationsdatei anpassen** – `release/TS3DNS-Cluster.cfg` bearbeiten (siehe [Abschnitt 5](#5-konfiguration)).

3. **Server starten:**
   ```bash
   java -jar Java-TS3DNS-Cluster-Maven-1.0-jar-with-dependencies.jar
   ```

4. **Admin UI öffnen** (falls aktiviert): `http://<server-ip>:8080/`

---

## 4. Datenbankstruktur

Das SQL-Schema (`release/ts3dns.sql`) legt folgende Tabellen an:

### Tabelle `servers`

Enthält die TeamSpeak 3 Hauptinstanzen, die über ServerQuery abgefragt werden.

| Spalte | Typ | Beschreibung |
|---|---|---|
| `id` | INT (PK) | Eindeutige ID; wird in der `dns`-Tabelle als `server-id` referenziert |
| `ip` | VARCHAR(15) | IP-Adresse der TS3-Hauptinstanz |
| `port` | INT | ServerQuery-Port (Standard: `10011`) |
| `username` | VARCHAR(100) | ServerQuery-Benutzername (SSA-Account) |
| `password` | VARCHAR(100) | ServerQuery-Passwort |
| `online` | INT(1) | Wird automatisch gepflegt (0 = offline, 1 = online) |

### Tabelle `dns`

Enthält die DNS-Einträge (Hostnamen → TS3-Serveradressen).

| Spalte | Typ | Beschreibung |
|---|---|---|
| `id` | INT (PK) | Automatische ID |
| `dns` | VARCHAR(250) | Hostname / Domain, die aufgelöst werden soll (z. B. `ts.example.com` oder `*` als Wildcard) |
| `name` | VARCHAR(200) | Name des virtuellen TS3-Servers – wird automatisch befüllt |
| `ip` | VARCHAR(50) | IP des virtuellen TS3-Servers (meistens identisch mit der Hauptinstanz) |
| `port` | INT | Voice-Port des virtuellen TS3-Servers |
| `default` | INT(1) | `1` = Standard-Eintrag, wenn kein passender DNS gefunden wird |
| `machine-id` | INT | `0` = alle Nodes; `>= 1` = nur Node mit gleicher `default_machine_id` |
| `server-id` | INT | Referenz auf die `id` in der Tabelle `servers` |
| `vserver-id` | INT | Virtuelle Server-ID – wird automatisch befüllt |
| `slots` | INT | Maximale Slots – wird automatisch befüllt |
| `active_slots` | INT | Aktuelle Verbindungen – wird automatisch befüllt |
| `lastused` | INT | Unix-Timestamp der letzten Anfrage |
| `usecount` | INT | Anzahl der Anfragen für diesen Eintrag |
| `failback_ip` | VARCHAR(25) | IP des Failback-Servers |
| `failback_port` | INT | Port des Failback-Servers |
| `failback` | INT(1) | `1` = Failback aktiviert, `0` = deaktiviert |

### Tabelle `flood_bans` *(wird automatisch erstellt)*

Wird beim ersten Start automatisch angelegt und speichert persistente IP-Sperren des Anti-Flood-Schutzes.

| Spalte | Typ | Beschreibung |
|---|---|---|
| `id` | INT (PK) | Automatische ID |
| `ip` | VARCHAR(45) | Gesperrte IP-Adresse (IPv4 oder IPv6) |
| `reason` | VARCHAR(255) | Sperrgrund |
| `banned_at` | BIGINT | Unix-Timestamp des Sperrbeginns |
| `expires_at` | BIGINT | Unix-Timestamp des Ablaufs; `0` = permanent |

---

## 5. Konfiguration

Alle Einstellungen befinden sich in `release/TS3DNS-Cluster.cfg`.

### Allgemeine Einstellungen

```properties
# Debug-Ausgaben aktivieren
default_debug = false

# Rolle dieses Nodes (exklusiv: genau einer muss Master sein)
default_master_server = true
default_slave_server  = false

# Standard-Fallback, wenn kein DNS-Eintrag gefunden wird
# Muss auf eine echte IP-Adresse eines Fallback-Servers gesetzt werden!
default_ip_for_dns   = 0.0.0.0
default_port_for_dns = 9987

# Netzwerk-Bindung des DNS-Ports
default_server_port = 41144
default_server_ip   = 0.0.0.0

# Machine-ID: 0 = alle Einträge; >= 1 = nur Einträge mit gleicher machine-id
default_machine_id = 0

# TS3-Nachrichten an Clients senden (1 = ja, 0 = nein)
default_send_messages = 1
```

### MySQL / MariaDB

```properties
mysql_host = 10.10.10.3
mysql_user = xxxxxx
mysql_pass = xxxxxx
mysql_db   = ts3dns
mysql_port = 3306
```

### Globaler Couchbase-Cache *(optional, nur für Cluster)*

```properties
couchbase_enable       = false
couchbase_host         = 10.10.10.5
couchbase_bucket       = ts3dns
couchbase_username     = xxxxxx
couchbase_password     = xxxxxx
couchbase_table        = dnscache
couchbase_master_table = macache
couchbase_machine_id   = 1
```

### Admin UI

```properties
# Web-Oberfläche aktivieren
admin_ui_enabled  = true
# Port des eingebetteten HTTP-Servers
admin_ui_port     = 8080
# Bind-Adresse (0.0.0.0 = alle Interfaces)
admin_ui_bind     = 0.0.0.0
# Zugangsdaten – MÜSSEN vor dem ersten Start geändert werden!
# Empfehlung: mindestens 12 Zeichen, Groß-/Kleinbuchstaben, Ziffern und Sonderzeichen verwenden.
admin_ui_username = admin
admin_ui_password = changeme
```

### Anti-Flood-Schutz

```properties
# Rate-Limiting aktivieren
flood_protection_enabled = true
# Maximale Anfragen pro IP innerhalb des Zeitfensters
flood_max_requests       = 10
# Länge des Zeitfensters in Sekunden
flood_window_seconds     = 1
# Anzahl geblockter Anfragen, ab der eine IP automatisch gebannt wird (0 = nie)
flood_auto_ban_threshold = 100
# Dauer eines automatischen Bans in Sekunden (0 = permanent)
flood_auto_ban_duration  = 3600
```

---

## 6. Server starten

```bash
java -jar Java-TS3DNS-Cluster-Maven-1.0-jar-with-dependencies.jar
```

Der Server liest beim Start automatisch `TS3DNS-Cluster.cfg` aus dem aktuellen Verzeichnis.

---

## 7. Admin UI

Die Admin-Oberfläche ist ein eingebetteter HTTP-Server (basierend auf `com.sun.net.httpserver`) und wird über die Config-Schlüssel `admin_ui_*` konfiguriert.

**Standard-URL:** `http://<server-ip>:8080/`

Nach dem Login stehen folgende Bereiche zur Verfügung:

| Bereich | REST-Endpunkt | Beschreibung |
|---|---|---|
| Login / Logout | `/api/login`, `/api/logout`, `/api/me` | Session-Verwaltung |
| DNS-Einträge | `/api/dns` | CRUD für die Tabelle `dns` |
| Server | `/api/servers` | Verwaltung der TS3-Instanzen |
| Statistiken | `/api/stats` | Abfrage- und Nutzungsstatistiken |
| Cluster | `/api/cluster` | Cluster-Status und Node-Übersicht |
| Flood-Verwaltung | `/api/flood` | Ban-Liste, Einstellungen, Top-Blocked-IPs |

> **Sicherheitshinweis:** Zugangsdaten (`admin_ui_username` / `admin_ui_password`) **unbedingt vor dem ersten Start** in der Konfigurationsdatei ändern. Es wird ein sicheres Passwort mit mindestens 12 Zeichen empfohlen.

---

## 8. Anti-Flood-Schutz

Der integrierte Anti-Flood-Schutz schützt den DNS-Port (Standard: `41144`) vor Überlastung durch zu viele Anfragen von einer einzelnen IP.

**Funktionsweise:**
- Jede eingehende Verbindung wird anhand der Quell-IP geprüft.
- Überschreitet eine IP das konfigurierte Limit (`flood_max_requests` pro `flood_window_seconds`), wird die Verbindung sofort getrennt.
- Ab `flood_auto_ban_threshold` geblockten Anfragen wird die IP automatisch für `flood_auto_ban_duration` Sekunden gesperrt.
- Sperren werden in der MySQL-Tabelle `flood_bans` gespeichert und überleben einen Server-Neustart.
- Einstellungen können zur Laufzeit über die Admin UI geändert werden, ohne den Server neu zu starten.

---

## 9. Cluster-Betrieb (Master / Slave)

Der Cluster besteht aus **einem Master-Node** und **beliebig vielen Slave-Nodes**.

### Master-Node
```properties
default_master_server = true
default_slave_server  = false
```

- Fragt alle TeamSpeak 3 Instanzen regelmäßig via ServerQuery ab.
- Aktualisiert Servernamen, Slot-Zahlen und aktive Verbindungen in der Datenbank.
- Erkennt Serverausfälle und benachrichtigt alle Nodes über den Couchbase-Cache.

### Slave-Node
```properties
default_master_server = false
default_slave_server  = true
```

- Beantwortet DNS-Anfragen und bedient TS3-Clients.
- Empfängt Failback-Benachrichtigungen vom Master.
- Leitet Clients bei einem Ausfall auf den konfigurierten Failback-Server um.

### Voraussetzung für den Cluster-Betrieb

Ein gemeinsamer **Couchbase Server** ist zwingend erforderlich, damit Master und Slaves kommunizieren können:

```properties
couchbase_enable = true
```

> **Hinweis:** Im Einzelbetrieb (nur ein Node) ist Couchbase optional und kann deaktiviert bleiben.

### Machine-ID

Über `default_machine_id` lassen sich DNS-Einträge auf bestimmte Nodes beschränken:
- `0` → Eintrag wird von allen Nodes bedient
- `>= 1` → Eintrag wird nur von dem Node mit der gleichen Machine-ID bedient

---

## 10. Lizenz

Der Code steht unter der **GNU General Public License Version 3 (GPLv3)**.

Wenn du das Projekt verwendest oder mithelfen möchtest, freue ich mich über eine Nachricht oder einen Pull Request. Viel Erfolg und Spaß damit!

