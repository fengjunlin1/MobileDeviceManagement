package com.mdm.assistant.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DatabaseInitConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitConfig.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        log.info("开始初始化数据库表结构...");
        try {
            addUserIdColumnToChatMemory();
            createChatHistoryTable();
            addUserProfileColumns();
            addUserIdColumnToWarrantyRecord();
            createMyDevicesTable();
            migrateMyDevicesRamRom();
            log.info("数据库表结构初始化完成");
        } catch (Exception e) {
            log.error("数据库表结构初始化失败", e);
        }
    }

    private void addUserIdColumnToChatMemory() {
        try {
            // 先查询列是否已存在
            boolean columnExists = false;
            try {
                List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SHOW COLUMNS FROM chat_memory LIKE 'user_id'");
                columnExists = columns.size() > 0;
            } catch (Exception e) {
                log.debug("查询列是否存在时出错，假设不存在", e);
            }

            if (!columnExists) {
                jdbcTemplate.execute("ALTER TABLE chat_memory ADD COLUMN user_id BIGINT");
                log.info("已成功为 chat_memory 表添加 user_id 列");
            } else {
                log.info("chat_memory 表的 user_id 列已存在，跳过添加");
            }
        } catch (Exception e) {
            log.warn("添加 user_id 列到 chat_memory 可能失败（可能已存在）", e);
        }
    }

    private void createChatHistoryTable() {
        try {
            String createTableSql = """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            jdbcTemplate.execute(createTableSql);
            log.info("已成功创建 chat_history 表");
        } catch (Exception e) {
            log.warn("创建 chat_history 表可能失败（可能已存在）", e);
        }
    }

    private void addUserProfileColumns() {
        String[] columns = {"avatar_data MEDIUMTEXT", "gender VARCHAR(10)", "birthday DATE"};
        for (String colDef : columns) {
            String colName = colDef.split(" ")[0];
            try {
                boolean columnExists = false;
                try {
                    List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                        "SHOW COLUMNS FROM users LIKE '" + colName + "'");
                    columnExists = cols.size() > 0;
                } catch (Exception e) {
                    log.debug("查询列 {} 是否存在时出错，假设不存在", colName, e);
                }
                if (!columnExists) {
                    jdbcTemplate.execute("ALTER TABLE users ADD COLUMN " + colDef);
                    log.info("已成功为 users 表添加 {} 列", colName);
                } else {
                    log.info("users 表的 {} 列已存在，跳过添加", colName);
                }
            } catch (Exception e) {
                log.warn("添加 {} 列到 users 表可能失败", colName, e);
            }
        }
    }

    private void addUserIdColumnToWarrantyRecord() {
        try {
            boolean columnExists = false;
            try {
                List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SHOW COLUMNS FROM warranty_record LIKE 'user_id'");
                columnExists = columns.size() > 0;
            } catch (Exception e) {
                log.debug("查询列是否存在时出错，假设不存在", e);
            }

            if (!columnExists) {
                jdbcTemplate.execute("ALTER TABLE warranty_record ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0");
                log.info("已成功为 warranty_record 表添加 user_id 列");
            } else {
                log.info("warranty_record 表的 user_id 列已存在，跳过添加");
            }
        } catch (Exception e) {
            log.warn("添加 user_id 列到 warranty_record 可能失败（可能已存在）", e);
        }
    }

    private void createMyDevicesTable() {
        try {
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS my_devices (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    device_name VARCHAR(200) NOT NULL,
                    device_category VARCHAR(50),
                    brand VARCHAR(100),
                    ram VARCHAR(20),
                    rom VARCHAR(20),
                    sn_code VARCHAR(200) NOT NULL,
                    activation_date DATE NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_mydev_user_id (user_id),
                    INDEX idx_mydev_sn_code (sn_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            jdbcTemplate.execute(createTableSql);
            log.info("已成功创建 my_devices 表");
        } catch (Exception e) {
            log.warn("创建 my_devices 表可能失败（可能已存在）", e);
        }
    }

    private void migrateMyDevicesRamRom() {
        String[][] columns = {
            {"ram", "VARCHAR(20)"},
            {"rom", "VARCHAR(20)"}
        };
        for (String[] colDef : columns) {
            String colName = colDef[0];
            try {
                boolean columnExists = false;
                try {
                    List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                        "SHOW COLUMNS FROM my_devices LIKE '" + colName + "'");
                    columnExists = cols.size() > 0;
                } catch (Exception e) {
                    log.debug("查询列 {} 是否存在时出错，假设不存在", colName, e);
                }
                if (!columnExists) {
                    jdbcTemplate.execute("ALTER TABLE my_devices ADD COLUMN " + colName + " " + colDef[1]);
                    log.info("已成功为 my_devices 表添加 {} 列", colName);
                } else {
                    log.info("my_devices 表的 {} 列已存在，跳过添加", colName);
                }
            } catch (Exception e) {
                log.warn("添加 {} 列到 my_devices 表可能失败", colName, e);
            }
        }
    }
}
