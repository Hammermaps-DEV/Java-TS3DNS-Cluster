-- phpMyAdmin SQL Dump
-- version 4.2.5
-- http://www.phpmyadmin.net
--
-- Host: 127.0.0.1:3306
-- Generation Time: Feb 16, 2015 at 07:39 PM
-- Server version: 10.0.12-MariaDB
-- PHP Version: 5.6.0

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";

--
-- Database: `ts3dns`
--

-- --------------------------------------------------------

--
-- Table structure for table `dns`
--

CREATE TABLE IF NOT EXISTS `dns` (
`id` int(11) NOT NULL,
  `dns` varchar(250) NOT NULL DEFAULT '',
  `ip` varchar(50) NOT NULL DEFAULT '000.000.000.000:0000',
  `default` int(1) NOT NULL DEFAULT '0',
  `machine-id` int(5) NOT NULL DEFAULT '0'
) ENGINE=InnoDB  DEFAULT CHARSET=latin1 AUTO_INCREMENT=3 ;

--
-- Dumping data for table `dns`
--

INSERT INTO `dns` (`id`, `dns`, `ip`, `default`, `machine-id`) VALUES
(1, 'reverse.privatedns.com', '123.123.123.123:2222', 0, 0),
(2, 'gggg.ggg1234.fr', '111.111.111.111:2346', 1, 0);

--
-- Indexes for dumped tables
--

--
-- Indexes for table `dns`
--
ALTER TABLE `dns`
 ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `dns`
--
ALTER TABLE `dns`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=3;