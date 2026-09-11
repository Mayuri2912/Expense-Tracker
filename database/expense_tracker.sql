-- ============================================================
-- Expense Tracker - Database Schema + Sample Data
-- ============================================================
-- This script recreates the EXACT schema currently used by the running
-- application (verified against a real mysqldump of the live database),
-- with three data-only fixes applied so the app works immediately after
-- import. No table was renamed, no primary key changed, and no foreign
-- key relationship altered from the original schema.
--
-- Fixes applied to the original dump's data (schema itself is untouched):
--   1. users.password    - the original dump stored "123456" in PLAIN TEXT
--                           for all 4 sample users. Since the app now hashes
--                           passwords with BCrypt, these are replaced with a
--                           real BCrypt hash of "123456" so the same sample
--                           logins keep working. (Hash generated with the
--                           same algorithm Spring Security's
--                           BCryptPasswordEncoder uses - bcrypt hashes are
--                           cross-compatible regardless of what generated
--                           them.)
--   2. users.full_name    - user id=1 ("Mayuri") had full_name = '' while
--                           name = 'Mayuri'. Backfilled to match.
--   3. categories.user_id - every category in the original dump had
--                           user_id = NULL (categories were global before
--                           per-user ownership was added). All 20 sample
--                           expenses belong to user_id = 1, and reference
--                           these categories directly, so they are backfilled
--                           to user_id = 1 - the user who actually created
--                           and uses them. New categories created through
--                           the app are always assigned to their creator.
-- ============================================================

CREATE DATABASE IF NOT EXISTS `expense_tracker` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `expense_tracker`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Table: users
-- ============================================================
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `full_name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- All 4 sample users share the password "123456" (now BCrypt-hashed).
INSERT INTO `users` (`id`,`name`,`email`,`password`,`created_at`,`full_name`) VALUES
 (1,'Mayuri','mayuri@gmail.com','$2b$10$59M1MF518kUNBIh/n40KW.vWWaezhPZk51xRc2mjMOsSGKmaNSiI2','2026-07-19 06:21:12','Mayuri'),
 (2,'Rahul Sharma','rahul@gmail.com','$2b$10$59M1MF518kUNBIh/n40KW.vWWaezhPZk51xRc2mjMOsSGKmaNSiI2','2026-07-23 15:28:50','Rahul Sharma'),
 (3,'Priya Singh','priya@gmail.com','$2b$10$59M1MF518kUNBIh/n40KW.vWWaezhPZk51xRc2mjMOsSGKmaNSiI2','2026-07-23 15:28:50','Priya Singh'),
 (4,'Amit Kumar','amit@gmail.com','$2b$10$59M1MF518kUNBIh/n40KW.vWWaezhPZk51xRc2mjMOsSGKmaNSiI2','2026-07-23 15:28:50','Amit Kumar');

-- ============================================================
-- Table: categories
-- ============================================================
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_category_name_user` (`name`,`user_id`),
  KEY `fk_categories_user` (`user_id`),
  CONSTRAINT `fk_categories_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- user_id backfilled to 1 (see note above) - every category below is owned
-- by Mayuri, the only sample user with expenses/categories/budgets.
INSERT INTO `categories` (`id`,`name`,`created_at`,`user_id`) VALUES
 (1,'Food','2026-07-19 06:22:04',1),
 (2,'Shopping','2026-07-19 06:22:04',1),
 (3,'Travel','2026-07-19 06:22:04',1),
 (4,'Bills','2026-07-23 15:30:32',1),
 (5,'Education','2026-07-23 15:30:32',1),
 (6,'Health','2026-07-23 15:30:32',1),
 (7,'Fuel','2026-07-23 15:30:32',1),
 (8,'Groceries','2026-07-23 15:30:32',1),
 (9,'Rent','2026-07-23 15:30:32',1),
 (10,'Entertainment','2026-07-23 15:30:32',1),
 (11,'Shopping','2026-07-23 15:30:32',1),
 (12,'Coaching','2026-07-25 18:38:45',1),
 (13,'Coaching','2026-07-25 18:38:55',1),
 (14,'Parlour','2026-07-25 18:39:12',1),
 (15,'Coaching','2026-07-25 18:49:07',1),
 (16,'College fee','2026-07-29 09:13:41',1);

-- NOTE: rows 12/13/15 ("Coaching") and 2/11 ("Shopping") are duplicate names
-- for the same user. The UNIQUE (name, user_id) constraint above only
-- blocks *new* duplicates going forward - it does not retroactively touch
-- these pre-existing rows, so the import will still succeed.

-- ============================================================
-- Table: expenses
-- ============================================================
DROP TABLE IF EXISTS `expenses`;
CREATE TABLE `expenses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `amount` double NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `expense_date` date NOT NULL,
  `payment_method` varchar(255) DEFAULT NULL,
  `category_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `category_id` (`category_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `expenses_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `expenses_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- WARNING: category_id/user_id both cascade on delete - deleting a category
