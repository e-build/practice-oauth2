-- 운영 환경과 유사한 로그인 이력 테스트 데이터 생성
-- 다양한 시나리오와 패턴을 반영한 현실적인 데이터

-- 테스트용 클라이언트 및 계정 데이터
INSERT INTO io_idp_client (id, idp_client_id, shopl_client_id, platform, environment, client_name, client_authentication_methods, authorization_grant_types, scopes, client_settings, token_settings) VALUES
('test-client-1', 'dashboard-client-1', 'SHOPL001', 'DASHBOARD', 'QA', 'Test Dashboard Client', 'client_secret_basic', 'authorization_code,refresh_token', 'read,write', '{}', '{}'),
('test-client-2', 'app-client-1', 'SHOPL001', 'APP', 'QA', 'Test Mobile App Client', 'client_secret_basic', 'authorization_code,refresh_token', 'read,write', '{}', '{}'),
('test-client-3', 'dashboard-client-2', 'SHOPL002', 'DASHBOARD', 'QA', 'Test Dashboard Client 2', 'client_secret_basic', 'authorization_code,refresh_token', 'read,write', '{}', '{}');

INSERT INTO io_idp_account (id, shopl_client_id, shopl_user_id, shopl_login_id, email, name, pwd) VALUES
('acc001', 'SHOPL001', 'user001', 'user001_login', 'admin@company.com', '관리자', '$2a$10$example1'),
('acc002', 'SHOPL001', 'user002', 'user002_login', 'manager@company.com', '매니저', '$2a$10$example2'),
('acc003', 'SHOPL001', 'user003', 'user003_login', 'developer@company.com', '개발자', '$2a$10$example3'),
('acc004', 'SHOPL001', 'user004', 'user004_login', 'sales@company.com', '영업팀', '$2a$10$example4'),
('acc005', 'SHOPL002', 'user005', 'user005_login', 'ceo@startup.com', 'CEO', '$2a$10$example5'),
('acc006', 'SHOPL002', 'user006', 'user006_login', 'cto@startup.com', 'CTO', '$2a$10$example6');

-- 현실적인 로그인 이력 데이터 생성

-- 1. 정상적인 업무 패턴 (오전 9시~10시 출근, 오후 6시~7시 퇴근)
-- 관리자 - 매일 규칙적으로 로그인
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, result, session_id, ip_address, user_agent, location) VALUES
-- 지난 주 데이터
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-15 09:15:30.123', 'BASIC', 'SUCCESS', 'sess-001-20240115-0915', '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-15 18:45:12.456', 'BASIC', 'SUCCESS', 'sess-001-20240115-1845', '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-16 09:08:45.789', 'BASIC', 'SUCCESS', 'sess-001-20240116-0908', '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-17 09:22:15.234', 'BASIC', 'SUCCESS', 'sess-001-20240117-0922', '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea'),

-- 이번 주 데이터
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-22 09:12:30.123', 'BASIC', 'SUCCESS', 'sess-001-20240122-0912', '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-23 09:05:45.456', 'BASIC', 'SUCCESS', 'sess-001-20240123-0905', '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-24 08:58:12.789', 'BASIC', 'SUCCESS', 'sess-001-20240124-0858', '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea');

-- 2. 매니저 - 가끔 모바일에서도 접속, 소셜 로그인 병행 사용
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, provider, result, session_id, ip_address, user_agent, location) VALUES
-- 데스크톱에서 기본 로그인
('SHOPL001', 'user002', 'DASHBOARD', '2024-01-15 09:30:22.111', 'BASIC', NULL, 'SUCCESS', 'sess-002-20240115-0930', '192.168.1.25', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user002', 'DASHBOARD', '2024-01-16 09:45:33.222', 'BASIC', NULL, 'SUCCESS', 'sess-002-20240116-0945', '192.168.1.25', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea'),

