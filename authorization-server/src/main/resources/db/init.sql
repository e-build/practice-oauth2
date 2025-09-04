
# SAS core 스키마 :: client
create table shopl_authentication.io_idp_client
(
    id                            varchar(255)                        not null
        primary key,
    client_id                     varchar(255)                        not null,
    client_id_issued_at           timestamp default CURRENT_TIMESTAMP not null,
    client_secret                 varchar(255)                        null,
    client_secret_expires_at      timestamp                           null,
    client_name                   varchar(255)                        not null,
    client_authentication_methods varchar(1000)                       not null,
    authorization_grant_types     varchar(1000)                       not null,
    redirect_uris                 varchar(1000)                       null,
    post_logout_redirect_uris     varchar(1000)                       null,
    scopes                        varchar(1000)                       not null,
    client_settings               varchar(2000)                       not null,
    token_settings                varchar(2000)                       not null
);

# SAS core 스키마 :: authorization
create table shopl_authentication.io_idp_authorization
(
    id                            varchar(255) not null
        primary key,
    registered_client_id          varchar(255) not null,
    principal_name                varchar(255) not null,
    authorization_grant_type      varchar(255) not null,
    authorized_scopes             text         null,
    attributes                    text         null,
    state                         varchar(500) null,
    authorization_code_value      text         null,
    authorization_code_issued_at  timestamp    null,
    authorization_code_expires_at timestamp    null,
    authorization_code_metadata   text         null,
    access_token_value            text         null,
    access_token_issued_at        timestamp    null,
    access_token_expires_at       timestamp    null,
    access_token_metadata         text         null,
    access_token_type             varchar(255) null,
    access_token_scopes           text         null,
    refresh_token_value           text         null,
    refresh_token_issued_at       timestamp    null,
    refresh_token_expires_at      timestamp    null,
    refresh_token_metadata        text         null,
    oidc_id_token_value           text         null,
    oidc_id_token_issued_at       timestamp    null,
    oidc_id_token_expires_at      timestamp    null,
    oidc_id_token_metadata        text         null,
    oidc_id_token_claims          text         null,
    user_code_value               text         null,
    user_code_issued_at           timestamp    null,
    user_code_expires_at          timestamp    null,
    user_code_metadata            text         null,
    device_code_value             text         null,
    device_code_issued_at         timestamp    null,
    device_code_expires_at        timestamp    null,
    device_code_metadata          text         null
);

# SAS core 스키마 :: authorizationconsent
create table shopl_authentication.io_idp_authorizationconsent
(
    registered_client_id varchar(255)  not null,
    principal_name       varchar(255)  not null,
    authorities          varchar(1000) not null,
    primary key (registered_client_id, principal_name)
);

-- 계정(주체)
create table shopl_authentication.io_idp_account
(
    id              VARCHAR(20) PRIMARY KEY,
    shopl_client_id VARCHAR(20)  NOT NULL,
    shopl_user_id   VARCHAR(20)  NOT NULL,

    # ID 관련
    email           varchar(255) null comment '사용자 이메일',
    phone           varchar(30)  null comment '사용자 휴대폰 번호',
    name            varchar(100) null comment '사용자 이름',
    status          varchar(20)           default 'ACTIVE' null comment '계정 상태 (ACTIVE, INACTIVE, BLOCKED 등)',
    is_email_verified        TINYINT(1)   NOT NULL DEFAULT 0,

    # 패스워드 관련
    is_temp_pwd     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '임시 비밀번호 여부',
    pwd             VARCHAR(255) NOT NULL COMMENT '비밀번호 해시',
    before_pwd      VARCHAR(255) NULL     DEFAULT NULL COMMENT '이전 비밀번호 해시',
    pwd_update_dt   DATETIME     NULL     DEFAULT NULL COMMENT '비밀번호 업데이트 시점',
    pwd_expires_dt  DATETIME     NULL     DEFAULT NULL COMMENT '비밀번호 만료 시점',
    failed_attempts INT          NOT NULL DEFAULT 0 COMMENT '로그인 실패 횟수',
    locked_until_dt DATETIME     NULL     DEFAULT NULL COMMENT '계정 잠금 해제 시점',

    reg_dt          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dt          DATETIME     NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    del_dt          DATETIME     NULL     DEFAULT NULL,

    INDEX idx_shopl_client_user (shopl_client_id, shopl_user_id),
    INDEX idx_status (status)
);

