-- 설계 과정 샘플
CREATE TABLE shopl.io_client_info
(
    id     VARCHAR(20) PRIMARY KEY,
    NAME   VARCHAR(200) NOT NULL,

    reg_dt DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dt DATETIME     NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE shopl.io_client_oauth_option
(
    id                    VARCHAR(20) PRIMARY KEY,
    client_id             VARCHAR(20) NOT NULL,
    is_enable_basic_login TINYINT(1)  NOT NULL DEFAULT 1,
    is_enable_google      TINYINT(1)  NOT NULL DEFAULT 0,
    is_enable_naver       TINYINT(1)  NOT NULL DEFAULT 0,
    is_enable_kakao       TINYINT(1)  NOT NULL DEFAULT 0,
    is_enable_apple       TINYINT(1)  NOT NULL DEFAULT 0,
    is_enable_sso         TINYINT(1)  NOT NULL DEFAULT 0,

    reg_dt                DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dt                DATETIME    NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_client_id (client_id)
);