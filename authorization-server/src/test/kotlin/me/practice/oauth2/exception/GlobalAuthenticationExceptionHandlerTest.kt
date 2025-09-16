package me.practice.oauth2.exception

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.service.LoginHistoryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.client.RestClientException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.sql.SQLException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("GlobalAuthenticationExceptionHandler 단위 테스트")
class GlobalAuthenticationExceptionHandlerTest {

    private lateinit var sut: GlobalAuthenticationExceptionHandler
    private lateinit var loginHistoryService: LoginHistoryService
    private lateinit var mockRequest: HttpServletRequest
    private lateinit var mockSession: HttpSession

    @BeforeEach
    fun setUp() {
        loginHistoryService = mockk()
        mockRequest = mockk()
        mockSession = mockk()

        sut = GlobalAuthenticationExceptionHandler(loginHistoryService)

        // Mock 기본 설정
        every { mockRequest.session } returns mockSession
        every { mockSession.id } returns "session-123"
        every { mockRequest.requestURI } returns "/login"
        every { loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any()) } just Runs
    }

    @Test
    @DisplayName("EXTERNAL_PROVIDER_ERROR: RestClientException을 올바른 실패 사유로 매핑한다")
    fun handleSystemException_RestClientException() {
        // Given
        val exception = RestClientException("External service error")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.EXTERNAL_PROVIDER_ERROR,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("SYSTEM_ERROR: DataAccessException을 올바른 실패 사유로 매핑한다")
    fun handleSystemException_DataAccessException() {
        // Given
        val exception = mockk<DataAccessException>()
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.SYSTEM_ERROR,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("SYSTEM_ERROR: SQLException을 올바른 실패 사유로 매핑한다")
    fun handleSystemException_SQLException() {
        // Given
        val exception = SQLException("Database connection failed")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.SYSTEM_ERROR,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("NETWORK_ERROR: ConnectException을 올바른 실패 사유로 매핑한다")
    fun handleSystemException_ConnectException() {
        // Given
        val exception = ConnectException("Connection refused")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.NETWORK_ERROR,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("NETWORK_ERROR: SocketTimeoutException을 올바른 실패 사유로 매핑한다")
    fun handleSystemException_SocketTimeoutException() {
        // Given
        val exception = SocketTimeoutException("Read timeout")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.NETWORK_ERROR,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("NETWORK_ERROR: UnknownHostException을 올바른 실패 사유로 매핑한다")
    fun handleSystemException_UnknownHostException() {
        // Given
        val exception = UnknownHostException("Host not found")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.NETWORK_ERROR,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("SYSTEM_ERROR: OutOfMemoryError를 올바른 실패 사유로 매핑한다")
    fun handleSystemException_OutOfMemoryError() {
        // Given
        val exception = OutOfMemoryError("Java heap space")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.SYSTEM_ERROR,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("SYSTEM_ERROR: SecurityException을 올바른 실패 사유로 매핑한다")
    fun handleSystemException_SecurityException() {
        // Given
        val exception = SecurityException("Access denied")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.SYSTEM_ERROR,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("UNKNOWN: 예상치 못한 예외를 올바른 실패 사유로 매핑한다")
    fun handleSystemException_UnexpectedException() {
        // Given
        val exception = RuntimeException("Unexpected error")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, mockRequest, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.UNKNOWN,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("OAuth2 요청 URI를 소셜 로그인 타입으로 매핑한다")
    fun determineLoginType_OAuth2Request() {
        // Given
        every { mockRequest.requestURI } returns "/oauth2/authorization/google"
        val exception = RuntimeException("OAuth2 error")

        // When
        sut.handleSystemException(exception, mockRequest)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "UNKNOWN",
                shoplUserId = "unknown",
                platform = any(),
                loginType = LoginType.SOCIAL,
                failureReason = FailureReasonType.UNKNOWN,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("SSO 요청 URI를 SSO 로그인 타입으로 매핑한다")
    fun determineLoginType_SsoRequest() {
        // Given
        every { mockRequest.requestURI } returns "/sso/login"
        val exception = RuntimeException("SSO error")

        // When
        sut.handleSystemException(exception, mockRequest)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "UNKNOWN",
                shoplUserId = "unknown",
                platform = any(),
                loginType = LoginType.SSO,
                failureReason = FailureReasonType.UNKNOWN,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("request가 null인 경우 기본값으로 처리한다")
    fun handleSystemException_NullRequest() {
        // Given
        val exception = RuntimeException("System error")
        val shoplClientId = "CLIENT001"
        val shoplUserId = "user123"

        // When
        sut.handleSystemException(exception, null, shoplClientId, shoplUserId)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.UNKNOWN,
                sessionId = "unknown-session",
                request = null
            )
        }
    }

    @Test
    @DisplayName("기본 매개변수를 사용한 호출")
    fun handleSystemException_DefaultParameters() {
        // Given
        val exception = RuntimeException("System error")

        // When
        sut.handleSystemException(exception, mockRequest)

        // Then
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "UNKNOWN",
                shoplUserId = "unknown",
                platform = any(),
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.UNKNOWN,
                sessionId = "session-123",
                request = mockRequest
            )
        }
    }

    @Test
    @DisplayName("로그인 이력 기록 실패 시에도 예외를 발생시키지 않는다")
    fun handleSystemException_HistoryRecordingFailure() {
        // Given
        val exception = RuntimeException("System error")
        every { loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("DB Error")

        // When & Then (예외가 발생하지 않아야 함)
        sut.handleSystemException(exception, mockRequest)

        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("isAuthenticationRelated - AuthenticationException은 인증 관련으로 판단한다")
    fun isAuthenticationRelated_AuthenticationException() {
        // Given
        val exception = mockk<AuthenticationException>()

        // When & Then
        assertTrue(sut.isAuthenticationRelated(exception))
    }

    @Test
    @DisplayName("isAuthenticationRelated - DataAccessException은 인증 관련으로 판단한다")
    fun isAuthenticationRelated_DataAccessException() {
        // Given
        val exception = mockk<DataAccessException>()

        // When & Then
        assertTrue(sut.isAuthenticationRelated(exception))
    }

    @Test
    @DisplayName("isAuthenticationRelated - SQLException은 인증 관련으로 판단한다")
    fun isAuthenticationRelated_SQLException() {
        // Given
        val exception = SQLException("DB error")

        // When & Then
        assertTrue(sut.isAuthenticationRelated(exception))
    }

    @Test
    @DisplayName("isAuthenticationRelated - SecurityException은 인증 관련으로 판단한다")
    fun isAuthenticationRelated_SecurityException() {
        // Given
        val exception = SecurityException("Security error")

        // When & Then
        assertTrue(sut.isAuthenticationRelated(exception))
    }

    @Test
    @DisplayName("isAuthenticationRelated - RuntimeException은 인증 관련이 아니라고 판단한다")
    fun isAuthenticationRelated_RuntimeException() {
        // Given
        val exception = RuntimeException("Runtime error")

        // When & Then
        assertFalse(sut.isAuthenticationRelated(exception))
    }
}