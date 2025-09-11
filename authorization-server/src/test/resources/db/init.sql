-- H2 데이터베이스용 테스트 스키마
DROP TABLE IF EXISTS io_idp_login_history;

CREATE TABLE io_idp_login_history
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    shopl_client_id     VARCHAR(20) NOT NULL,
    shopl_user_id       VARCHAR(20) NOT NULL,
    platform            VARCHAR(20) NOT NULL CHECK (platform IN ('DASHBOARD', 'APP')),

    login_time          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    login_type          VARCHAR(20) NOT NULL CHECK (login_type IN ('BASIC', 'SOCIAL', 'SSO')),
    provider            VARCHAR(64) NULL,

    result              VARCHAR(20) NOT NULL CHECK (result IN ('SUCCESS', 'FAIL')),
    failure_reason      VARCHAR(100) NULL,

    ip_address          VARCHAR(45) NULL,
    user_agent          CLOB NULL,
    location            VARCHAR(200) NULL,

    session_id          VARCHAR(128) NOT NULL
);

-- 인덱스 생성
CREATE INDEX idx_user_client_time ON io_idp_login_history (shopl_user_id, shopl_client_id, login_time);
CREATE INDEX idx_result_time ON io_idp_login_history (result, login_time);
CREATE INDEX idx_client_time ON io_idp_login_history (shopl_client_id, login_time);