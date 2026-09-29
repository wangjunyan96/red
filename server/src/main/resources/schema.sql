CREATE TABLE IF NOT EXISTS t_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone        VARCHAR(20)  NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    points       BIGINT       NOT NULL DEFAULT 0,
    created_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    valid        TINYINT      NOT NULL DEFAULT 1
);

-- 数据号：云手机登号器用的账号 token
-- token 本身可到 1024 字符；utf8mb4 下整列 UNIQUE 会超过 InnoDB 3072 字节上限，所以只用前缀唯一索引。
CREATE TABLE IF NOT EXISTS t_data_account (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    token         VARCHAR(1024) NOT NULL,
    token_type    VARCHAR(64)   NOT NULL DEFAULT 'hero_killer',
    status        VARCHAR(16)   NOT NULL DEFAULT 'UNLINKED',
    reunion_code  VARCHAR(255),
    created_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    valid         TINYINT       NOT NULL DEFAULT 1,
    UNIQUE KEY uk_data_account_token (token(255)),
    KEY idx_data_account_type_status (token_type, status, valid)
);

-- 重逢码：脚本绑定好友时填写
CREATE TABLE IF NOT EXISTS t_reunion_code (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    reunion_code  VARCHAR(255) NOT NULL UNIQUE,
    bind_count    INT          NOT NULL DEFAULT 0,
    created_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    valid         TINYINT      NOT NULL DEFAULT 1
);

-- 任务队列：game_code + task_type 区分游戏与玩法，payload 存该类型字段（JSON）
CREATE TABLE IF NOT EXISTS t_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_code        VARCHAR(64)   NOT NULL DEFAULT 'hero_killer',
    task_type        VARCHAR(64)   NOT NULL DEFAULT 'reunion',
    account          VARCHAR(1024) NOT NULL DEFAULT '',
    reunion_code     VARCHAR(255),
    payload          TEXT,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    assigned_device  VARCHAR(255),
    run_id           VARCHAR(64),
    lease_until      BIGINT        NOT NULL DEFAULT 0,
    attempts         INT           NOT NULL DEFAULT 0,
    last_error       TEXT,
    created_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    valid            TINYINT       NOT NULL DEFAULT 1,
    KEY idx_task_claim (valid, game_code, task_type, status, lease_until)
);

-- 已有库补列 / 补索引。重复执行会报错，OptionalSchemaInitializer 已 continueOnError。
ALTER TABLE t_data_account ADD COLUMN token_type VARCHAR(64) NOT NULL DEFAULT 'hero_killer';
ALTER TABLE t_data_account ADD INDEX idx_data_account_type_status (token_type, status, valid);
ALTER TABLE t_task ADD COLUMN game_code VARCHAR(64) NOT NULL DEFAULT 'hero_killer';
ALTER TABLE t_task ADD COLUMN task_type VARCHAR(64) NOT NULL DEFAULT 'reunion';
ALTER TABLE t_task ADD COLUMN payload TEXT;
ALTER TABLE t_task MODIFY COLUMN reunion_code VARCHAR(255) NULL;
ALTER TABLE t_task MODIFY COLUMN account VARCHAR(1024) NOT NULL DEFAULT '';
ALTER TABLE t_task ADD INDEX idx_task_claim (valid, game_code, task_type, status, lease_until);

-- C 端可售项目
CREATE TABLE IF NOT EXISTS t_c_project (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(64)  NOT NULL UNIQUE,
    name          VARCHAR(128) NOT NULL,
    category      VARCHAR(64)  NOT NULL DEFAULT '未分类',
    price_cents   INT          NOT NULL DEFAULT 0,
    game_code     VARCHAR(64)  NOT NULL,
    task_type     VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    tutorial      TEXT,
    sort_no       INT          NOT NULL DEFAULT 0,
    created_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid         TINYINT      NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS t_c_keyword (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    word          VARCHAR(64)  NOT NULL,
    project_id    BIGINT       NOT NULL,
    valid         TINYINT      NOT NULL DEFAULT 1,
    UNIQUE KEY uk_c_keyword_word (word)
);

CREATE TABLE IF NOT EXISTS t_c_order (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no      VARCHAR(64)  NOT NULL UNIQUE,
    user_id       BIGINT       NOT NULL,
    project_id    BIGINT       NOT NULL,
    task_id       BIGINT,
    invite_code   VARCHAR(255),
    amount_cents  INT          NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING',
    created_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid         TINYINT      NOT NULL DEFAULT 1,
    KEY idx_c_order_user (user_id, id)
);

CREATE TABLE IF NOT EXISTS t_c_message (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    msg_type      VARCHAR(16)  NOT NULL DEFAULT 'text',
    content       TEXT,
    extra         TEXT,
    created_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_c_msg_user (user_id, id)
);

CREATE TABLE IF NOT EXISTS t_c_card (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_key      VARCHAR(64)  NOT NULL UNIQUE,
    amount_cents  INT          NOT NULL,
    used          TINYINT      NOT NULL DEFAULT 0,
    used_user_id  BIGINT,
    used_time     TIMESTAMP    NULL,
    created_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid         TINYINT      NOT NULL DEFAULT 1
);

ALTER TABLE t_c_order MODIFY COLUMN order_no VARCHAR(64) NOT NULL;
