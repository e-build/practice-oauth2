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

-- SSO 설정 테이블
CREATE TABLE shopl.io_client_sso_setting
(
    id                          VARCHAR(20) PRIMARY KEY,
    client_id                   VARCHAR(20) NOT NULL,
    sso_type                    ENUM('OIDC', 'SAML', 'OAUTH2') NOT NULL,

    -- OIDC 설정 필드
    oidc_client_id              VARCHAR(50)  NULL,
    oidc_client_secret          VARCHAR(500) NULL,
    oidc_issuer                 VARCHAR(500) NULL,
    oidc_scopes                 VARCHAR(500) NULL DEFAULT 'openid email profile',
    oidc_response_type          VARCHAR(100) NULL DEFAULT 'code',
    oidc_response_mode          VARCHAR(50)  NULL,
    oidc_claims_mapping         JSON         NULL,

    -- SAML 설정 필드
    saml_entity_id              VARCHAR(500) NULL,
    saml_sso_url                VARCHAR(500) NULL,
    saml_slo_url                VARCHAR(500) NULL,
    saml_x509_cert              TEXT         NULL,
    saml_name_id_format         VARCHAR(200) NULL DEFAULT 'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent',
    saml_binding_sso            ENUM('HTTP-POST', 'HTTP-Redirect') NULL DEFAULT 'HTTP-POST',
    saml_binding_slo            ENUM('HTTP-POST', 'HTTP-Redirect') NULL DEFAULT 'HTTP-POST',
    saml_want_assertions_signed TINYINT(1)   NULL DEFAULT 1,
    saml_want_response_signed   TINYINT(1)   NULL DEFAULT 1,
    saml_signature_algorithm    VARCHAR(100) NULL DEFAULT 'http://www.w3.org/2001/04/xmldsig-more#rsa-sha256',
    saml_digest_algorithm       VARCHAR(100) NULL DEFAULT 'http://www.w3.org/2001/04/xmlenc#sha256',
    saml_attribute_mapping      JSON         NULL,

    -- OAuth2 설정 필드
    oauth2_client_id            VARCHAR(50)  NULL,
    oauth2_client_secret        VARCHAR(500) NULL,
    oauth2_authorization_uri    VARCHAR(500) NULL,
    oauth2_token_uri            VARCHAR(500) NULL,
    oauth2_user_info_uri        VARCHAR(500) NULL,
    oauth2_scopes               VARCHAR(500) NULL,
    oauth2_user_name_attribute  VARCHAR(100) NULL DEFAULT 'sub',

    -- 공통 설정 필드
    redirect_uris               JSON         NULL,
    auto_provision              TINYINT(1)   NOT NULL DEFAULT 1,
    default_role                VARCHAR(50)  NULL,

    reg_dt                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dt                      DATETIME     NULL     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    del_dt                      DATETIME     NULL,

    UNIQUE INDEX idx_client_id_unique (client_id),
    INDEX idx_client_id (client_id),
    INDEX idx_sso_type (sso_type),
    INDEX idx_oidc_client_id (oidc_client_id),
    INDEX idx_saml_entity_id (saml_entity_id),
    INDEX idx_oauth2_client_id (oauth2_client_id),

    FOREIGN KEY (client_id) REFERENCES io_client_info(id) ON DELETE CASCADE
);