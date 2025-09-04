-- 기본 로그인 테스트용 샘플 데이터 INSERT 스크립트

-- 1. 테스트용 계정 데이터
-- 비밀번호는 BCrypt로 암호화된 값 사용 (평문: "password123")
INSERT INTO shopl_authentication.io_idp_account
(id, shopl_client_id, shopl_user_id, email, phone, name, status, is_email_verified, is_temp_pwd, pwd, failed_attempts, reg_dt)
VALUES
-- 이메일 로그인 테스트용 계정
('ACCT001', 'CLIENT001', 'user001', 'test@example.com', '01012345678', '홍길동', 'ACTIVE', 1, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, NOW()),
('ACCT002', 'CLIENT001', 'user002', 'admin@example.com', '01023456789', '관리자', 'ACTIVE', 1, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, NOW()),

-- 전화번호 로그인 테스트용 계정 (이메일 없음)
('ACCT003', 'CLIENT001', 'user003', NULL, '01034567890', '김철수', 'ACTIVE', 0, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, NOW()),

-- 다른 클라이언트 계정
('ACCT004', 'CLIENT002', 'user004', 'user@client2.com', '01045678901', '이영희', 'ACTIVE', 1, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, NOW()),

-- 임시 비밀번호 계정 (평문: "temp1234")
('ACCT005', 'CLIENT001', 'user005', 'temp@example.com', '01056789012', '임시사용자', 'ACTIVE', 1, 1, '{bcrypt}$2a$10$8K1p/wFkTbsJHOfbCo8.7uKn4UE5vEn4DzD5w9I9vEHjpZYPv4FXi', 0, NOW()),

-- 계정 잠김 테스트용 계정 (3시간 후 자동 해제)
('ACCT006', 'CLIENT001', 'user006', 'locked@example.com', '01067890123', '잠김사용자', 'ACTIVE', 1, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 5, NOW()),

-- 비활성 계정
('ACCT007', 'CLIENT001', 'user007', 'inactive@example.com', '01078901234', '비활성사용자', 'INACTIVE', 1, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, NOW()),

-- 이메일과 전화번호 모두 있는 계정
('ACCT008', 'CLIENT001', 'user008', 'both@example.com', '01089012345', '종합사용자', 'ACTIVE', 1, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, NOW()),

-- NoOp 암호화 테스트용 계정 (개발용)
('ACCT009', 'CLIENT001', 'user009', 'noop@example.com', '01090123456', 'NoOp사용자', 'ACTIVE', 1, 0, '{noop}plaintext', 0, NOW()),

-- 다양한 전화번호 형식 테스트
('ACCT010', 'CLIENT001', 'user010', 'phone1@example.com', '010-1111-2222', '하이픈전화', 'ACTIVE', 1, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, NOW()),
('ACCT011', 'CLIENT001', 'user011', 'phone2@example.com', '01022223333', '일반전화', 'ACTIVE', 1, 0, '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 0, NOW());

-- 2. OAuth2 클라이언트 등록 데이터 (Spring Authorization Server용)
INSERT INTO shopl_authentication.io_idp_client
(id, client_id, client_id_issued_at, client_secret, client_secret_expires_at, client_name,
 client_authentication_methods, authorization_grant_types, redirect_uris, post_logout_redirect_uris,
 scopes, client_settings, token_settings)
VALUES
    ('CLIENT_REG_001', 'oauth2-client', NOW(), '{noop}secret', NULL, 'Test OAuth2 Client',
     'client_secret_basic,client_secret_post',
     'authorization_code,refresh_token',
     'http://localhost:9001/dashboard,http://localhost:9001/callback',
     'http://localhost:9001/logout',
     'openid,profile,read,write',
     '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
     '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}');

-- 3. shopl client와 OAuth client 매핑
INSERT INTO shopl_authentication.io_idp_shopl_client_mapping
(shopl_client_id, idp_client_id)
VALUES
    ('CLIENT001', 'CLIENT_REG_001'),
    ('CLIENT002', 'CLIENT_REG_001');

-- 4. 계정 잠김 테스트를 위한 업데이트 (3시간 후 자동 해제)
UPDATE shopl_authentication.io_idp_account
SET locked_until_dt = DATE_ADD(NOW(), INTERVAL 3 HOUR)
WHERE id = 'ACCT006';

-- 5. 비밀번호 만료 테스트 계정
INSERT INTO shopl_authentication.io_idp_account
(id, shopl_client_id, shopl_user_id, email, phone, name, status, is_email_verified, is_temp_pwd,
 pwd, pwd_expires_dt, failed_attempts, reg_dt)
VALUES
    ('ACCT012', 'CLIENT001', 'user012', 'expired@example.com', '01012341234', '만료사용자', 'ACTIVE', 1, 0,
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     DATE_SUB(NOW(), INTERVAL 1 DAY), 0, NOW());