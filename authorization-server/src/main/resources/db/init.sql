create table shopl_authorization.io_idp_client
(
    id                            varchar(255)                             not null primary key comment 'PK, 내부 식별자',
    idp_client_id                 varchar(255)                             not null comment 'OAuth2 Client ID',

    shopl_client_id               varchar(20)                              null comment '고객사 ID (멀티테넌시 지원용)',
    platform                      ENUM ('DASHBOARD', 'APP')                null comment '클라이언트 플랫폼 타입',
    environment                   ENUM ('SHOPL', 'CPS','SSS', 'QA', 'DEV') null comment '클라이언트 운영 환경',

    client_id_issued_at           timestamp default CURRENT_TIMESTAMP      not null comment 'Client ID 발급 시각',
    client_secret                 varchar(255)                             null comment 'Client Secret (암호화 저장 권장)',
    client_secret_expires_at      timestamp                                null comment 'Client Secret 만료 시각',
    client_name                   varchar(255)                             not null comment '클라이언트 이름 (식별용)',
    client_authentication_methods varchar(1000)                            not null comment '인증 방식 (client_secret_basic, private_key_jwt 등)',
    authorization_grant_types     varchar(1000)                            not null comment '허용된 Grant Types',
    redirect_uris                 varchar(1000)                            null comment '등록된 Redirect URI 목록',
    post_logout_redirect_uris     varchar(1000)                            null comment '로그아웃 후 Redirect URI',
    scopes                        varchar(1000)                            not null comment '허용된 Scope 목록',
    client_settings               varchar(2000)                            not null comment '클라이언트 설정 (JSON 직렬화)',
    token_settings                varchar(2000)                            not null comment '토큰 발급 관련 설정 (만료, 서명 등)'
);

create table shopl_authorization.io_idp_authorization_consent
(
    id             bigint auto_increment primary key comment 'PK',
    idp_client_id  varchar(255)  not null comment 'OAuth2 Client ID',
    principal_name varchar(255)  not null comment '사용자 식별자 (username, email 등)',
    authorities    varchar(1000) not null comment '동의한 권한(scope/role 목록)'
);

create table shopl_authorization.io_idp_account
(
    id              VARCHAR(20) PRIMARY KEY comment 'PK, 내부 계정 식별자',
    shopl_client_id VARCHAR(20)  NOT NULL comment '고객사 ID',
    shopl_user_id   VARCHAR(20)  NOT NULL comment '내부 사용자 ID',
    shopl_login_id  VARCHAR(20)  NOT NULL comment '로그인 ID',

    email           varchar(255) null comment '사용자 이메일',
    phone           varchar(30)  null comment '사용자 휴대폰 번호',
    name            varchar(100) null comment '사용자 이름',
    status          varchar(20)           default 'ACTIVE' null comment '계정 상태 (ACTIVE, INACTIVE, BLOCKED 등)',
    is_cert_email   TINYINT(1)   NOT NULL DEFAULT 0 comment '이메일 인증 여부',

    is_temp_pwd     TINYINT(1)   NOT NULL DEFAULT 0 comment '임시 비밀번호 여부',
    pwd             VARCHAR(255) NULL comment '비밀번호 해시',
    before_pwd      VARCHAR(255) NULL     DEFAULT NULL comment '이전 비밀번호 해시',
    pwd_update_dt   DATETIME     NULL     DEFAULT NULL comment '비밀번호 업데이트 시각',

    reg_dt          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP comment '계정 생성 시각',
    mod_dt          DATETIME     NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP comment '계정 수정 시각',
    del_dt          DATETIME     NULL     DEFAULT NULL comment '삭제(탈퇴) 시각',

    INDEX idx_shopl_client_user (shopl_client_id, shopl_user_id),
    INDEX idx_status (status)
);

create table shopl_authorization.io_idp_account_oauth_link
(
    id                VARCHAR(20) PRIMARY KEY comment 'PK',
    account_id        VARCHAR(20)                                                                NOT NULL comment '내부 계정 ID',
    shopl_client_id   VARCHAR(20)                                                                NOT NULL comment '고객사 ID',

    provider_type     ENUM ('GOOGLE','NAVER','KAKAO','APPLE','MICROSOFT','GITHUB','SAML','OIDC') NOT NULL comment 'OAuth Provider 유형',
    provider_user_id  VARCHAR(191)                                                               NOT NULL comment '외부 Provider에서의 사용자 식별자 (OIDC sub, SAML NameID 등)',
    email_at_provider VARCHAR(320)                                                               NULL     DEFAULT NULL comment 'Provider 기준 이메일',
    name_at_provider  VARCHAR(100)                                                               NULL     DEFAULT NULL comment 'Provider 기준 사용자명',
    raw_claims        JSON                                                                       NULL     DEFAULT NULL comment 'Provider 원본 Claims',

    reg_dt            DATETIME                                                                   NOT NULL DEFAULT CURRENT_TIMESTAMP comment '연동 등록 시각',
    mod_dt            DATETIME                                                                   NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP comment '수정 시각',
    del_dt            DATETIME                                                                   NULL     DEFAULT NULL comment '삭제 시각',

    CONSTRAINT fk_oauth_account_id FOREIGN KEY (account_id) REFERENCES io_idp_account (id) ON DELETE CASCADE,
    UNIQUE KEY uq_provider_subject (shopl_client_id, provider_type, provider_user_id),
    INDEX idx_oauth_account_provider (account_id, provider_type),
    INDEX idx_provider_email (provider_type, email_at_provider)
);

