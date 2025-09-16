package me.practice.oauth2.configuration

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.exception.*
import me.practice.oauth2.service.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataAccessException
import org.springframework.security.authentication.*
import org.springframework.security.core.Authentication
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("BasicAuthenticationProvider 단위 테스트")
class BasicAuthenticationProviderTest {

    private lateinit var sut: BasicAuthenticationProvider
    private lateinit var userDetailsService: CustomUserDetailsService
    private lateinit var accountValidator: AccountValidator
    private lateinit var passwordValidator: PasswordValidator
    private lateinit var loginHistoryService: LoginHistoryService
    private lateinit var requestContextService: RequestContextService
    private lateinit var loginSecurityValidator: LoginSecurityValidator
    private lateinit var globalExceptionHandler: GlobalAuthenticationExceptionHandler

    private lateinit var mockAccount: IoIdpAccount
    private lateinit var mockUserDetails: CustomUserDetails
    private lateinit var mockRequest: HttpServletRequest

    @BeforeEach
    fun setUp() {
        userDetailsService = mockk()
        accountValidator = mockk()
        passwordValidator = mockk()
        loginHistoryService = mockk()
        requestContextService = mockk()
        loginSecurityValidator = mockk()
        globalExceptionHandler = mockk()

        mockAccount = mockk()
        mockUserDetails = mockk()
        mockRequest = mockk()

        sut = BasicAuthenticationProvider(
            userDetailsService,
            accountValidator,
            passwordValidator,
            loginHistoryService,
            requestContextService,
            loginSecurityValidator,
            globalExceptionHandler
        )

        // Mock 기본 설정
        every { mockAccount.shoplClientId } returns "CLIENT001"
        every { mockAccount.shoplUserId } returns "user123"
        every { mockUserDetails.getAccount() } returns mockAccount
        every { mockUserDetails.authorities } returns emptyList()
        every { requestContextService.getCurrentRequest() } returns mockRequest
        every { requestContextService.getCurrentSessionId() } returns "session-123"
        every { loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any()) } just Runs
        every { loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any()) } just Runs
        every { globalExceptionHandler.handleSystemException(any(), any(), any(), any()) } just Runs
    }

    @Test
    @DisplayName("정상 인증 성공 시 UsernamePasswordAuthenticationToken을 반환한다")
    fun authenticateSuccess() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } just Runs
        every { passwordValidator.validatePassword(any(), any()) } just Runs

        // When
        val result = sut.authenticate(authentication)

        // Then
        assertNotNull(result)
        assertTrue(result.isAuthenticated)
        assertEquals(mockUserDetails, result.principal)
        assertEquals(null, result.credentials) // 비밀번호는 null로 설정

        verify(exactly = 1) {
            loginSecurityValidator.validateLoginAttempts("user123")
            accountValidator.validateAccountStatus(mockUserDetails)
            passwordValidator.validatePassword(mockUserDetails, password)
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any())
        }

        verify(exactly = 0) {
            loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("TOO_MANY_ATTEMPTS: 로그인 시도 횟수 초과 시 예외 발생 및 이력 기록")
    fun authenticateFailure_TooManyAttempts() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val exception = TooManyAttemptsException("user123", 5, 5)

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } throws exception

        // When & Then
        val thrownException = assertThrows<TooManyAttemptsException> {
            sut.authenticate(authentication)
        }

        assertEquals(exception, thrownException)

        verify(exactly = 1) {
            loginSecurityValidator.validateLoginAttempts("user123")
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = any(),
                loginType = any(),
                failureReason = FailureReasonType.TOO_MANY_ATTEMPTS,
                sessionId = "session-123",
                request = mockRequest
            )
        }

        verify(exactly = 0) {
            accountValidator.validateAccountStatus(any())
            passwordValidator.validatePassword(any(), any())
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("ACCOUNT_EXPIRED: 계정 만료 시 예외 발생 및 이력 기록")
    fun authenticateFailure_AccountExpired() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val exception = AccountExpiredException("user123")

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } throws exception

        // When & Then
        val thrownException = assertThrows<AccountExpiredException> {
            sut.authenticate(authentication)
        }

        assertEquals(exception, thrownException)

        verify(exactly = 1) {
            loginSecurityValidator.validateLoginAttempts("user123")
            accountValidator.validateAccountStatus(mockUserDetails)
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = any(),
                loginType = any(),
                failureReason = FailureReasonType.ACCOUNT_EXPIRED,
                sessionId = "session-123",
                request = mockRequest
            )
        }

        verify(exactly = 0) {
            passwordValidator.validatePassword(any(), any())
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("ACCOUNT_LOCKED: 계정 잠금 시 예외 발생 및 이력 기록")
    fun authenticateFailure_AccountLocked() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val exception = LockedException("Account locked")

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } throws exception

        // When & Then
        val thrownException = assertThrows<LockedException> {
            sut.authenticate(authentication)
        }

        assertEquals(exception, thrownException)

        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = any(),
                loginType = any(),
                failureReason = FailureReasonType.ACCOUNT_LOCKED,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("ACCOUNT_DISABLED: 계정 비활성화 시 예외 발생 및 이력 기록")
    fun authenticateFailure_AccountDisabled() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val exception = DisabledException("Account disabled")

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } throws exception

        // When & Then
        val thrownException = assertThrows<DisabledException> {
            sut.authenticate(authentication)
        }

        assertEquals(exception, thrownException)

        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = any(),
                loginType = any(),
                failureReason = FailureReasonType.ACCOUNT_DISABLED,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("PASSWORD_EXPIRED: 비밀번호 만료 시 예외 발생 및 이력 기록")
    fun authenticateFailure_PasswordExpired() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val exception = PasswordExpiredException("user123")

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } throws exception

        // When & Then
        val thrownException = assertThrows<PasswordExpiredException> {
            sut.authenticate(authentication)
        }

        assertEquals(exception, thrownException)

        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = any(),
                loginType = any(),
                failureReason = FailureReasonType.PASSWORD_EXPIRED,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("INVALID_CREDENTIALS: 잘못된 비밀번호 시 예외 발생 및 이력 기록")
    fun authenticateFailure_InvalidCredentials() {
        // Given
        val username = "testuser"
        val password = "wrongpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val exception = BadCredentialsException("Invalid password")

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } just Runs
        every { passwordValidator.validatePassword(any(), any()) } throws exception

        // When & Then
        val thrownException = assertThrows<BadCredentialsException> {
            sut.authenticate(authentication)
        }

        assertEquals(exception, thrownException)

        verify(exactly = 1) {
            passwordValidator.validatePassword(mockUserDetails, password)
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = any(),
                loginType = any(),
                failureReason = FailureReasonType.INVALID_CREDENTIALS,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("SYSTEM_ERROR: DataAccessException 발생 시 시스템 예외 처리 및 InternalAuthenticationServiceException 발생")
    fun authenticateFailure_DataAccessException() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val exception = mockk<DataAccessException>()

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } just Runs
        every { passwordValidator.validatePassword(any(), any()) } throws exception

        // When & Then
        val thrownException = assertThrows<InternalAuthenticationServiceException> {
            sut.authenticate(authentication)
        }

        assertEquals("Database access error", thrownException.message)
        assertEquals(exception, thrownException.cause)

        verify(exactly = 1) {
            globalExceptionHandler.handleSystemException(
                exception = exception,
                request = mockRequest,
                shoplClientId = "CLIENT001",
                shoplUserId = "user123"
            )
        }

        verify(exactly = 0) {
            loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any())
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("SYSTEM_ERROR: 기타 예외 발생 시 시스템 예외 처리 및 InternalAuthenticationServiceException 발생")
    fun authenticateFailure_UnexpectedException() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val exception = RuntimeException("Unexpected error")

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } just Runs
        every { passwordValidator.validatePassword(any(), any()) } throws exception

        // When & Then
        val thrownException = assertThrows<InternalAuthenticationServiceException> {
            sut.authenticate(authentication)
        }

        assertEquals("System error during authentication", thrownException.message)
        assertEquals(exception, thrownException.cause)

        verify(exactly = 1) {
            globalExceptionHandler.handleSystemException(
                exception = exception,
                request = mockRequest,
                shoplClientId = "CLIENT001",
                shoplUserId = "user123"
            )
        }
    }

    @Test
    @DisplayName("로그인 성공 이력 기록 실패 시에도 인증은 성공한다")
    fun authenticateSuccess_HistoryRecordingFailure() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } just Runs
        every { passwordValidator.validatePassword(any(), any()) } just Runs
        every { loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any()) } throws RuntimeException("DB Error")

        // When
        val result = sut.authenticate(authentication)

        // Then
        assertNotNull(result)
        assertTrue(result.isAuthenticated)
        assertEquals(mockUserDetails, result.principal)
    }

    @Test
    @DisplayName("로그인 실패 이력 기록 실패 시에도 원래 예외가 발생한다")
    fun authenticateFailure_HistoryRecordingFailure() {
        // Given
        val username = "testuser"
        val password = "wrongpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        val originalException = BadCredentialsException("Invalid password")

        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { loginSecurityValidator.validateLoginAttempts(any()) } just Runs
        every { accountValidator.validateAccountStatus(any()) } just Runs
        every { passwordValidator.validatePassword(any(), any()) } throws originalException
        every { loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("DB Error")

        // When & Then
        val thrownException = assertThrows<BadCredentialsException> {
            sut.authenticate(authentication)
        }

        assertEquals(originalException, thrownException)
    }

    @Test
    @DisplayName("supports 메서드는 UsernamePasswordAuthenticationToken을 지원한다")
    fun supportsUsernamePasswordAuthenticationToken() {
        // When & Then
        assertTrue(sut.supports(UsernamePasswordAuthenticationToken::class.java))
    }
}