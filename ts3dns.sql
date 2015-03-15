-- phpMyAdmin SQL Dump
-- version 4.2.5
-- http://www.phpmyadmin.net
--
-- Host: 127.0.0.1:3306
-- Generation Time: Mar 15, 2015 at 09:02 PM
-- Server version: 10.0.12-MariaDB
-- PHP Version: 5.6.0

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";

--
-- Database: `testdns`
--

-- --------------------------------------------------------

--
-- Table structure for table `dns`
--

CREATE TABLE IF NOT EXISTS `dns` (
`id` int(11) NOT NULL,
  `dns` varchar(250) NOT NULL DEFAULT '',
  `ip` varchar(50) NOT NULL DEFAULT '000.000.000.000:0000',
  `port` int(8) NOT NULL DEFAULT '0',
  `default` int(1) NOT NULL DEFAULT '0',
  `machine-id` int(5) NOT NULL DEFAULT '0',
  `server-id` int(11) NOT NULL DEFAULT '0',
  `vserver-id` int(11) NOT NULL DEFAULT '0',
  `failback_ip` varchar(25) NOT NULL DEFAULT '000.000.000.000:0000',
  `failback_port` int(8) NOT NULL DEFAULT '0',
  `failback` int(1) NOT NULL DEFAULT '0'
) ENGINE=InnoDB  DEFAULT CHARSET=latin1 AUTO_INCREMENT=3 ;

--
-- Dumping data for table `dns`
--

INSERT INTO `dns` (`id`, `dns`, `ip`, `port`, `default`, `machine-id`, `server-id`, `vserver-id`, `failback_ip`, `failback_port`, `failback`) VALUES
(1, 'reverse.privatedns.com', '123.123.123.123', 2222, 0, 0, 1, 0, '000.000.000.000', 1234, 1),
(2, 'gggg.ggg1234.fr', '111.111.111.111', 2346, 1, 0, 1, 0, '000.000.000.000', 5678, 1);

-- --------------------------------------------------------

--
-- Table structure for table `servers`
--

CREATE TABLE IF NOT EXISTS `servers` (
`id` int(11) NOT NULL,
  `ip` varchar(15) COLLATE utf8_unicode_ci NOT NULL DEFAULT '0.0.0.0',
  `port` int(8) NOT NULL DEFAULT '10011',
  `username` varchar(100) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `password` varchar(100) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `online` int(1) NOT NULL DEFAULT '0'
) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=2 ;

--
-- Dumping data for table `servers`
--

INSERT INTO `servers` (`id`, `ip`, `port`, `username`, `password`, `online`) VALUES
(1, '127.0.0.1', 10011, 'serveradmin', '+qKHUvEX', 0);

--
-- Indexes for dumped tables
--

--
-- Indexes for table `dns`
--
ALTER TABLE `dns`
 ADD PRIMARY KEY (`id`), ADD KEY `dns` (`dns`), ADD KEY `machine-id` (`machine-id`);

--
-- Indexes for table `servers`
--
ALTER TABLE `servers`
 ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `dns`
--
ALTER TABLE `dns`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=3;
--
-- AUTO_INCREMENT for table `servers`
--
ALTER TABLE `servers`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=2;