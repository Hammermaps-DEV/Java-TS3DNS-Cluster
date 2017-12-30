/*
Navicat MySQL Data Transfer

Source Server         : 89.163.243.8
Source Server Version : 100211
Source Host           : 89.163.243.8:3306
Source Database       : system_ts3dns

Target Server Type    : MYSQL
Target Server Version : 100211
File Encoding         : 65001

Date: 2017-12-30 14:22:51
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for dns
-- ----------------------------
DROP TABLE IF EXISTS `dns`;
CREATE TABLE `dns` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `dns` varchar(250) NOT NULL DEFAULT '',
  `name` varchar(200) NOT NULL DEFAULT '',
  `ip` varchar(50) NOT NULL DEFAULT '000.000.000.000:0000',
  `port` int(8) NOT NULL DEFAULT 0,
  `default` int(1) NOT NULL DEFAULT 0,
  `machine-id` int(5) NOT NULL DEFAULT 0,
  `server-id` int(11) NOT NULL DEFAULT 0,
  `vserver-id` int(11) NOT NULL DEFAULT 0,
  `slots` int(5) NOT NULL DEFAULT 100,
  `active_slots` int(11) NOT NULL DEFAULT 0,
  `lastused` int(11) NOT NULL,
  `usecount` int(11) NOT NULL,
  `failback_ip` varchar(25) NOT NULL DEFAULT '000.000.000.000:0000',
  `failback_port` int(8) NOT NULL DEFAULT 0,
  `failback` int(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `dns` (`dns`) USING BTREE,
  KEY `machine-id` (`machine-id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=latin1;

-- ----------------------------
-- Records of dns
-- ----------------------------
INSERT INTO `dns` VALUES ('1', '*', 'Test Server 1', '127.0.0.1', '9987', '0', '1', '1', '25', '200', '0', '0', '0', '123.123.123.123', '9987', '1');
INSERT INTO `dns` VALUES ('2', 'localhost', 'Test Server 1', '127.0.0.1', '9987', '1', '1', '1', '25', '200', '0', '0', '0', '123.123.123.123', '9987', '1');
INSERT INTO `dns` VALUES ('3', 'test2.localhost.net', 'Test Server 2', '127.0.0.1', '9989', '0', '1', '1', '9', '100', '0', '0', '0', '123.123.123.123', '9987', '1');
INSERT INTO `dns` VALUES ('4', 'test3.localhost.net', 'Test Server 3', '127.0.0.1', '9990', '0', '1', '1', '28', '80', '0', '0', '0', '123.123.123.123', '9987', '1');

-- ----------------------------
-- Table structure for servers
-- ----------------------------
DROP TABLE IF EXISTS `servers`;
CREATE TABLE `servers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ip` varchar(15) COLLATE utf8_unicode_ci NOT NULL DEFAULT '0.0.0.0',
  `port` int(8) NOT NULL DEFAULT 10011,
  `username` varchar(100) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `password` varchar(100) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `online` int(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- ----------------------------
-- Records of servers
-- ----------------------------
INSERT INTO `servers` VALUES ('1', '10.10.10.6', '10011', 'XXXXXXXXX', 'XXXXXXXXX', '0');
SET FOREIGN_KEY_CHECKS=1;
