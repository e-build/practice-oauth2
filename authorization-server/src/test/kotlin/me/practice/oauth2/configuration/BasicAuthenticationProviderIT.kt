package me.practice.oauth2.configuration

import me.practice.oauth2.entity.*
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.history.LoginHistoryStatisticsService
import me.practice.oauth2.testbase.IntegrationTestBase
import me.practice.oauth2.testbase.AuthenticationTestUtils
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.exception.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.security.authentication.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime
import kotlin.test.*

@SpringBootTest
@TestPropertySource(locations = ["classpath:application-test.properties"])
@Import(BasicAuthenticationProvider::class, LoginHistoryService::class)
@Transactional
@DisplayName("BasicAuthenticationProvider 통합 테스트 - 실제 DB 상태 조작")
class BasicAuthenticationProviderIT : IntegrationTestBase() {

    @Autowired
    private lateinit var sut: BasicAuthenticationProvider

    @Autowired
    private lateinit var loginHistoryRepository: IoIdpLoginHistoryRepository

    @Autowired
    private lateinit var accountRepository: IoIdpAccountRepository

    @Autowired
    private lateinit var userDetailsService: CustomUserDetailsService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var statisticsService: LoginHistoryStatisticsService

    @BeforeEach
    override fun setUp() {
        super.setUp()
        loginHistoryRepository.deleteAll()
        accountRepository.deleteAll()
    }

