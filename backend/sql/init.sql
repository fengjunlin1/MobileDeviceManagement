CREATE DATABASE IF NOT EXISTS mdm_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mdm_assistant;

CREATE TABLE IF NOT EXISTS device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    model VARCHAR(100),
    category VARCHAR(50),
    processor VARCHAR(200),
    screen VARCHAR(200),
    camera VARCHAR(200),
    battery VARCHAR(100),
    price DECIMAL(10,2),
    warranty_months INT,
    purchase_date DATETIME,
    sn_code VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_spec (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_url VARCHAR(500) UNIQUE,
    brand VARCHAR(100),
    model VARCHAR(100),
    name VARCHAR(200),
    score INT,
    weight VARCHAR(1000),
    dimensions VARCHAR(500),
    os VARCHAR(200),
    chipset VARCHAR(300),
    cpu VARCHAR(500),
    gpu VARCHAR(200),
    ram VARCHAR(100),
    storage VARCHAR(100),
    battery VARCHAR(200),
    charging VARCHAR(200),
    display_type VARCHAR(200),
    display_size VARCHAR(100),
    display_resolution VARCHAR(100),
    display_refresh_rate VARCHAR(50),
    rear_camera VARCHAR(500),
    front_camera VARCHAR(500),
    video_recording VARCHAR(300),
    network_5g BOOLEAN,
    network_4g BOOLEAN,
    bluetooth VARCHAR(100),
    wifi VARCHAR(100),
    nfc BOOLEAN,
    release_date VARCHAR(200),
    price VARCHAR(200),
    price_usd DECIMAL(10,2),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_brand (brand),
    INDEX idx_device_url (device_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) UNIQUE NOT NULL,
    budget_min INT,
    budget_max INT,
    primary_use VARCHAR(200),
    preferred_brands VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS warranty_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(100) NOT NULL,
    sn_code VARCHAR(100),
    device_name VARCHAR(200),
    warranty_status VARCHAR(50),
    warranty_start DATETIME,
    warranty_end DATETIME,
    warranty_range VARCHAR(500),
    query_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id),
    INDEX idx_sn_code (sn_code),
    INDEX idx_warranty_end (warranty_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    memory_id VARCHAR(100) NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    messages_json TEXT NOT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_memory_id (memory_id),
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    messages_json TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_user_session (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT,
    source_url VARCHAR(1000),
    device_name VARCHAR(200),
    category VARCHAR(100),
    embedding TEXT,
    crawled_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_name (device_name),
    INDEX idx_category (category),
    INDEX idx_crawled_at (crawled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
