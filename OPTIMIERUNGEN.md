# Code-Analyse: Optimierungs-, Sicherheits- und Performance-Verbesserungen

**Projekt:** Java TS3-DNS Server Cluster v1.0 Beta  
**Analysiert am:** 2026-03-25  
**Analysierte Dateien:**
- `src/main/java/ts3dns/cluster/TS3DNSCluster.java`
- `src/main/java/ts3dns/cluster/TS3DNSClusterServer.java`
- `src/main/java/ts3dns/cluster/TS3DNSClusterMaster.java`
- `src/main/java/ts3dns/cluster/TS3DNSClusterSlave.java`
- `src/main/java/ts3dns/cluster/TS3DNSClusterPing.java`
- `src/main/java/ts3dns/client/TS3DNSClient.java`
- `src/main/java/ts3dns/client/TS3Updater.java`
- `src/main/java/ts3dns/database/MySQLDatabaseHandler.java`
- `src/main/java/ts3dns/server/TS3DNSServer.java`
- `release/TS3DNS-Cluster.cfg`
- `pom.xml`

---

## Inhaltsverzeichnis
1. [🔒 Sicherheitsprobleme (Security)](#1--sicherheitsprobleme-security)
2. [⚡ Performance-Probleme](#2--performance-probleme)
3. [🐛 Bugs & Fehlerbehandlung](#3--bugs--fehlerbehandlung)
4. [🧹 Code-Qualität & Wartbarkeit](#4--code-qualität--wartbarkeit)
5. [📦 Abhängigkeiten (Dependencies)](#5--abhängigkeiten-dependencies)
6. [Zusammenfassung](#zusammenfassung)

---

## 1. 🔒 Sicherheitsprobleme (Security)

### S-1: Datenbankzugangsdaten im Log sichtbar
**Datei:** `MySQLDatabaseHandler.java`, Zeile 36, 52

Die JDBC-URL wird mit Benutzername und Passwort als Query-Parameter zusammengebaut:
```java
this.url = "jdbc:mariadb://"+host+":"+port+"/"+db+"?user="+user+"&password="+pass;
```
Diese URL wird anschließend beim Verbindungsaufbau direkt in das Log geschrieben (Zeile 52):
```java
TS3DNSCluster.log(..., "Connect to MySQL Server: " + url, ...);
```
**Risiko:** Datenbankpasswörter erscheinen im Klartext in allen Log-Ausgaben.  
**Empfehlung:** `DriverManager.getConnection(url, user, pass)` mit separaten Parametern nutzen. URL nur mit Host/DB in den Logs ausgeben.

---

### S-2: Klartext-Passwörter in der Konfigurationsdatei
**Datei:** `release/TS3DNS-Cluster.cfg`, Zeile 14–15, 23–24

```properties
mysql_pass = xxxxxx
couchbase_password = xxxxxx
```
**Risiko:** Zugangsdaten sind für jeden Benutzer mit Lesezugriff auf die Datei einsehbar. Bei versehentlichem Einchecken ins Repository sind sie dauerhaft in der Git-History.  
**Empfehlung:** Passwörter über Umgebungsvariablen oder ein Secret-Management-System (z. B. HashiCorp Vault, systemd credentials) bereitstellen. Die Konfigurationsdatei sollte nur Platzhalter enthalten und aus der Versionsverwaltung ausgeschlossen sein.

---

### S-3: Thread-Safety – nicht synchronisierte statische Collections
**Datei:** `TS3DNSClusterServer.java`, Zeilen 58, 76; `TS3DNSServer.java`, Zeile 39

```java
private static final Map cache = new HashMap();       // Zeile 58
public static Map lock_update = new HashMap();        // Zeile 76
private static JsonDocument found = null;             // TS3DNSServer.java Zeile 39
```
Auf alle drei Felder greifen mehrere Threads gleichzeitig zu (je ein `TS3DNSClient`-Thread pro Verbindung, plus `TS3DNSServer`- und Ping-Threads).  
**Risiko:** Race Conditions, `ConcurrentModificationException`, inkonsistente Daten, schwer reproduzierbare Bugs.  
**Empfehlung:**
- `HashMap` durch `ConcurrentHashMap` ersetzen.
- `static JsonDocument found` als lokale Variable deklarieren, nicht als statisches Feld.

---

### S-4: Fehlende Eingabevalidierung für den DNS-Namen
**Datei:** `TS3DNSClient.java`, Zeile 81

```java
if(search_dns.length() <= 3 /*|| !search_dns.matches("...")*/) {
```
Die Regex-Validierung für gültige Domain-Namen ist auskommentiert.  
**Risiko:** Beliebige Zeichenketten (inkl. Sonderzeichen, sehr langer Input) werden als DNS-Name akzeptiert und in die Datenbank abgefragt.  
**Empfehlung:** Regex-Validierung wieder aktivieren und anpassen, z. B.:
```java
if(search_dns.length() <= 3 || !search_dns.matches("^[a-zA-Z0-9][a-zA-Z0-9\\-\\.]{1,253}[a-zA-Z0-9]$")) {
```

---

### S-5: Unbegrenzter Eingabe-Read – potenzielle DoS-Schwachstelle
**Datei:** `TS3DNSClient.java`, Methode `readUntilEnd()`, Zeile 282–293

```java
private String readUntilEnd() {
    StringBuilder sb = new StringBuilder();
    do {
        sb.append((char)input.read());
    }
    while(input.ready());
    return sb.toString();
}
```
Es gibt keine Begrenzung der gelesenen Datenmenge.  
**Risiko:** Ein böswilliger Client kann kontinuierlich Daten senden und so den Heap des Servers erschöpfen (Out-of-Memory / Denial of Service).  
**Empfehlung:** Maximale Eingabelänge begrenzen, z. B. nach 255 Zeichen abbrechen:
```java
if(sb.length() > 255) break;
```

---

### S-6: SQL-LIKE mit IP-Adresse – unbeabsichtigte Wildcard-Nutzung
**Datei:** `TS3DNSClient.java`, Zeile 175

```java
query = "SELECT `online`,`port`,`ip` FROM `servers` WHERE `ip` LIKE ?;";
```
Die IP-Adresse wird mit `LIKE` statt `=` verglichen.  
**Risiko:** Enthält die gespeicherte oder übergebene IP-Adresse Zeichen wie `%` oder `_`, wird der Vergleich als SQL-Wildcard-Muster interpretiert, was zu unerwarteten Treffern führt.  
**Empfehlung:** `=` statt `LIKE` verwenden:
```java
query = "SELECT `online`,`port`,`ip` FROM `servers` WHERE `ip` = ?;";
```

---

### S-7: Log-Injection über Benutzereingabe
**Datei:** `TS3DNSClient.java`, Zeilen 102–103, 108–109

```java
TS3DNSCluster.log(..., "Search IP/Port for DNS: '" + search_dns + "'", ...);
```
Der vom Client empfangene Wert `search_dns` wird ungefiltert in Log-Ausgaben geschrieben.  
**Risiko:** Ein Angreifer kann Newlines (`\n`, `\r`) oder andere Steuerzeichen in die Eingabe einbetten, um gefälschte Log-Einträge zu erzeugen (Log Injection/Forging).  
**Empfehlung:** Eingaben vor dem Logging bereinigen (Zeilenumbrüche und Steuerzeichen entfernen):
```java
String safeDns = search_dns.replaceAll("[\r\n\t]", "_");
```

---

### S-8: Falsche Logik in `MySQLDatabaseHandler.close()`
**Datei:** `MySQLDatabaseHandler.java`, Zeilen 44–48

```java
public void close() throws SQLException {
    if(connection.isClosed()) {      // BUG: sollte !connection.isClosed() sein
        connection.close();
    }
}
```
**Risiko:** Die Datenbankverbindung wird **niemals** geschlossen (nur dann, wenn sie bereits geschlossen ist). Connection-Leaks entstehen bei jedem Shutdown.  
**Empfehlung:**
```java
if(connection != null && !connection.isClosed()) {
    connection.close();
}
```

---

### S-9: NullPointerException bei Couchbase-Deaktivierung
**Datei:** `TS3DNSClusterServer.java`, Zeile 213

```java
public void stop() {
    ...
    cluster.disconnect();   // cluster ist null wenn cb_enabled = false
}
```
**Risiko:** Wird die Anwendung gestoppt und Couchbase ist deaktiviert, wirft `stop()` eine `NullPointerException` und unterbricht den gesamten Shutdown-Hook.  
**Empfehlung:**
```java
if(cluster != null) {
    cluster.disconnect();
}
```

---

### S-10: Falsche Null-Check-Reihenfolge
**Datei:** `TS3DNSCluster.java`, Zeile 65

```java
if(!properties.isEmpty() && properties != null) {
```
**Risiko:** `properties.isEmpty()` wird vor dem Null-Check aufgerufen. Ist `properties` null, entsteht eine `NullPointerException`.  
**Empfehlung:** Reihenfolge tauschen:
```java
if(properties != null && !properties.isEmpty()) {
```

---

### S-11: FileInputStream ohne try-with-resources – Resource Leak
**Datei:** `TS3DNSCluster.java`, Zeile 64

```java
properties.load(new FileInputStream(confFile));
```
Der `FileInputStream` wird nach dem Laden nicht geschlossen (kein try-with-resources, kein explizites `close()`).  
**Risiko:** File-Descriptor-Leak.  
**Empfehlung:**
```java
try (FileInputStream fis = new FileInputStream(confFile)) {
    properties.load(fis);
}
```

---

## 2. ⚡ Performance-Probleme

### P-1: Thread-per-Connection – kein Thread-Pool
**Datei:** `TS3DNSClusterServer.java`, Zeilen 198–200

```java
tS3DNSClient = new TS3DNSClient(client, mysql, default_ip, default_port);
tS3DNSClient.start();
```
Für jede eingehende Verbindung wird ein neuer OS-Thread erstellt.  
**Problem:** Unter hoher Last (viele simultane DNS-Anfragen) entstehen hunderte Threads, was zu hohem Speicherverbrauch und Context-Switch-Overhead führt. Ab ca. 500–1000 Threads wird das System instabil.  
**Empfehlung:** Thread-Pool mit `ExecutorService` verwenden:
```java
private final ExecutorService threadPool = Executors.newCachedThreadPool();
// oder mit fixem Limit:
private final ExecutorService threadPool = Executors.newFixedThreadPool(100);

// Statt tS3DNSClient.start():
threadPool.submit(new TS3DNSClient(client, mysql, default_ip, default_port));
```

---

### P-2: Kein Connection-Pooling für MySQL
**Datei:** `MySQLDatabaseHandler.java`

Es wird nur eine einzelne JDBC-Verbindung für alle Anfragen gehalten. Bei mehreren parallelen Client-Threads konkurrieren alle um diese eine Verbindung.  
**Problem:** Serialisierung aller Datenbankzugriffe, keine Parallelität möglich.  
**Empfehlung:** Connection-Pool verwenden (z. B. HikariCP):
```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>
```
```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:mariadb://host:port/db");
config.setMaximumPoolSize(20);
HikariDataSource ds = new HikariDataSource(config);
```

---

### P-3: Busy-Wait-Schleifen in Hintergrund-Threads
**Dateien:** `TS3DNSClusterMaster.java` (Zeile 56), `TS3DNSClusterSlave.java` (Zeile 39), `TS3DNSServer.java` (Zeile 68)

```java
// In TS3DNSClusterMaster:
Thread.sleep(500);   // 2x pro Sekunde Couchbase pollen

// In TS3DNSClusterSlave:
Thread.sleep(1000);  // 1x pro Sekunde Couchbase schreiben

// In TS3DNSServer:
Thread.sleep(4000);  // Pause zwischen Server-Checks
```
Alle drei Threads laufen in `while(true)`-Schleifen und blockieren mit `sleep()`.  
**Problem:** Unstrukturiert, schwer zu stoppen, kein sauberer Shutdown möglich.  
**Empfehlung:** `ScheduledExecutorService` verwenden:
```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(this::checkSlaves, 0, 500, TimeUnit.MILLISECONDS);
```

---

### P-4: Veraltete Cache-Einträge werden nicht entfernt – Memory Leak
**Datei:** `TS3DNSClusterServer.java`, Zeile 247

```java
if(((int)data.get("time") + 10) <= currentTimestamp) return false;
return cache.containsKey(key);
```
Wenn ein Cache-Eintrag abgelaufen ist, wird `false` zurückgegeben, aber der Eintrag verbleibt in der `HashMap`.  
**Problem:** Bei hohem Traffic wächst die Cache-Map unbegrenzt, da abgelaufene Einträge nie entfernt werden → Memory Leak.  
**Empfehlung:** Abgelaufene Einträge beim Erkennen entfernen:
```java
if(((int)data.get("time") + 10) <= currentTimestamp) {
    cache.remove(key);
    return false;
}
```
Alternativ: Caffeine oder Guava Cache mit TTL-Support verwenden.

---

### P-5: Wiederholte Property-Lesezugriffe in Schleifen
In nahezu jeder Methode wird `Boolean.parseBoolean(properties.getProperty("default_debug"))` aufgerufen – teilweise in Schleifen, die hundertfach pro Sekunde ausgeführt werden.

**Problem:** Obwohl der Overhead gering ist, summiert er sich bei hoher Last unnötig.  
**Empfehlung:** Beim Start cachen:
```java
// In TS3DNSCluster oder TS3DNSClusterServer:
public static boolean DEBUG = false;

// Beim Laden:
DEBUG = Boolean.parseBoolean(properties.getProperty("default_debug"));
```

---

### P-6: Falsche JDBC-Methode für UPDATE/DML-Statements
**Dateien:** `TS3DNSClient.java` (Zeilen 221, 117), `TS3Updater.java` (Zeile 114), `TS3DNSClusterPing.java` (Zeile 117)

```java
stmt.executeQuery();  // wird für UPDATE-Statements verwendet
```
`executeQuery()` ist für SELECT-Statements gedacht und erwartet ein `ResultSet`. Für INSERT/UPDATE/DELETE ist `executeUpdate()` korrekt.  
**Problem:** Viele JDBC-Treiber werfen bei UPDATE mit `executeQuery()` eine Exception oder verhalten sich undefiniert.  
**Empfehlung:**
```java
stmt.executeUpdate();  // für INSERT, UPDATE, DELETE
```

---

### P-7: Neue Ping-Threads überlappen sich
**Datei:** `TS3DNSServer.java`, Zeilen 61–66

```java
TS3DNSClusterPing ping = new TS3DNSClusterPing(...);
ping.start();
Thread.sleep(4000);
```
Bei jedem Durchlauf werden neue Ping-Threads für jeden Server gestartet, unabhängig davon, ob der vorherige Ping-Thread für diesen Server noch läuft.  
**Problem:** Mehrere parallele Pings zum selben Server; bei langsamen Servern können sich Threads aufstauen.  
**Empfehlung:** Prüfen, ob der vorherige Thread noch aktiv ist, oder `ScheduledExecutorService` mit `scheduleWithFixedDelay` verwenden.

---

### P-8: Unnötige StringBuilder-Nutzung für einfache Strings
Im gesamten Codebase werden Konstrukte wie folgt genutzt:
```java
(new StringBuilder("text ")).append(value).toString()
```
Für kurze, einmalige String-Konkatenationen ist das unnötig – seit Java 8 optimiert der Compiler `"text " + value` identisch.  
**Empfehlung:** Für Lesbarkeit `String.format()` oder einfache Konkatenation nutzen. `StringBuilder` nur für Schleifen verwenden.

---

## 3. 🐛 Bugs & Fehlerbehandlung

### B-1: Doppeltes Schließen von `input` in TS3DNSClient
**Datei:** `TS3DNSClient.java`, Zeilen 85–90

```java
if(!input.ready())
    input.close();          // erstes close()

if(input != null)           // zu spät: input wurde bereits verwendet
    input.close();          // zweites close()
```
`input.ready()` wird aufgerufen **bevor** auf `null` geprüft wird. Ist `input` null, entsteht sofort eine NPE. Danach wird `input.close()` zweimal aufgerufen.  
**Empfehlung:** try-with-resources verwenden und einmal schließen.

---

### B-2: ResultSet `rs` außerhalb von try-with-resources deklariert
**Datei:** `TS3DNSClient.java`, Zeilen 113–134

```java
ResultSet rs;
try (PreparedStatement stmt = this.mysql.prepare(query)) {
    rs = stmt.executeQuery();
    ...
    rs.close();  // manuell, aber nur im Erfolgsfall
}
```
Wirft `rs.next()` oder eine andere Methode eine Exception, wird `rs` nicht geschlossen → Resource Leak.  
**Empfehlung:**
```java
try (PreparedStatement stmt = this.mysql.prepare(query);
     ResultSet rs = stmt.executeQuery()) {
    ...
}
```

---

### B-3: Redundantes `stmt.close()` innerhalb von try-with-resources
**Datei:** `TS3DNSServer.java`, Zeile 71; `TS3DNSClient.java`, Zeilen 132–133

```java
try (PreparedStatement stmt = ...; ResultSet rs = ...) {
    ...
    stmt.close();   // redundant, try-with-resources schließt bereits
}
```
**Empfehlung:** Redundante `close()`-Aufrufe entfernen.

---

### B-4: Fehlender Socket-Close bei Exception in TS3DNSClusterPing
**Datei:** `TS3DNSClusterPing.java`, Zeilen 64–66

```java
socket = new Socket();
socket.connect(new InetSocketAddress(ip, port), timeout);
socket.close();
```
Wird die Verbindung hergestellt (`connect` erfolgreich) und danach eine Exception geworfen, wird `socket.close()` nicht aufgerufen → Resource Leak.  
**Empfehlung:** try-with-resources verwenden:
```java
try (Socket socket = new Socket()) {
    socket.connect(new InetSocketAddress(ip, port), timeout);
}
```

---

### B-5: `stop()`-Methode in TS3DNSClusterServer überschreibt Thread-Methode
**Datei:** `TS3DNSClusterServer.java`, Zeile 208

`TS3DNSClusterServer` ist kein `Thread`, hat aber eine Methode `stop()`. Der Name kollidiert mit `Thread.stop()` und ist irreführend.  
**Empfehlung:** Methode in `shutdown()` umbenennen.

---

### B-6: Hintergrund-Threads haben keinen kontrollierten Stop-Mechanismus
**Dateien:** `TS3DNSClusterMaster.java`, `TS3DNSClusterSlave.java`, `TS3DNSServer.java`

Alle laufen in `while(true)` ohne Interrupt-Unterstützung:
```java
} catch (InterruptedException ex) { }  // Interrupt wird ignoriert!
```
**Risiko:** Der Shutdown-Hook kann die Threads nicht sauber beenden.  
**Empfehlung:**
```java
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    break;
}
```
Und `while(!Thread.currentThread().isInterrupted())` statt `while(true)`.

---

## 4. 🧹 Code-Qualität & Wartbarkeit

### Q-1: Raw Types statt parametrisierter Generics
Im gesamten Codebase werden raw types verwendet:
```java
Map data = new HashMap();                       // sollte Map<String, Object> sein
Map<String, Object> map = data;                 // TS3DNSClusterMaster.java, Zeile 42
```
**Problem:** Keine Compile-Zeit-Typsicherheit, explizite Casts nötig, potenzielle `ClassCastException`.  
**Empfehlung:** Generics konsequent nutzen: `Map<String, Object>`, `HashMap<String, Object>`.

---

### Q-2: Logging-Framework komplett auskommentiert
**Datei:** `TS3DNSCluster.java`, Zeilen 75–81

```java
public static void log(String classn, Level lvl, String msg, boolean error) {
  //  if(Boolean.parseBoolean(properties.getProperty("default_debug")) || error) {
  //      Logger.getLogger(classn).log(lvl, msg);
  //  } else {
        System.out.println(msg);
  //  }
}
```
Alle Log-Ausgaben gehen an `System.out`, unabhängig vom Log-Level. Fehler (`SEVERE`) werden gleich behandelt wie Debug-Meldungen.  
**Empfehlung:** SLF4J mit Logback (bereits als Abhängigkeit vorhanden) oder `java.util.logging` korrekt konfigurieren und nutzen. Log-Level-Filterung implementieren.

---

### Q-3: Toter Code (auskommentierte Abschnitte)
Mehrere auskommentierte Codeblöcke, die nicht mehr benötigt werden:
- `TS3DNSClient.java`, Zeile 81: Domain-Regex-Validierung
- `TS3DNSClusterPing.java`, Zeile 87: `sendMSG`-Aufruf  
- `TS3DNSClusterServer.java`, Zeile 207: `//this.common.sendMSG(...)`
- `MySQLDatabaseHandler.java`, Zeilen 60, 70: Query-Logging

**Empfehlung:** Toten Code entfernen oder mit einem TODO-Kommentar versehen.

---

### Q-4: Hardcodierter Konfigurationsdateiname
**Datei:** `TS3DNSCluster.java`, Zeile 34

```java
public static String configFile = "TS3DNS-Cluster.cfg";
```
**Problem:** Kein Support für unterschiedliche Umgebungen (Dev/Prod), kein Kommandozeilenargument möglich.  
**Empfehlung:** Kommandozeilenargument auswerten:
```java
if(args.length > 0) {
    configFile = args[0];
}
```

---

### Q-5: Tippfehler im Konfigurationsschlüssel
**Datei:** `TS3DNS-Cluster.cfg`, Zeile 10; `TS3DNSClusterServer.java` u. a.

```properties
default_send_massages = 1   # sollte "messages" sein
```
**Empfehlung:** `default_send_massages` → `default_send_messages` umbenennen (inkl. aller Referenzen im Code).

---

### Q-6: `@Override`-Annotation fehlt
**Dateien:** `TS3DNSClusterMaster.java` (Zeile 34), `TS3DNSClusterSlave.java` (Zeile 28)

```java
public void run() {  // kein @Override
```
**Empfehlung:** `@Override` hinzufügen – ermöglicht Compile-Zeit-Prüfung.

---

### Q-7: Konfigurationsklasse `TS3DNSCluster.properties` ist `public` und mutable
**Datei:** `TS3DNSCluster.java`, Zeile 32

```java
public static Properties properties = null;
```
Das Properties-Objekt ist global `public` und kann von überall verändert werden.  
**Empfehlung:** `private static` mit einem Getter:
```java
private static Properties properties = null;
public static String getProperty(String key) { return properties.getProperty(key); }
```

---

### Q-8: Inkonsistente Fehlerbehandlung
In einigen Catch-Blöcken werden Exceptions kommentarlos verschluckt:
```java
} catch(IOException exception) {}               // TS3DNSClient.java, Zeile 96
} catch (InterruptedException ex) { }           // TS3DNSClusterMaster.java, Zeile 57
} catch (SQLException ex) {}                    // TS3DNSClusterServer.java, Zeile 212
```
**Empfehlung:** Mindestens immer loggen:
```java
} catch(IOException e) {
    TS3DNSCluster.log(..., Level.WARNING, "Fehler beim Schließen: " + e.getMessage(), false);
}
```

---

### Q-9: Keine Unit-Tests vorhanden
Das Projekt enthält keinerlei Tests (`src/test/` ist leer).  
**Empfehlung:** Zumindest Tests für:
- `MySQLDatabaseHandler` (Verbindungslogik mit Mockito)
- `TS3DNSClusterServer.existsCache()` / `setCache()` / `getCache()`
- DNS-Lookup-Logik in `TS3DNSClient`

---

## 5. 📦 Abhängigkeiten (Dependencies)

### D-1: Veraltete Abhängigkeiten mit bekannten Sicherheitslücken
**Datei:** `pom.xml`

| Abhängigkeit | Aktuelle Version | Empfohlen | Hinweis |
|---|---|---|---|
| `couchbase-client 2.5.3` | veraltet (EOL) | `3.7.x` | Komplett neue API, erhebliche Sicherheitsverbesserungen |
| `mariadb-java-client-jre7 1.6.1` | veraltet | `3.3.x` | Enthält bekannte CVEs; jre7-Variante seit Jahren nicht mehr gepflegt |
| `slf4j-simple 1.7.25` | veraltet | `2.0.x` | Log4Shell-nahe Verbesserungen, API-Änderungen |
| `teamspeak3-api 1.2.0-SNAPSHOT` | SNAPSHOT | `1.3.0+` / Release | SNAPSHOT-Dependencies sind in Produktionscode inakzeptabel |

**Empfehlung:** Alle Abhängigkeiten auf aktuelle, stabile Release-Versionen aktualisieren.

---

### D-2: SNAPSHOT-Dependency in Produktion
**Datei:** `pom.xml`, Zeile 70

```xml
<version>1.2.0-SNAPSHOT</version>
```
SNAPSHOT-Versionen sind instabile Entwicklungsversionen. Bei jedem Build kann sich der Inhalt ändern → nicht reproduzierbare Builds.  
**Empfehlung:** Ausschließlich Release-Versionen verwenden.

---

### D-3: Java-Zielversion veraltet
**Datei:** `pom.xml`, Zeilen 74–76

```xml
<maven.compiler.source>1.8</maven.compiler.source>
<maven.compiler.target>1.8</maven.compiler.target>
```
Java 8 (1.8) ist seit 2019 außerhalb des öffentlichen Supports (Oracle). Aktuelle LTS-Version ist Java 21.  
**Empfehlung:** Migration auf Java 17 oder 21 LTS ermöglicht modernere Sprachfeatures und Sicherheitsverbesserungen.

---

## Zusammenfassung

| Kategorie | Anzahl Probleme | Kritisch |
|---|---|---|
| 🔒 Sicherheit | 11 | 4 (S-1, S-2, S-3, S-5) |
| ⚡ Performance | 8 | 3 (P-1, P-2, P-4) |
| 🐛 Bugs & Fehlerbehandlung | 6 | 2 (B-2, B-6) |
| 🧹 Code-Qualität | 9 | 2 (Q-1, Q-2) |
| 📦 Abhängigkeiten | 3 | 2 (D-1, D-2) |
| **Gesamt** | **37** | **13** |

### Wichtigste Sofortmaßnahmen (nach Priorität)

1. **[S-3]** `HashMap` durch `ConcurrentHashMap` ersetzen (Race Conditions / Datenverlust unter Last)
2. **[S-1]** Passwörter nicht in die JDBC-URL und nicht in Logs schreiben
3. **[P-1]** Thread-Pool einführen (Schutz vor Thread-Exhaustion)
4. **[P-2]** MySQL Connection-Pool einführen (Stabilität unter Last)
5. **[S-5]** Maximale Eingabelänge in `readUntilEnd()` begrenzen (DoS-Schutz)
6. **[B-8/S-8]** `MySQLDatabaseHandler.close()` – invertierte Logik korrigieren
7. **[S-9]** NPE in `TS3DNSClusterServer.stop()` beheben
8. **[D-1]** Veraltete Abhängigkeiten aktualisieren (insb. `mariadb-java-client`)
9. **[P-4]** Abgelaufene Cache-Einträge beim Erkennen entfernen (Memory Leak)
10. **[Q-2]** Logging-Framework korrekt konfigurieren und aktivieren