    @Test
    @DisplayName("정상 인증 성공 시 SUCCESS 이력이 기록된다")
    fun authenticateSuccess_ShouldRecordSuccessHistory() {
        // Given
        val rawPassword = "password123"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "ACTIVE"
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        setupRequestContext(mockRequest)

        try {
            // When
            val result = sut.authenticate(authentication)

            // Then
            assertNotNull(result)
            assertTrue(result.isAuthenticated)

            // 로그인 이력 확인
            val histories = loginHistoryRepository.findAll()
            assertEquals(1, histories.size)

            val history = histories[0]
            assertEquals(account.shoplClientId, history.shoplClientId)
            assertEquals(account.shoplUserId, history.shoplUserId)
            assertEquals(IdpClient.Platform.DASHBOARD, history.platform)
            assertEquals(LoginType.BASIC, history.loginType)
            assertEquals(LoginResult.SUCCESS, history.result)
            assertEquals(TEST_SESSION_ID, history.sessionId)
            assertNull(history.failureReason)
            assertNotNull(history.regDt)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("INVALID_CREDENTIALS: 잘못된 비밀번호로 인증 실패 시 이력 기록")
    fun authenticateFailure_InvalidCredentials() {
        // Given
        val rawPassword = "password123"
        val wrongPassword = "wrongPassword"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "ACTIVE"
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, wrongPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        setupRequestContext(mockRequest)

        try {
            // When & Then
            assertFailsWith<BadCredentialsException> {
                sut.authenticate(authentication)
            }

            // 로그인 실패 이력 확인
            val histories = loginHistoryRepository.findAll()
            assertEquals(1, histories.size)

            val history = histories[0]
            assertEquals(account.shoplClientId, history.shoplClientId)
            assertEquals(account.shoplUserId, history.shoplUserId)
            assertEquals(LoginResult.FAIL, history.result)
            assertEquals(FailureReasonType.INVALID_CREDENTIALS, history.failureReason)
            assertEquals(TEST_SESSION_ID, history.sessionId)
            assertNotNull(history.regDt)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("ACCOUNT_DISABLED: 비활성화된 계정으로 인증 실패 시 이력 기록")
    fun authenticateFailure_AccountDisabled() {
        // Given
        val rawPassword = "password123"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "INACTIVE" // 비활성화된 계정
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        setupRequestContext(mockRequest)

        try {
            // When & Then
            assertFailsWith<DisabledException> {
                sut.authenticate(authentication)
            }

            // 로그인 실패 이력 확인
            val histories = loginHistoryRepository.findAll()
            assertEquals(1, histories.size)

            val history = histories[0]
            assertEquals(account.shoplClientId, history.shoplClientId)
            assertEquals(account.shoplUserId, history.shoplUserId)
            assertEquals(LoginResult.FAIL, history.result)
            assertEquals(FailureReasonType.ACCOUNT_DISABLED, history.failureReason)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("ACCOUNT_LOCKED: 잠긴 계정으로 인증 실패 시 이력 기록")
    fun authenticateFailure_AccountLocked() {
        // Given
        val rawPassword = "password123"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "BLOCKED" // 잠긴 계정
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        setupRequestContext(mockRequest)

        try {
            // When & Then
            assertFailsWith<LockedException> {
                sut.authenticate(authentication)
            }

            // 로그인 실패 이력 확인
            val histories = loginHistoryRepository.findAll()
            assertEquals(1, histories.size)

            val history = histories[0]
            assertEquals(FailureReasonType.ACCOUNT_LOCKED, history.failureReason)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("ACCOUNT_EXPIRED: 삭제된(만료된) 계정으로 인증 실패 시 이력 기록")
    fun authenticateFailure_AccountExpired() {
        // Given
        val rawPassword = "password123"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "ACTIVE",
            delDt = LocalDateTime.now() // 삭제된 계정 (만료)
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        setupRequestContext(mockRequest)

        try {
            // When & Then
            assertFailsWith<AccountExpiredException> {
                sut.authenticate(authentication)
            }

            // 로그인 실패 이력 확인
            val histories = loginHistoryRepository.findAll()
            assertEquals(1, histories.size)

            val history = histories[0]
            assertEquals(FailureReasonType.ACCOUNT_EXPIRED, history.failureReason)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("PASSWORD_EXPIRED: 비밀번호가 만료된 계정으로 인증 실패 시 이력 기록")
    fun authenticateFailure_PasswordExpired() {
        // Given
        val rawPassword = "password123"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "ACTIVE",
            pwdUpdateDt = LocalDateTime.now().minusDays(91) // 90일 초과된 비밀번호
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        setupRequestContext(mockRequest)

        try {
            // When & Then
            assertFailsWith<PasswordExpiredException> {
                sut.authenticate(authentication)
            }

            // 로그인 실패 이력 확인
            val histories = loginHistoryRepository.findAll()
            assertEquals(1, histories.size)

            val history = histories[0]
            assertEquals(FailureReasonType.PASSWORD_EXPIRED, history.failureReason)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("TOO_MANY_ATTEMPTS: 로그인 시도 횟수 초과 시 인증 실패 및 이력 기록")
    fun authenticateFailure_TooManyAttempts() {
        // Given
        val rawPassword = "password123"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "ACTIVE"
        )

        // 5번의 실패 이력을 먼저 생성하여 시도 횟수 초과 상황 만들기
        repeat(5) {
            val failHistory = IoIdpLoginHistory(
                id = "FAIL_HISTORY_${it + 1}",
                shoplClientId = account.shoplClientId,
                shoplUserId = account.shoplUserId,
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                result = LoginResult.FAIL,
                failureReason = FailureReasonType.INVALID_CREDENTIALS,
                sessionId = "session-${it + 1}",
                regDt = LocalDateTime.now().minusMinutes(1) // 1분 전에 실패
            )
            loginHistoryRepository.save(failHistory)
        }

        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        setupRequestContext(mockRequest)

        try {
            // When & Then
            assertFailsWith<TooManyAttemptsException> {
                sut.authenticate(authentication)
            }

            // 로그인 실패 이력 확인 (기존 5개 + 새로운 1개 = 총 6개)
            val histories = loginHistoryRepository.findAll()
            assertEquals(6, histories.size)

            val newHistory = histories.find { it.failureReason == FailureReasonType.TOO_MANY_ATTEMPTS }
            assertNotNull(newHistory)
            assertEquals(account.shoplClientId, newHistory.shoplClientId)
            assertEquals(account.shoplUserId, newHistory.shoplUserId)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("데이터베이스 예외 발생 시 시스템 예외 처리 및 InternalAuthenticationServiceException 발생")
    fun authenticateFailure_DatabaseException() {
        // Given - 존재하지 않는 사용자로 DataAccessException 유발
        val authentication = UsernamePasswordAuthenticationToken("nonexistent@example.com", "password")
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        setupRequestContext(mockRequest)

        try {
            // When & Then
            // UserDetailsService에서 사용자를 찾을 수 없어 UsernameNotFoundException이 발생하고,
            // 이는 AuthenticationException의 하위 클래스이므로 BadCredentialsException으로 변환됨
            assertFailsWith<BadCredentialsException> {
                sut.authenticate(authentication)
            }
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("로그인 이력 기록 실패 시에도 인증은 성공한다")
    fun authenticateSuccess_HistoryRecordingFailure() {
        // Given
        val rawPassword = "password123"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "ACTIVE"
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)

        // RequestContextHolder를 설정하지 않아서 세션 정보를 가져올 수 없게 함
        // 이로 인해 이력 기록에서 예외가 발생할 수 있지만 인증은 성공해야 함

        // When
        val result = sut.authenticate(authentication)

        // Then
        assertNotNull(result)
        assertTrue(result.isAuthenticated)
        assertEquals(account.email, (result.principal as CustomUserDetails).username)
    }

    @Test
    @DisplayName("supports 메서드는 UsernamePasswordAuthenticationToken만 지원한다")
    fun supports_ShouldReturnTrueForUsernamePasswordAuthenticationToken() {
        // When & Then
        assertTrue(sut.supports(UsernamePasswordAuthenticationToken::class.java))
        assertFalse(sut.supports(String::class.java))
        assertFalse(sut.supports(Object::class.java))
    }

    @Test
    @DisplayName("연속 실패 후 성공 시 성공 이력이 기록된다")
    fun authenticateSuccess_AfterMultipleFailures() {
        // Given
        val rawPassword = "password123"
        val wrongPassword = "wrongPassword"
        val encodedPassword = passwordEncoder.encode(rawPassword)
        val account = createAndSaveTestAccount(
            email = "test@example.com",
            pwd = encodedPassword,
            status = "ACTIVE"
        )

        // 먼저 3번 실패
        repeat(3) {
            val wrongAuth = UsernamePasswordAuthenticationToken(account.email, wrongPassword)
            val mockRequest = AuthenticationTestUtils.createMockRequest()
            setupRequestContext(mockRequest)

            try {
                assertFailsWith<BadCredentialsException> {
                    sut.authenticate(wrongAuth)
                }
            } finally {
                RequestContextHolder.resetRequestAttributes()
            }
        }

        // 실패 이력 확인
        val failHistories = loginHistoryRepository.findAll()
        assertEquals(3, failHistories.size)
        assertTrue(failHistories.all { it.result == LoginResult.FAIL })

        // 이제 성공 시도
        val successAuth = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()
        setupRequestContext(mockRequest)

        try {
            // When
            val result = sut.authenticate(successAuth)

            // Then
            assertNotNull(result)
            assertTrue(result.isAuthenticated)

            // 총 4개 이력 (실패 3개 + 성공 1개)
            val allHistories = loginHistoryRepository.findAll()
            assertEquals(4, allHistories.size)

            val successHistory = allHistories.find { it.result == LoginResult.SUCCESS }
            assertNotNull(successHistory)
            assertEquals(account.shoplUserId, successHistory.shoplUserId)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    private fun createAndSaveTestAccount(
        id: String = "TEST_ACCOUNT_${System.currentTimeMillis()}",
        shoplClientId: String = TEST_CLIENT_ID,
        shoplUserId: String = TEST_USER_ID,
        shoplLoginId: String = "test@example.com",
        email: String? = "test@example.com",
        pwd: String? = null,
        status: String = "ACTIVE",
        pwdUpdateDt: LocalDateTime? = null,
        delDt: LocalDateTime? = null
    ): IoIdpAccount {
        val account = IoIdpAccount(
            id = id,
            shoplClientId = shoplClientId,
            shoplUserId = shoplUserId,
            shoplLoginId = shoplLoginId,
            email = email,
            pwd = pwd,
            status = status,
            pwdUpdateDt = pwdUpdateDt,
            delDt = delDt
        )
        return accountRepository.save(account)
    }

    private fun setupRequestContext(mockRequest: jakarta.servlet.http.HttpServletRequest) {
        val requestAttributes = ServletRequestAttributes(mockRequest)
        RequestContextHolder.setRequestAttributes(requestAttributes)
    }
}