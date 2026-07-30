-- Salvează ca: sql/init.sql

CREATE DATABASE IF NOT EXISTS `warframe_foundry`;
USE `warframe_foundry`;

-- Dezactivăm temporar verificarea FK pentru curățare
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `blueprint_requirements`;
DROP TABLE IF EXISTS `user_blueprints`;
DROP TABLE IF EXISTS `user_items`;
DROP TABLE IF EXISTS `mission_rewards`;
DROP TABLE IF EXISTS `user_missions`;
DROP TABLE IF EXISTS `blueprints`;
DROP TABLE IF EXISTS `items`;
DROP TABLE IF EXISTS `item_categories`;
DROP TABLE IF EXISTS `login_attempts`;
DROP TABLE IF EXISTS `users`;

SET FOREIGN_KEY_CHECKS = 1;

-- 1. users
CREATE TABLE `users` (
                         `id` int NOT NULL AUTO_INCREMENT,
                         `username` varchar(50) NOT NULL,
                         `password` varchar(255) NOT NULL,
                         `email` varchar(100) NOT NULL,
                         `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                         `is_logged_in` tinyint(1) DEFAULT '0',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `username` (`username`),
                         UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. login_attempts
CREATE TABLE `login_attempts` (
                                  `device_id` varchar(255) NOT NULL,
                                  `failed_attempts` int DEFAULT '0',
                                  `blocked_until` datetime DEFAULT NULL,
                                  PRIMARY KEY (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. item_categories
CREATE TABLE `item_categories` (
                                   `id` int NOT NULL AUTO_INCREMENT,
                                   `name` varchar(50) NOT NULL,
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. items
CREATE TABLE `items` (
                         `id` int NOT NULL AUTO_INCREMENT,
                         `category_id` int NOT NULL,
                         `name` varchar(100) NOT NULL,
                         `description` text,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `name` (`name`),
                         KEY `category_id` (`category_id`),
                         CONSTRAINT `items_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `item_categories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5. blueprints
CREATE TABLE `blueprints` (
                              `id` int NOT NULL AUTO_INCREMENT,
                              `name` varchar(100) NOT NULL,
                              `result_item_id` int NOT NULL,
                              `build_time_seconds` int NOT NULL DEFAULT '43200',
                              `credit_cost` int NOT NULL DEFAULT '15000',
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `name` (`name`),
                              KEY `result_item_id` (`result_item_id`),
                              CONSTRAINT `blueprints_ibfk_1` FOREIGN KEY (`result_item_id`) REFERENCES `items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. blueprint_requirements
CREATE TABLE `blueprint_requirements` (
                                          `blueprint_id` int NOT NULL,
                                          `required_item_id` int NOT NULL,
                                          `required_quantity` int NOT NULL,
                                          PRIMARY KEY (`blueprint_id`,`required_item_id`),
                                          KEY `required_item_id` (`required_item_id`),
                                          CONSTRAINT `blueprint_requirements_ibfk_1` FOREIGN KEY (`blueprint_id`) REFERENCES `blueprints` (`id`) ON DELETE CASCADE,
                                          CONSTRAINT `blueprint_requirements_ibfk_2` FOREIGN KEY (`required_item_id`) REFERENCES `items` (`id`) ON DELETE CASCADE,
                                          CONSTRAINT `blueprint_requirements_chk_1` CHECK ((`required_quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 7. user_items
CREATE TABLE `user_items` (
                              `user_id` int NOT NULL,
                              `item_id` int NOT NULL,
                              `quantity` int NOT NULL DEFAULT '0',
                              PRIMARY KEY (`user_id`,`item_id`),
                              KEY `item_id` (`item_id`),
                              CONSTRAINT `user_items_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
                              CONSTRAINT `user_items_ibfk_2` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ON DELETE CASCADE,
                              CONSTRAINT `user_items_chk_1` CHECK ((`quantity` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 8. user_blueprints
CREATE TABLE `user_blueprints` (
                                   `user_id` int NOT NULL,
                                   `blueprint_id` int NOT NULL,
                                   `quantity` int NOT NULL DEFAULT '1',
                                   `is_crafted` tinyint(1) NOT NULL DEFAULT '0',
                                   `crafting_finished_at` datetime DEFAULT NULL,
                                   PRIMARY KEY (`user_id`,`blueprint_id`),
                                   KEY `blueprint_id` (`blueprint_id`),
                                   CONSTRAINT `user_blueprints_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
                                   CONSTRAINT `user_blueprints_ibfk_2` FOREIGN KEY (`blueprint_id`) REFERENCES `blueprints` (`id`) ON DELETE CASCADE,
                                   CONSTRAINT `user_blueprints_chk_1` CHECK ((`quantity` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 9. user_missions
CREATE TABLE `user_missions` (
                                 `id` varchar(64) NOT NULL,
                                 `user_id` int NOT NULL,
                                 `slot_index` int NOT NULL,
                                 `name` varchar(255) NOT NULL,
                                 `location` varchar(255) NOT NULL,
                                 `duration_minutes` int NOT NULL,
                                 `state` varchar(32) NOT NULL,
                                 `finished_at` datetime DEFAULT NULL,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uq_user_slot` (`user_id`,`slot_index`),
                                 CONSTRAINT `user_missions_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 10. mission_rewards
CREATE TABLE `mission_rewards` (
                                   `mission_id` varchar(64) NOT NULL,
                                   `item_id` int NOT NULL,
                                   `quantity` bigint NOT NULL,
                                   PRIMARY KEY (`mission_id`,`item_id`),
                                   KEY `item_id` (`item_id`),
                                   CONSTRAINT `mission_rewards_ibfk_1` FOREIGN KEY (`mission_id`) REFERENCES `user_missions` (`id`) ON DELETE CASCADE,
                                   CONSTRAINT `mission_rewards_ibfk_2` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =================================================================
-- METADATE GENERALE PENTRU JOC (FĂRĂ UTILIZATORI)
-- =================================================================

-- Categorii
INSERT INTO `item_categories` (`id`, `name`) VALUES
                                                 (1, 'RESOURCE'),
                                                 (2, 'PRIMARY'),
                                                 (3, 'MELEE'),
                                                 (4, 'SECONDARY'),
                                                 (5, 'COMPONENT');

-- Iteme (Resurse & Arme)
INSERT INTO `items` (`id`, `category_id`, `name`, `description`) VALUES
                                                                     (1, 1, 'Credits', 'In-game currency used for crafting and trading.'),
                                                                     (2, 1, 'Orokin Cell', 'Rare resource used in Prime items.'),
                                                                     (3, 1, 'Plastids', 'Common organic material.'),
                                                                     (4, 1, 'Rubedo', 'Crystalline resource.'),
                                                                     (5, 2, 'Boar Prime', 'High-damage Prime shotgun.'),
                                                                     (6, 3, 'Afuris Prime', 'Dual automatic Prime pistols.');

-- Schițe (Blueprints)
INSERT INTO `blueprints` (`id`, `name`, `result_item_id`, `build_time_seconds`, `credit_cost`) VALUES
                                                                                                   (1, 'Boar Prime Blueprint', 5, 120, 15000),
                                                                                                   (2, 'Afuris Prime Blueprint', 6, 180, 20000);

-- Cerințe pentru schițe (Requirements)
INSERT INTO `blueprint_requirements` (`blueprint_id`, `required_item_id`, `required_quantity`) VALUES
                                                                                                   (1, 2, 3),   -- Boar Prime cere 3 Orokin Cells
                                                                                                   (1, 3, 500), -- Boar Prime cere 500 Plastids
                                                                                                   (2, 2, 5),   -- Afuris Prime cere 5 Orokin Cells
                                                                                                   (2, 4, 1000);-- Afuris Prime cere 1000 Rubedo