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
    target_type     VARCHAR(32) NOT NULL COMMENT 'HANDLER / WORKFLOW',
    target_ref      VARCHAR(256) NOT NULL COMMENT 'handler name 或 workflow_id',
    target_version  INT UNSIGNED NULL COMMENT 'WORKFLOW 固定版本；NULL 表示启动时取当前发布版本',
    payload         JSON NULL COMMENT '目标入参',
    enabled         TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=停用，写入与拉取时 ZREM',
    trigger_type    VARCHAR(32) NOT NULL COMMENT 'CRON / FIXED_RATE / FIXED_DELAY / DELAY / ONCE',
    trigger_spec    JSON NOT NULL,
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

-- 工作流逻辑身份。发布版本不可变，current_published_version 指向当前默认版本。
CREATE TABLE tx_workflow (
    workflow_id                VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    name                       VARCHAR(128) NOT NULL,
    current_published_version  INT UNSIGNED NULL,
    created_at                 TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                 TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (workflow_id)
) COMMENT = '工作流逻辑身份';

CREATE TABLE tx_workflow_draft (
    workflow_id      VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    revision         BIGINT UNSIGNED NOT NULL,
    definition_json  JSON NOT NULL,
    ui_layout_json   JSON NULL,
    updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (workflow_id),
    CONSTRAINT fk_workflow_draft_workflow FOREIGN KEY (workflow_id) REFERENCES tx_workflow (workflow_id)
) COMMENT = '工作流可变草稿';

CREATE TABLE tx_workflow_version (
    workflow_id      VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    version          INT UNSIGNED NOT NULL,
    definition_json  JSON NOT NULL,
    ui_layout_json   JSON NULL,
    definition_hash  CHAR(64) NOT NULL,
    status           VARCHAR(32) NOT NULL COMMENT 'PUBLISHED / ARCHIVED',
    published_at     TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (workflow_id, version),
    KEY idx_workflow_version_status (workflow_id, status),
    CONSTRAINT fk_workflow_version_workflow FOREIGN KEY (workflow_id) REFERENCES tx_workflow (workflow_id)
) COMMENT = '工作流不可变发布版本';

CREATE TABLE tx_workflow_instance (
    instance_id          VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    workflow_id          VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    workflow_version     INT UNSIGNED NOT NULL,
    trigger_execution_id BIGINT NOT NULL,
    status               VARCHAR(32) NOT NULL COMMENT 'RUNNING / SUCCESS / FAILED / PAUSED / CANCELLED',
    input_data           JSON NULL,
    output_data          JSON NULL,
    started_at           TIMESTAMP(3) NOT NULL,
    finished_at          TIMESTAMP(3) NULL,
    PRIMARY KEY (instance_id),
    UNIQUE KEY uk_workflow_trigger_execution (trigger_execution_id),
    KEY idx_workflow_instance_status (workflow_id, status),
    CONSTRAINT fk_workflow_instance_version
        FOREIGN KEY (workflow_id, workflow_version) REFERENCES tx_workflow_version (workflow_id, version),
    CONSTRAINT fk_workflow_instance_execution
        FOREIGN KEY (trigger_execution_id) REFERENCES tx_execution (id)
) COMMENT = '工作流实例';