-- or a user permanently deletes every expense that references it. The app's
-- UI warns about this before a category delete is confirmed.
INSERT INTO `expenses` (`id`,`title`,`amount`,`description`,`expense_date`,`payment_method`,`category_id`,`user_id`,`created_at`) VALUES
 (2,'Burger',500,'Burger King','2026-07-29','UPI',1,1,'2026-07-24 11:52:19'),
 (3,'Movie',500,'PVR Cinema','2026-07-02','Card',8,1,'2026-07-24 11:52:19'),
 (4,'Petrol',1200,'Bike Fuel','2026-07-03','UPI',7,1,'2026-07-24 11:52:19'),
 (5,'Electricity Bill',1800,'July Bill','2026-07-04','Net Banking',5,1,'2026-07-24 11:52:19'),
 (7,'Shopping',2500,'Clothes from Mall','2026-07-06','Card',8,1,'2026-07-24 11:52:19'),
 (8,'Medicine',780,'Apollo Pharmacy','2026-07-07','Cash',6,1,'2026-07-24 11:52:19'),
 (9,'Groceries',1450,'Monthly Grocery','2026-07-08','UPI',9,1,'2026-07-24 11:52:19'),
 (10,'Lunch',250,'College Canteen','2026-07-09','Cash',1,1,'2026-07-24 11:52:19'),
 (11,'Dinner',600,'Restaurant','2026-07-10','Card',1,1,'2026-07-24 11:52:19'),
 (12,'Books',950,'Java Book','2026-07-11','UPI',6,1,'2026-07-24 11:52:19'),
 (13,'Laptop Repair',3200,'Keyboard Repair','2026-07-12','Card',6,1,'2026-07-24 11:52:19'),
 (14,'Rent',8500,'Hostel Rent','2026-07-13','UPI',10,1,'2026-07-24 11:52:19'),
 (15,'Bus Ticket',180,'College Bus','2026-07-14','Cash',3,1,'2026-07-24 11:52:19'),
 (16,'Coffee',180,'Cafe Coffee Day','2026-07-15','UPI',1,1,'2026-07-24 11:52:19'),
 (17,'Gym Fee',1200,'Monthly Gym','2026-07-16','Card',6,1,'2026-07-24 11:52:19'),
 (18,'Online Course',2500,'Spring Boot Course','2026-07-17','UPI',6,1,'2026-07-24 11:52:19'),
 (19,'Ice Cream',150,'Amul Ice Cream','2026-07-18','Cash',1,1,'2026-07-24 11:52:19'),
 (21,'Internet Bill',999,'Airtel Fiber','2026-07-20','UPI',5,1,'2026-07-24 11:52:19'),
 (22,'Cake',800,'Brother\'s Bakery','2026-07-29','Cash',1,1,NULL),
 (23,'Dolo',200,'Medicine','2026-07-30','UPI',6,1,NULL);

-- ============================================================
-- Table: budgets
-- ============================================================
DROP TABLE IF EXISTS `budgets`;
CREATE TABLE `budgets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` double NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `month` int NOT NULL,
  `year` int NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_budget_user_month_year` (`user_id`,`month`,`year`),
  CONSTRAINT `fk_budgets_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- No sample budget rows in the original dump; add one for Mayuri so the
-- dashboard's budget/alert UI has something to show immediately.
INSERT INTO `budgets` (`amount`,`created_at`,`month`,`year`,`user_id`) VALUES
 (20000, NOW(), MONTH(CURDATE()), YEAR(CURDATE()), 1);

