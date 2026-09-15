-- TaskX MySQL 初始化脚本
-- 文档：docs/08-schema.md、docs/03-domain-model.md、docs/00-decisions.md
-- 要求：MySQL 8.0+（JSON 类型、utf8mb4）
-- 用法：mysql -u root -p < docs/sql/schema.sql

CREATE DATABASE IF NOT EXISTS taskx
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE taskx;

-- 执行者。executor_id 必须稳定；心跳仅观测，不用于抢 slot。
CREATE TABLE tx_executor (
    executor_id         VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '稳定执行者 ID',
    last_heartbeat_at   TIMESTAMP(3) NULL COMMENT '仅观测',
    created_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (executor_id)
) COMMENT = '执行者注册';

-- slot 计划归属。slot_no 主键；被他人占用则启动失败，已是自己则可重启。
CREATE TABLE tx_slot_ownership (
    slot_no     INT NOT NULL COMMENT '0 .. N-1，默认 N=32',
    executor_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    updated_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (slot_no),
    CONSTRAINT fk_slot_executor FOREIGN KEY (executor_id) REFERENCES tx_executor (executor_id)
) COMMENT = 'slot 计划归属';

-- 调度配置。槽位由 hash(task_id)%N 计算，不落列。
-- trigger_spec JSON 形状见 docs/08-schema.md。
CREATE TABLE tx_task (
    task_id         VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    handler         VARCHAR(256) NOT NULL,
    payload         JSON NULL COMMENT 'Handler 参数',
    enabled         TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=停用，写入与拉取时 ZREM',
    trigger_type    VARCHAR(32) NOT NULL COMMENT 'CRON / FIXED_RATE / FIXED_DELAY / DELAY / ONCE',
    trigger_spec    JSON NOT NULL,
    workflow_json   JSON NULL COMMENT '可选编排整图；第一版仅 Start→任务→End',
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (task_id)
) COMMENT = '调度配置';

-- 执行记录。唯一键 (task_id, scheduled_fire_time)；PENDING→RUNNING CAS 才执行。
CREATE TABLE tx_execution (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    task_id               VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    scheduled_fire_time   BIGINT NOT NULL COMMENT 'Unix 秒，领取时的 ZSET score',
    executor_id           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    status                VARCHAR(32) NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED / CANCELLED',
    error_message         VARCHAR(2048) NULL,
    created_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at            TIMESTAMP(3) NULL,
    finished_at           TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_fire (task_id, scheduled_fire_time),
    KEY idx_executor_status (executor_id, status)
) COMMENT = '执行记录';