-- OAuth 연동 정보
create table shopl_authentication.io_idp_account_oauth_link
(
    id                VARCHAR(20) PRIMARY KEY,
    account_id        VARCHAR(20)                                                                NOT NULL,
    shopl_client_id   VARCHAR(20)                                                                NOT NULL,
    provider_type     ENUM ('GOOGLE','NAVER','KAKAO','APPLE','MICROSOFT','GITHUB','SAML','OIDC') NOT NULL,
    provider_user_id  VARCHAR(191)                                                               NOT NULL COMMENT 'OIDC sub, SAML NameID 등',
    email_at_provider VARCHAR(320)                                                               NULL     DEFAULT NULL,
    name_at_provider  VARCHAR(100)                                                               NULL     DEFAULT NULL,
    raw_claims        JSON                                                                       NULL     DEFAULT NULL,

    reg_dt            DATETIME                                                                   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dt            DATETIME                                                                   NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    del_dt            DATETIME                                                                   NULL     DEFAULT NULL,

    CONSTRAINT fk_oauth_account_id FOREIGN KEY (account_id) REFERENCES io_idp_account (id) ON DELETE CASCADE,
    UNIQUE KEY uq_provider_subject (shopl_client_id, provider_type, provider_user_id),
    INDEX idx_oauth_account_provider (account_id, provider_type),
    INDEX idx_provider_email (provider_type, email_at_provider)
);

-- 고객사 단위 SSO 설정
create table shopl_authentication.io_idp_shopl_client_sso_setting
(
    id                          VARCHAR(20) PRIMARY KEY,
    shopl_client_id             VARCHAR(20) UNIQUE                 NOT NULL,
    sso_type                    ENUM ('OIDC','SAML')               NOT NULL,

    -- OIDC 전용 필드들
    oidc_issuer                 VARCHAR(500)                       NULL     DEFAULT NULL COMMENT 'OIDC Issuer URL',
    oidc_scopes                 VARCHAR(500)                       NULL     DEFAULT 'openid email profile' COMMENT 'Required Scopes',
    oidc_response_type          VARCHAR(100)                       NULL     DEFAULT 'code' COMMENT 'Response Type',
    oidc_response_mode          VARCHAR(50)                        NULL     DEFAULT NULL COMMENT 'Response Mode',
    oidc_claims_mapping         JSON                               NULL     DEFAULT NULL COMMENT 'Claims to User Attribute Mapping',

    -- SAML 전용 필드들
    saml_entity_id              VARCHAR(500)                       NULL     DEFAULT NULL COMMENT 'SAML Entity ID',
    saml_sso_url                VARCHAR(500)                       NULL     DEFAULT NULL COMMENT 'SAML SSO URL',
    saml_slo_url                VARCHAR(500)                       NULL     DEFAULT NULL COMMENT 'SAML SLO URL',
    saml_x509_cert              TEXT                               NULL     DEFAULT NULL COMMENT 'SAML X.509 Certificate',
    saml_name_id_format         VARCHAR(200)                       NULL     DEFAULT 'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent' COMMENT 'NameID Format',
    saml_binding_sso            ENUM ('HTTP-POST','HTTP-Redirect') NULL     DEFAULT 'HTTP-POST' COMMENT 'SSO Binding',
    saml_binding_slo            ENUM ('HTTP-POST','HTTP-Redirect') NULL     DEFAULT 'HTTP-POST' COMMENT 'SLO Binding',
    saml_want_assertions_signed TINYINT(1)                         NULL     DEFAULT 1 COMMENT 'Want Assertions Signed',
    saml_want_response_signed   TINYINT(1)                         NULL     DEFAULT 1 COMMENT 'Want Response Signed',
    saml_signature_algorithm    VARCHAR(100)                       NULL     DEFAULT 'http://www.w3.org/2001/04/xmldsig-more#rsa-sha256' COMMENT 'Signature Algorithm',
    saml_digest_algorithm       VARCHAR(100)                       NULL     DEFAULT 'http://www.w3.org/2001/04/xmlenc#sha256' COMMENT 'Digest Algorithm',
    saml_attribute_mapping      JSON                               NULL     DEFAULT NULL COMMENT 'SAML Attribute Mapping',

    -- 공통 필드들
    redirect_uris               JSON                               NULL     DEFAULT NULL COMMENT 'Allowed Redirect URIs',
    auto_provision              TINYINT(1)                         NOT NULL DEFAULT 1 COMMENT '자동 계정 생성 여부',
    default_role                VARCHAR(50)                        NULL     DEFAULT NULL COMMENT '기본 할당 역할',

    reg_dt                      DATETIME                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dt                      DATETIME                           NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    del_dt                      DATETIME                           NULL     DEFAULT NULL,

    INDEX idx_shopl_client_id (shopl_client_id),

    -- SAML 필수 필드 체크
    CONSTRAINT chk_saml_required CHECK (sso_type != 'SAML' OR
                                        (saml_entity_id IS NOT NULL AND saml_sso_url IS NOT NULL AND
                                         saml_x509_cert IS NOT NULL))
);

create table shopl_authentication.io_idp_shopl_client_mapping
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    shopl_client_id VARCHAR(20)  NOT NULL,
    idp_client_id   VARCHAR(255) NOT NULL
)