-- 모바일에서 구글 소셜 로그인
('SHOPL001', 'user002', 'APP', '2024-01-16 12:15:45.333', 'SOCIAL', 'GOOGLE', 'SUCCESS', 'sess-002-20240116-1215', '203.252.33.10', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Seoul, South Korea'),
('SHOPL001', 'user002', 'APP', '2024-01-17 14:30:12.444', 'SOCIAL', 'GOOGLE', 'SUCCESS', 'sess-002-20240117-1430', '203.252.33.10', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Seoul, South Korea'),

-- 최근 출장으로 인한 다른 지역에서 로그인
('SHOPL001', 'user002', 'DASHBOARD', '2024-01-22 10:20:55.555', 'BASIC', NULL, 'SUCCESS', 'sess-002-20240122-1020', '121.131.45.120', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Busan, South Korea'),
('SHOPL001', 'user002', 'APP', '2024-01-23 08:45:30.666', 'SOCIAL', 'GOOGLE', 'SUCCESS', 'sess-002-20240123-0845', '121.131.45.120', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Busan, South Korea');

-- 3. 개발자 - 불규칙한 시간, 야근 패턴, 집에서 원격 근무
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, result, session_id, ip_address, user_agent, location) VALUES
-- 일반적인 출근 시간
('SHOPL001', 'user003', 'DASHBOARD', '2024-01-15 10:30:15.111', 'BASIC', 'SUCCESS', 'sess-003-20240115-1030', '192.168.1.35', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),

-- 야근 패턴
('SHOPL001', 'user003', 'DASHBOARD', '2024-01-15 22:45:30.222', 'BASIC', 'SUCCESS', 'sess-003-20240115-2245', '192.168.1.35', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user003', 'DASHBOARD', '2024-01-16 01:20:45.333', 'BASIC', 'SUCCESS', 'sess-003-20240116-0120', '192.168.1.35', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),

-- 재택근무 (집에서)
('SHOPL001', 'user003', 'DASHBOARD', '2024-01-17 11:15:20.444', 'BASIC', 'SUCCESS', 'sess-003-20240117-1115', '123.456.78.90', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user003', 'DASHBOARD', '2024-01-22 14:30:10.555', 'BASIC', 'SUCCESS', 'sess-003-20240122-1430', '123.456.78.90', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),

-- 주말 긴급 대응
('SHOPL001', 'user003', 'APP', '2024-01-21 15:45:30.666', 'BASIC', 'SUCCESS', 'sess-003-20240121-1545', '123.456.78.90', 'Mozilla/5.0 (Android 14; SM-G998B) AppleWebKit/537.36', 'Seoul, South Korea');

-- 4. 영업팀 - 외근 많음, 다양한 지역에서 모바일 로그인
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, provider, result, session_id, ip_address, user_agent, location) VALUES
-- 오피스에서
('SHOPL001', 'user004', 'DASHBOARD', '2024-01-15 08:45:20.111', 'BASIC', NULL, 'SUCCESS', 'sess-004-20240115-0845', '192.168.1.40', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea'),

-- 고객사 방문 중
('SHOPL001', 'user004', 'APP', '2024-01-15 14:20:35.222', 'SOCIAL', 'KAKAO', 'SUCCESS', 'sess-004-20240115-1420', '210.120.45.67', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Incheon, South Korea'),
('SHOPL001', 'user004', 'APP', '2024-01-16 16:30:45.333', 'SOCIAL', 'KAKAO', 'SUCCESS', 'sess-004-20240116-1630', '175.223.12.89', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Daejeon, South Korea'),
('SHOPL001', 'user004', 'APP', '2024-01-17 11:15:20.444', 'SOCIAL', 'KAKAO', 'SUCCESS', 'sess-004-20240117-1115', '112.185.67.34', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Gwangju, South Korea'),

-- 최근 데이터
('SHOPL001', 'user004', 'DASHBOARD', '2024-01-22 09:00:10.555', 'BASIC', NULL, 'SUCCESS', 'sess-004-20240122-0900', '192.168.1.40', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user004', 'APP', '2024-01-23 13:45:25.666', 'SOCIAL', 'KAKAO', 'SUCCESS', 'sess-004-20240123-1345', '198.162.34.78', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Busan, South Korea');

-- 5. 스타트업 CEO - SSO 사용, 다양한 시간대
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, provider, result, session_id, ip_address, user_agent, location) VALUES
-- SSO 로그인 (회사 Google Workspace 연동)
('SHOPL002', 'user005', 'DASHBOARD', '2024-01-15 07:30:15.111', 'SSO', 'GOOGLE', 'SUCCESS', 'sess-005-20240115-0730', '203.234.56.78', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL002', 'user005', 'DASHBOARD', '2024-01-16 06:45:30.222', 'SSO', 'GOOGLE', 'SUCCESS', 'sess-005-20240116-0645', '203.234.56.78', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea'),

-- 해외 출장 중
('SHOPL002', 'user005', 'APP', '2024-01-20 21:15:45.333', 'SSO', 'GOOGLE', 'SUCCESS', 'sess-005-20240120-2115', '74.125.224.72', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'San Francisco, USA'),
('SHOPL002', 'user005', 'DASHBOARD', '2024-01-21 09:30:20.444', 'SSO', 'GOOGLE', 'SUCCESS', 'sess-005-20240121-0930', '74.125.224.72', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'San Francisco, USA'),

-- 돌아온 후
('SHOPL002', 'user005', 'DASHBOARD', '2024-01-23 08:20:35.555', 'SSO', 'GOOGLE', 'SUCCESS', 'sess-005-20240123-0820', '203.234.56.78', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea');

-- 6. 스타트업 CTO - 기본 로그인, 야근 패턴
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, result, session_id, ip_address, user_agent, location) VALUES
('SHOPL002', 'user006', 'DASHBOARD', '2024-01-15 10:45:20.111', 'BASIC', 'SUCCESS', 'sess-006-20240115-1045', '203.234.56.79', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL002', 'user006', 'DASHBOARD', '2024-01-15 23:30:45.222', 'BASIC', 'SUCCESS', 'sess-006-20240115-2330', '203.234.56.79', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL002', 'user006', 'DASHBOARD', '2024-01-16 11:20:15.333', 'BASIC', 'SUCCESS', 'sess-006-20240116-1120', '203.234.56.79', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL002', 'user006', 'DASHBOARD', '2024-01-22 09:15:30.444', 'BASIC', 'SUCCESS', 'sess-006-20240122-0915', '203.234.56.79', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea');

-- 7. 로그인 실패 패턴들

-- 비밀번호 틀림 (3번 실패 후 성공)
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, result, failure_reason, session_id, ip_address, user_agent, location) VALUES
('SHOPL001', 'user002', 'DASHBOARD', '2024-01-20 09:00:10.100', 'BASIC', 'FAIL', 'INVALID_CREDENTIALS', 'sess-fail-001', '192.168.1.25', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user002', 'DASHBOARD', '2024-01-20 09:00:25.200', 'BASIC', 'FAIL', 'INVALID_CREDENTIALS', 'sess-fail-002', '192.168.1.25', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user002', 'DASHBOARD', '2024-01-20 09:00:40.300', 'BASIC', 'FAIL', 'INVALID_CREDENTIALS', 'sess-fail-003', '192.168.1.25', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user002', 'DASHBOARD', '2024-01-20 09:01:15.400', 'BASIC', 'SUCCESS', NULL, 'sess-002-20240120-0901', '192.168.1.25', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea');

-- 계정 잠김
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, result, failure_reason, session_id, ip_address, user_agent, location) VALUES
('SHOPL001', 'user003', 'DASHBOARD', '2024-01-19 14:30:10.100', 'BASIC', 'FAIL', 'ACCOUNT_LOCKED', 'sess-fail-004', '123.456.78.90', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea'),
('SHOPL001', 'user003', 'DASHBOARD', '2024-01-19 14:35:20.200', 'BASIC', 'FAIL', 'ACCOUNT_LOCKED', 'sess-fail-005', '123.456.78.90', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea');

-- 소셜 로그인 실패 (OAuth 제공자 오류)
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, provider, result, failure_reason, session_id, ip_address, user_agent, location) VALUES
('SHOPL001', 'user004', 'APP', '2024-01-18 16:20:30.100', 'SOCIAL', 'KAKAO', 'FAIL', 'EXTERNAL_PROVIDER_ERROR', 'sess-fail-006', '210.120.45.67', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Incheon, South Korea'),
('SHOPL001', 'user004', 'APP', '2024-01-18 16:22:15.200', 'SOCIAL', 'KAKAO', 'SUCCESS', NULL, 'sess-004-20240118-1622', '210.120.45.67', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Incheon, South Korea');

-- 8. 의심스러운 활동 패턴 (보안 관련)

-- 다른 국가에서 로그인 시도 (실패)
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, result, failure_reason, session_id, ip_address, user_agent, location) VALUES
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-21 03:15:30.100', 'BASIC', 'FAIL', 'SUSPICIOUS_LOCATION', 'sess-suspicious-001', '185.220.101.5', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Russia'),
('SHOPL001', 'user001', 'DASHBOARD', '2024-01-21 03:18:45.200', 'BASIC', 'FAIL', 'SUSPICIOUS_LOCATION', 'sess-suspicious-002', '185.220.101.5', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Russia');

-- 짧은 시간 내 여러 IP에서 로그인 시도
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, result, failure_reason, session_id, ip_address, user_agent, location) VALUES
('SHOPL002', 'user005', 'DASHBOARD', '2024-01-19 02:30:10.100', 'BASIC', 'FAIL', 'BRUTE_FORCE_DETECTED', 'sess-brute-001', '91.235.12.45', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Unknown'),
('SHOPL002', 'user005', 'DASHBOARD', '2024-01-19 02:30:25.200', 'BASIC', 'FAIL', 'BRUTE_FORCE_DETECTED', 'sess-brute-002', '203.45.67.89', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Unknown'),
('SHOPL002', 'user005', 'DASHBOARD', '2024-01-19 02:30:40.300', 'BASIC', 'FAIL', 'BRUTE_FORCE_DETECTED', 'sess-brute-003', '128.90.12.34', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Unknown');

-- 9. 주말/휴일 로그인 패턴
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, result, session_id, ip_address, user_agent, location) VALUES
-- 토요일 오후 (집에서 간단한 업무)
('SHOPL001', 'user001', 'APP', '2024-01-20 15:30:20.111', 'BASIC', 'SUCCESS', 'sess-weekend-001', '123.45.67.89', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Seoul, South Korea'),
('SHOPL002', 'user006', 'DASHBOARD', '2024-01-20 19:45:35.222', 'BASIC', 'SUCCESS', 'sess-weekend-002', '203.234.56.79', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', 'Seoul, South Korea');

-- 10. 새벽 시간대 로그인 (해외 고객 대응 등)
INSERT INTO io_idp_login_history (shopl_client_id, shopl_user_id, platform, login_time, login_type, provider, result, session_id, ip_address, user_agent, location) VALUES
('SHOPL001', 'user002', 'APP', '2024-01-17 02:30:45.111', 'SOCIAL', 'GOOGLE', 'SUCCESS', 'sess-early-001', '203.252.33.10', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 'Seoul, South Korea'),
('SHOPL002', 'user005', 'DASHBOARD', '2024-01-18 05:15:20.222', 'SSO', 'GOOGLE', 'SUCCESS', 'sess-early-002', '203.234.56.78', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', 'Seoul, South Korea');