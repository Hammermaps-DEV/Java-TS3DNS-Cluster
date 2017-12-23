-- phpMyAdmin SQL Dump
-- version 4.7.3
-- https://www.phpmyadmin.net/
--
-- Host: 192.168.1.1:3306
-- Erstellungszeit: 23. Dez 2017 um 19:19
-- Server-Version: 10.2.11-MariaDB-log
-- PHP-Version: 7.0.15

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";

--
-- Datenbank: `system_ts3dns`
--

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `dns`
--

CREATE TABLE `dns` (
  `id` int(11) NOT NULL,
  `dns` varchar(250) NOT NULL DEFAULT '',
  `name` varchar(200) NOT NULL DEFAULT '',
  `ip` varchar(50) NOT NULL DEFAULT '000.000.000.000:0000',
  `port` int(8) NOT NULL DEFAULT 0,
  `default` int(1) NOT NULL DEFAULT 0,
  `machine-id` int(5) NOT NULL DEFAULT 0,
  `server-id` int(11) NOT NULL DEFAULT 0,
  `vserver-id` int(11) NOT NULL DEFAULT 0,
  `slots` int(5) NOT NULL DEFAULT 100,
  `failback_ip` varchar(25) NOT NULL DEFAULT '000.000.000.000:0000',
  `failback_port` int(8) NOT NULL DEFAULT 0,
  `failback` int(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Daten für Tabelle `dns`
--

INSERT INTO `dns` (`id`, `dns`, `name`, `ip`, `port`, `default`, `machine-id`, `server-id`, `vserver-id`, `slots`, `failback_ip`, `failback_port`, `failback`) VALUES
(1, 'hammermaps.de', 'Hammermaps', '127.0.0.1', 9987, 1, 0, 1, 0, 100, '85.10.227.211', 9987, 1),
(2, 'ts.revoplay.de', 'Revoplay', '127.0.0.1', 9992, 0, 0, 1, 0, 100, '85.10.227.211', 9992, 1),
(3, 'dkf.hammermaps.de', 'DFK', '127.0.0.1', 9988, 0, 0, 1, 0, 100, '85.10.227.211', 9988, 1);

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `servers`
--

CREATE TABLE `servers` (
  `id` int(11) NOT NULL,
  `ip` varchar(15) COLLATE utf8_unicode_ci NOT NULL DEFAULT '0.0.0.0',
  `port` int(8) NOT NULL DEFAULT 10011,
  `username` varchar(100) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `password` varchar(100) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `online` int(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Daten für Tabelle `servers`
--

INSERT INTO `servers` (`id`, `ip`, `port`, `username`, `password`, `online`) VALUES
(1, '127.0.0.1', 10011, 'serveradmin', 'p6zhEXCi', 0);

--
-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `dns`
--
ALTER TABLE `dns`
  ADD PRIMARY KEY (`id`),
  ADD KEY `dns` (`dns`) USING BTREE,
  ADD KEY `machine-id` (`machine-id`) USING BTREE;

--
-- Indizes für die Tabelle `servers`
--
ALTER TABLE `servers`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT für exportierte Tabellen
--

--
-- AUTO_INCREMENT für Tabelle `dns`
--
ALTER TABLE `dns`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;
--
-- AUTO_INCREMENT für Tabelle `servers`
--
ALTER TABLE `servers`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;COMMIT;