create table shopl_authorization.io_idp_shopl_client_sso_setting
(
    id                          varchar(20)                                                                                        not null primary key,
    shopl_client_id             varchar(20) unique                                                                                 not null,
    sso_type                    enum ('OIDC', 'SAML')                                                                              not null,

    oidc_client_id              varchar(50)                                                                                        null comment 'OIDC SSO Client ID',
    oidc_client_secret          varchar(500)                                                                                       null comment 'OIDC SSO Client Secret',
    oidc_issuer                 varchar(500)                                                                                       null comment 'OIDC Issuer URL',
    oidc_scopes                 varchar(500)                        default 'openid email profile'                                 null comment 'Required Scopes',
    oidc_response_type          varchar(100)                        default 'code'                                                 null comment 'Response Type',
    oidc_response_mode          varchar(50)                                                                                        null comment 'Response Mode',
    oidc_claims_mapping         json                                                                                               null comment 'Claims to User Attribute Mapping',

    saml_entity_id              varchar(500)                                                                                       null comment 'SAML Entity ID',
    saml_sso_url                varchar(500)                                                                                       null comment 'SAML SSO URL',
    saml_slo_url                varchar(500)                                                                                       null comment 'SAML SLO URL',
    saml_x509_cert              text                                                                                               null comment 'SAML X.509 Certificate',
    saml_name_id_format         varchar(200)                        default 'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent' null comment 'NameID Format',
    saml_binding_sso            enum ('HTTP-POST', 'HTTP-Redirect') default 'HTTP-POST'                                            null comment 'SSO Binding',
    saml_binding_slo            enum ('HTTP-POST', 'HTTP-Redirect') default 'HTTP-POST'                                            null comment 'SLO Binding',
    saml_want_assertions_signed tinyint(1)                          default 1                                                      null comment 'Want Assertions Signed',
    saml_want_response_signed   tinyint(1)                          default 1                                                      null comment 'Want Response Signed',
    saml_signature_algorithm    varchar(100)                        default 'http://www.w3.org/2001/04/xmldsig-more#rsa-sha256'    null comment 'Signature Algorithm',
    saml_digest_algorithm       varchar(100)                        default 'http://www.w3.org/2001/04/xmlenc#sha256'              null comment 'Digest Algorithm',
    saml_attribute_mapping      json                                                                                               null comment 'SAML Attribute Mapping',
    redirect_uris               json                                                                                               null comment 'Allowed Redirect URIs',
    auto_provision              tinyint(1)                          default 1                                                      not null comment '자동 계정 생성 여부',
    default_role                varchar(50)                                                                                        null comment '기본 할당 역할',
    reg_dt                      datetime                            default CURRENT_TIMESTAMP                                      not null,
    mod_dt                      datetime                                                                                           null on update CURRENT_TIMESTAMP,
    del_dt                      datetime                                                                                           null
);

CREATE TABLE io_idp_login_history
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    shopl_client_id     VARCHAR(20) NOT NULL,
    shopl_user_id       VARCHAR(20) NOT NULL,
    platform            ENUM('DASHBOARD', 'APP') NOT NULL,

    login_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    login_type          ENUM('BASIC', 'SOCIAL', 'SSO') NOT NULL,
    provider            VARCHAR(64) NULL,

    result              ENUM('SUCCESS', 'FAIL') NOT NULL,
    failure_reason      VARCHAR(100) NULL,

    ip_address          VARCHAR(45) NULL,
    user_agent          TEXT NULL,
    location            VARCHAR(200) NULL,

    session_id          VARCHAR(128) NOT NULL,

    INDEX idx_user_client_time (shopl_user_id, shopl_client_id, login_time),
    INDEX idx_result_time (result, login_time),
    INDEX idx_client_time (shopl_client_id, login_time)
)
;