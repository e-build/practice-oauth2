package me.practice.oauth2.configuration

import me.practice.oauth2.entity.*
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.testbase.AuthenticationIntegrationTestBase
import me.practice.oauth2.testbase.AuthenticationTestUtils
import me.practice.oauth2.testbase.AccountTestHelper
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.exception.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.*
import java.time.LocalDateTime
import kotlin.test.*

@DisplayName("BasicAuthenticationProvider 통합 테스트 - 실제 DB 상태 조작")
class BasicAuthenticationProviderIT : AuthenticationIntegrationTestBase() {

    @Autowired
    private lateinit var sut: BasicAuthenticationProvider

    @Autowired
    private lateinit var accountRepository: IoIdpAccountRepository

    @Autowired
    private lateinit var accountTestHelper: AccountTestHelper

    @BeforeEach
    override fun setUp() {
        super.setUp()
        accountRepository.deleteAll()
    }

    @Test
    @DisplayName("정상 인증 성공 시 SUCCESS 이력이 기록된다")
    fun authenticateSuccess_ShouldRecordSuccessHistory() {
        // Given
        val rawPassword = "password123"
        val account = accountTestHelper.createAndSaveTestAccount(
            email = "test@example.com",
            rawPassword = rawPassword,
            status = "ACTIVE"
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When & Then
        withRequestContext(mockRequest) {
            val result = sut.authenticate(authentication)

            assertNotNull(result)
            assertTrue(result.isAuthenticated)

            // 로그인 이력 검증
            assertLoginHistory(
                expectedResult = LoginResult.SUCCESS,
                expectedShoplClientId = account.shoplClientId,
                expectedShoplUserId = account.shoplUserId
            )
        }
    }

    @Test
    @DisplayName("INVALID_CREDENTIALS: 잘못된 비밀번호로 인증 실패 시 이력 기록")
    fun authenticateFailure_InvalidCredentials() {
        // Given
        val rawPassword = "password123"
        val wrongPassword = "wrongPassword"
        val account = accountTestHelper.createAndSaveTestAccount(
            email = "test@example.com",
            rawPassword = rawPassword,
            status = "ACTIVE"
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, wrongPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When & Then
        withRequestContext(mockRequest) {
            assertFailsWith<BadCredentialsException> {
                sut.authenticate(authentication)
            }

            // 로그인 이력 검증
            assertLoginHistory(
                expectedResult = LoginResult.FAIL,
                expectedFailureReason = FailureReasonType.INVALID_CREDENTIALS,
                expectedShoplClientId = account.shoplClientId,
                expectedShoplUserId = account.shoplUserId
            )
        }
    }


    @Test
    @DisplayName("ACCOUNT_DISABLED: 비활성화된 계정으로 인증 실패 시 이력 기록")
    fun authenticateFailure_AccountDisabled() {
        // Given
        val rawPassword = "password123"
        val account = accountTestHelper.createAndSaveTestAccount(
            email = "test@example.com",
            rawPassword = rawPassword,
            status = "BLOCKED" // BLOCKED 상태는 isEnabled() = false가 되어 DisabledException 발생
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When & Then
        withRequestContext(mockRequest) {
            assertFailsWith<DisabledException> {
                sut.authenticate(authentication)
            }

            // 로그인 이력 검증
            assertLoginHistory(
                expectedResult = LoginResult.FAIL,
                expectedFailureReason = FailureReasonType.ACCOUNT_DISABLED,
                expectedShoplClientId = account.shoplClientId,
                expectedShoplUserId = account.shoplUserId
            )
        }
    }

    // 삭제된 계정은 CustomUserDetailsService에서 UsernameNotFoundException이 발생하여
    // BasicAuthenticationProvider의 예외 처리 로직이 실행되지 않으므로 별도 테스트 케이스로 분리

    @Test
    @DisplayName("PASSWORD_EXPIRED: 비밀번호가 만료된 계정으로 인증 실패 시 이력 기록")
    fun authenticateFailure_PasswordExpired() {
        // Given
        val rawPassword = "password123"
        val account = accountTestHelper.createAndSaveTestAccount(
            email = "test@example.com",
            rawPassword = rawPassword,
            status = "ACTIVE",
            pwdUpdateDt = LocalDateTime.now().minusDays(91) // 90일 초과된 비밀번호
        )
        val authentication = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When & Then
        withRequestContext(mockRequest) {
            assertFailsWith<PasswordExpiredException> {
                sut.authenticate(authentication)
            }

            // 로그인 이력 검증
            assertLoginHistory(
                expectedResult = LoginResult.FAIL,
                expectedFailureReason = FailureReasonType.PASSWORD_EXPIRED,
                expectedShoplClientId = account.shoplClientId,
                expectedShoplUserId = account.shoplUserId
            )
        }
    }

    @Test
    @DisplayName("TOO_MANY_ATTEMPTS: 로그인 시도 횟수 초과 시 인증 실패 및 이력 기록")
    fun authenticateFailure_TooManyAttempts() {
        // Given
        val rawPassword = "password123"
        val account = accountTestHelper.createAndSaveTestAccount(
            email = "test@example.com",
            rawPassword = rawPassword,
            status = "ACTIVE"
        )

        // 5번의 실패 이력을 먼저 생성하여 시도 횟수 초과 상황 만들기
        repeat(5) {
            val failHistory = IoIdpUserLoginHistory(
				idpClientId = "",
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

        // When & Then
        withRequestContext(mockRequest) {
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
        }
    }

    // 존재하지 않는 사용자는 CustomUserDetailsService에서 UsernameNotFoundException이 발생하여
    // BasicAuthenticationProvider의 예외 처리 로직이 실행되지 않으므로 별도 테스트 케이스로 분리

    @Test
    @DisplayName("로그인 이력 기록 실패 시에도 인증은 성공한다")
    fun authenticateSuccess_HistoryRecordingFailure() {
        // Given
        val rawPassword = "password123"
        val account = accountTestHelper.createAndSaveTestAccount(
            email = "test@example.com",
            rawPassword = rawPassword,
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
        val account = accountTestHelper.createAndSaveTestAccount(
            email = "test@example.com",
            rawPassword = rawPassword,
            status = "ACTIVE"
        )

        // 먼저 3번 실패
        repeat(3) {
            val wrongAuth = UsernamePasswordAuthenticationToken(account.email, wrongPassword)
            val mockRequest = AuthenticationTestUtils.createMockRequest()

            withRequestContext(mockRequest) {
                assertFailsWith<BadCredentialsException> {
                    sut.authenticate(wrongAuth)
                }
            }
        }

        // 실패 이력 확인
        val failHistories = loginHistoryRepository.findAll()
        assertEquals(3, failHistories.size)
        assertTrue(failHistories.all { it.result == LoginResult.FAIL })

        // 이제 성공 시도
        val successAuth = UsernamePasswordAuthenticationToken(account.email, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        withRequestContext(mockRequest) {
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
        }
    }

}