CREATE TABLE tx_workflow_node_activation (
    activation_id   VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    instance_id     VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    node_id         VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    activation_no   INT UNSIGNED NOT NULL,
    status          VARCHAR(32) NOT NULL COMMENT 'READY / RUNNING / RETRY_WAIT / WAITING / SUCCESS / FAILED / SKIPPED / CANCELLED',
    worker_group    VARCHAR(64) NOT NULL DEFAULT 'default',
    slot_no         INT NOT NULL,
    max_attempts    INT UNSIGNED NOT NULL DEFAULT 1,
    retry_interval_sec INT UNSIGNED NOT NULL DEFAULT 0,
    timeout_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    current_attempt INT UNSIGNED NOT NULL DEFAULT 0,
    route_handle    VARCHAR(64) NULL,
    input_data      JSON NULL,
    output_data     JSON NULL,
    available_at    TIMESTAMP(3) NOT NULL,
    lease_until     TIMESTAMP(3) NULL,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at      TIMESTAMP(3) NULL,
    finished_at     TIMESTAMP(3) NULL,
    PRIMARY KEY (activation_id),
    UNIQUE KEY uk_workflow_node_activation (instance_id, node_id, activation_no),
    KEY idx_workflow_ready (worker_group, slot_no, status, available_at),
    CONSTRAINT fk_workflow_activation_instance
        FOREIGN KEY (instance_id) REFERENCES tx_workflow_instance (instance_id)
) COMMENT = '工作流节点逻辑激活';

CREATE TABLE tx_workflow_node_attempt (
    attempt_id      BIGINT NOT NULL AUTO_INCREMENT,
    activation_id  VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    attempt_no      INT UNSIGNED NOT NULL,
    executor_id     VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    status          VARCHAR(32) NOT NULL COMMENT 'RUNNING / SUCCESS / FAILED / TIMED_OUT',
    error_code      VARCHAR(128) NULL,
    error_message   TEXT NULL,
    started_at      TIMESTAMP(3) NOT NULL,
    finished_at     TIMESTAMP(3) NULL,
    PRIMARY KEY (attempt_id),
    UNIQUE KEY uk_workflow_attempt (activation_id, attempt_no),
    CONSTRAINT fk_workflow_attempt_activation
        FOREIGN KEY (activation_id) REFERENCES tx_workflow_node_activation (activation_id)
) COMMENT = '工作流节点执行尝试';

CREATE TABLE tx_workflow_token (
    token_id              BIGINT NOT NULL AUTO_INCREMENT,
    instance_id           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_activation_id  VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    edge_id               VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    target_node_id        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    created_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (token_id),
    UNIQUE KEY uk_workflow_transition (source_activation_id, edge_id),
    KEY idx_workflow_token_target (instance_id, target_node_id),
    CONSTRAINT fk_workflow_token_instance
        FOREIGN KEY (instance_id) REFERENCES tx_workflow_instance (instance_id),
    CONSTRAINT fk_workflow_token_activation
        FOREIGN KEY (source_activation_id) REFERENCES tx_workflow_node_activation (activation_id)
) COMMENT = '工作流推进 Token';

CREATE TABLE tx_workflow_outbox (
    outbox_id       BIGINT NOT NULL AUTO_INCREMENT,
    activation_id   VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    worker_group    VARCHAR(64) NOT NULL,
    slot_no         INT NOT NULL,
    available_at    TIMESTAMP(3) NOT NULL,
    published_at    TIMESTAMP(3) NULL,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (outbox_id),
    KEY idx_workflow_outbox_unpublished (published_at, outbox_id),
    CONSTRAINT fk_workflow_outbox_activation
        FOREIGN KEY (activation_id) REFERENCES tx_workflow_node_activation (activation_id)
) COMMENT = '工作流 Redis 就绪索引 Outbox';

CREATE TABLE tx_workflow_callback (
    callback_id     BIGINT NOT NULL AUTO_INCREMENT,
    activation_id   VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    token_hash      CHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL COMMENT 'WAITING / COMPLETED / EXPIRED',
    expires_at      TIMESTAMP(3) NOT NULL,
    completed_at    TIMESTAMP(3) NULL,
    PRIMARY KEY (callback_id),
    UNIQUE KEY uk_workflow_callback_token (token_hash),
    KEY idx_workflow_callback_expiry (status, expires_at),
    CONSTRAINT fk_workflow_callback_activation
        FOREIGN KEY (activation_id) REFERENCES tx_workflow_node_activation (activation_id)
) COMMENT = '工作流异步回调';
