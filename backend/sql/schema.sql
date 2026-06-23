-- 1. 为 chat_memory 表添加 user_id 列
ALTER TABLE chat_memory ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- 2. 创建 chat_history 表
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

-- 3. 创建 chat_message 表（存储每条单独的对话消息）
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT '消息角色：user 或 assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    intent VARCHAR(50) COMMENT '消息意图类型',
    mentioned_devices VARCHAR(500) COMMENT '提到的设备名（逗号分隔）',
    is_recommendation BOOLEAN DEFAULT FALSE COMMENT '是否是推荐消息',
    recommendation_devices VARCHAR(500) COMMENT '推荐的设备名（逗号分隔）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_session (user_id, session_id),
    INDEX idx_session_time (session_id, created_at),
    INDEX idx_role (role),
    INDEX idx_recommendation (is_recommendation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表-持久化存储对话历史';