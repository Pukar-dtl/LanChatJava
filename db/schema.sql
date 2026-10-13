CREATE DATABASE IF NOT EXISTS lanchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE lanchat;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(64) NOT NULL,
    recipient VARCHAR(64) NOT NULL DEFAULT 'ALL',
    ciphertext LONGTEXT NOT NULL,
    iv VARCHAR(32) NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recipient (recipient),
    INDEX idx_sent_at (sent_at)
) ENGINE=InnoDB;