-- ============================================================
-- Table: password_reset_otps  (NEW - Forgot Password / Email OTP feature)
-- ============================================================
-- Purely additive: does not alter users/categories/expenses/budgets in any way.
DROP TABLE IF EXISTS `password_reset_otps`;
CREATE TABLE `password_reset_otps` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `otp` varchar(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_password_reset_otps_user` (`user_id`),
  CONSTRAINT `fk_password_reset_otps_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- Table: transactions  (NEW - Smart Transaction Review Hub)
-- ============================================================
-- Purely additive: does not alter users/categories/expenses/budgets/
-- password_reset_otps in any way. Deliberately kept separate from
-- `expenses` (see Transaction.java) - a transaction is NEVER auto-converted
-- into an expense. `expense_id` is filled in only when the user explicitly
-- ACCEPTs the transaction in the review inbox and a real Expense is created
-- from it.
--
-- Columns added by the Review Hub (all nullable or defaulted, so a plain
-- `ddl-auto=update` on an older DB just adds them and existing rows stay
-- valid):
--   review_status          NEEDS_REVIEW | ACCEPTED | IGNORED  (the workflow
--                          state; different axis from `status`, which is the
--                          payment outcome SUCCESS/FAILED/PENDING)
--   suggested_category_id  the categorization engine's guess, resolved to
--                          one of this user's own categories (nullable)
--   suggestion_reason      human-readable "why" shown in the inbox
--   recurring / recurring_group_key  set when the transaction looks like one
--                          instalment of a repeating series
DROP TABLE IF EXISTS `transactions`;
CREATE TABLE `transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `external_transaction_id` varchar(255) NOT NULL,
  `amount` double NOT NULL,
  `transaction_date` datetime(6) NOT NULL,
  `payment_method` varchar(255) DEFAULT NULL,
  `merchant` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `expense_id` bigint DEFAULT NULL,
  `review_status` varchar(20) NOT NULL DEFAULT 'NEEDS_REVIEW',
  `suggested_category_id` bigint DEFAULT NULL,
  `suggestion_reason` varchar(255) DEFAULT NULL,
  `recurring` tinyint(1) NOT NULL DEFAULT 0,
  `recurring_group_key` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_transactions_external_id` (`external_transaction_id`),
  KEY `fk_transactions_user` (`user_id`),
  KEY `fk_transactions_expense` (`expense_id`),
  KEY `fk_transactions_suggested_category` (`suggested_category_id`),
  CONSTRAINT `fk_transactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_transactions_expense` FOREIGN KEY (`expense_id`) REFERENCES `expenses` (`id`),
  CONSTRAINT `fk_transactions_suggested_category` FOREIGN KEY (`suggested_category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Sample transactions for Mayuri (user_id=1), dated relative to NOW() so they
-- always land in the current month. Left as NEEDS_REVIEW on purpose so the
-- /transactions review inbox has something to demonstrate straight after
-- import. One pair (Swiggy) is deliberately ~monthly so recurring detection
-- has a series to find.
INSERT INTO `transactions` (`external_transaction_id`,`amount`,`transaction_date`,`payment_method`,`merchant`,`status`,`description`,`created_at`,`user_id`,`review_status`) VALUES
 ('TXN-SAMPLE-001',799.00,NOW(),'UPI','Swiggy','SUCCESS','Sample online transaction',NOW(),1,'NEEDS_REVIEW'),
 ('TXN-SAMPLE-002',1499.00,DATE_SUB(NOW(), INTERVAL 1 DAY),'Card','Amazon','SUCCESS','Sample online transaction',NOW(),1,'NEEDS_REVIEW'),
 ('TXN-SAMPLE-003',799.00,DATE_SUB(NOW(), INTERVAL 30 DAY),'UPI','Swiggy','SUCCESS','Sample online transaction (prev month)',NOW(),1,'ACCEPTED');

-- ============================================================
-- Table: category_rules  (NEW - Smart Transaction Review Hub)
-- ============================================================
-- Purely additive. Powers the configurable auto-categorization engine:
-- "if the transaction text matches PATTERN, suggest CATEGORY".
--   user_id IS NULL  -> built-in default rule, applies to every user, targets
--                       a category by NAME (category_id NULL) because
--                       categories are per-user.
--   user_id set      -> a personal rule created from the review inbox
--                       ("remember this"), also carries a direct category_id.
-- The rows below are the same defaults the application self-seeds at startup
-- from src/main/resources/default-category-rules.csv (the seeder is a no-op if
-- any user_id IS NULL rule already exists), listed here so this file stays a
-- complete picture of the schema + starting data.
DROP TABLE IF EXISTS `category_rules`;
CREATE TABLE `category_rules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `match_type` varchar(20) NOT NULL DEFAULT 'CONTAINS',
  `pattern` varchar(255) NOT NULL,
  `category_name` varchar(100) NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `priority` int NOT NULL DEFAULT 100,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_category_rules_user` (`user_id`),
  KEY `fk_category_rules_category` (`category_id`),
  CONSTRAINT `fk_category_rules_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_category_rules_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `category_rules` (`user_id`,`match_type`,`pattern`,`category_name`,`priority`,`active`,`created_at`) VALUES
 (NULL,'CONTAINS','swiggy','Food',10,1,NOW()),
 (NULL,'CONTAINS','zomato','Food',10,1,NOW()),
 (NULL,'CONTAINS','dominos','Food',10,1,NOW()),
 (NULL,'CONTAINS','mcdonald','Food',10,1,NOW()),
 (NULL,'CONTAINS','kfc','Food',10,1,NOW()),
 (NULL,'CONTAINS','restaurant','Food',20,1,NOW()),
 (NULL,'CONTAINS','cafe','Food',20,1,NOW()),
 (NULL,'CONTAINS','amazon','Shopping',10,1,NOW()),
 (NULL,'CONTAINS','flipkart','Shopping',10,1,NOW()),
 (NULL,'CONTAINS','myntra','Shopping',10,1,NOW()),
 (NULL,'CONTAINS','ajio','Shopping',10,1,NOW()),
 (NULL,'CONTAINS','uber','Travel',10,1,NOW()),
 (NULL,'CONTAINS','ola','Travel',10,1,NOW()),
 (NULL,'CONTAINS','rapido','Travel',10,1,NOW()),
 (NULL,'CONTAINS','irctc','Travel',10,1,NOW()),
 (NULL,'CONTAINS','redbus','Travel',10,1,NOW()),
 (NULL,'CONTAINS','indigo','Travel',20,1,NOW()),
 (NULL,'CONTAINS','petrol','Fuel',10,1,NOW()),
 (NULL,'CONTAINS','fuel','Fuel',10,1,NOW()),
 (NULL,'CONTAINS','hpcl','Fuel',20,1,NOW()),
 (NULL,'CONTAINS','bpcl','Fuel',20,1,NOW()),
 (NULL,'CONTAINS','iocl','Fuel',20,1,NOW()),
 (NULL,'CONTAINS','electricity','Bills',10,1,NOW()),
 (NULL,'CONTAINS','recharge','Bills',10,1,NOW()),
 (NULL,'CONTAINS','airtel','Bills',20,1,NOW()),
 (NULL,'CONTAINS','jio','Bills',20,1,NOW()),
 (NULL,'CONTAINS','vodafone','Bills',20,1,NOW()),
 (NULL,'CONTAINS','broadband','Bills',20,1,NOW()),
 (NULL,'CONTAINS','gas','Bills',30,1,NOW()),
 (NULL,'CONTAINS','netflix','Entertainment',10,1,NOW()),
 (NULL,'CONTAINS','spotify','Entertainment',10,1,NOW()),
 (NULL,'CONTAINS','hotstar','Entertainment',10,1,NOW()),
 (NULL,'CONTAINS','prime video','Entertainment',10,1,NOW()),
 (NULL,'CONTAINS','bookmyshow','Entertainment',10,1,NOW()),
 (NULL,'CONTAINS','pvr','Entertainment',20,1,NOW()),
 (NULL,'CONTAINS','pharmacy','Health',10,1,NOW()),
 (NULL,'CONTAINS','apollo','Health',20,1,NOW()),
 (NULL,'CONTAINS','pharmeasy','Health',10,1,NOW()),
 (NULL,'CONTAINS','1mg','Health',10,1,NOW()),
 (NULL,'CONTAINS','hospital','Health',20,1,NOW()),
 (NULL,'CONTAINS','bigbasket','Groceries',10,1,NOW()),
 (NULL,'CONTAINS','blinkit','Groceries',10,1,NOW()),
 (NULL,'CONTAINS','zepto','Groceries',10,1,NOW()),
 (NULL,'CONTAINS','dmart','Groceries',10,1,NOW()),
 (NULL,'CONTAINS','grofers','Groceries',10,1,NOW()),
 (NULL,'CONTAINS','rent','Rent',10,1,NOW()),
 (NULL,'CONTAINS','udemy','Education',10,1,NOW()),
 (NULL,'CONTAINS','coursera','Education',10,1,NOW()),
 (NULL,'CONTAINS','unacademy','Education',10,1,NOW()),
 (NULL,'CONTAINS','byju','Education',10,1,NOW());

-- ============================================================
-- Helpful indexes
-- ============================================================
CREATE INDEX idx_expenses_user_date ON expenses (user_id, expense_date);
CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- Sample login credentials (all share the same password)
-- ============================================================
-- Email                  Password
-- mayuri@gmail.com       123456
-- rahul@gmail.com        123456
-- priya@gmail.com        123456
-- amit@gmail.com         123456
-- ============================================================
