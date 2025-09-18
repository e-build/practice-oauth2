package me.practice.oauth2.exception

import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.entity.*
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.testbase.AuthenticationIntegrationTestBase
import me.practice.oauth2.testbase.AuthenticationTestUtils
import me.practice.oauth2.domain.IdpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@TestPropertySource(locations = ["classpath:application-test.yml"])
@Transactional
@DisplayName("GlobalAuthenticationExceptionHandler 통합 테스트 - 실제 로그인 이력 기록")
class GlobalAuthenticationExceptionHandlerIT : AuthenticationIntegrationTestBase() {

    @Autowired
    private lateinit var sut: GlobalAuthenticationExceptionHandler

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    @DisplayName("EXTERNAL_PROVIDER_ERROR: RestClientException 처리 시 실제 로그인 이력이 기록된다")
    fun handleSystemException_RestClientException_ShouldRecordHistory() {
        // Given
        val exception = RestClientException("External OAuth provider is unavailable")
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.EXTERNAL_PROVIDER_ERROR,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123"
        )
    }

    @Test
    @DisplayName("SYSTEM_ERROR: DataAccessException 처리 시 실제 로그인 이력이 기록된다")
    fun handleSystemException_DataAccessException_ShouldRecordHistory() {
        // Given
        val exception = DataIntegrityViolationException("Database constraint violation")
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.SYSTEM_ERROR,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123"
        )
    }

    @Test
    @DisplayName("SYSTEM_ERROR: SQLException 처리 시 실제 로그인 이력이 기록된다")
    fun handleSystemException_SQLException_ShouldRecordHistory() {
        // Given
        val exception = SQLException("Connection timeout", "08000", 1000)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.SYSTEM_ERROR,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123"
        )
    }

    @Test
    @DisplayName("NETWORK_ERROR: ConnectException 처리 시 실제 로그인 이력이 기록된다")
    fun handleSystemException_ConnectException_ShouldRecordHistory() {
        // Given
        val exception = ConnectException("Connection refused: no further information")
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.NETWORK_ERROR,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123"
        )
    }

    @Test
    @DisplayName("NETWORK_ERROR: SocketTimeoutException 처리 시 실제 로그인 이력이 기록된다")
    fun handleSystemException_SocketTimeoutException_ShouldRecordHistory() {
        // Given
        val exception = SocketTimeoutException("Read timed out")
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.NETWORK_ERROR,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123"
        )
    }

    @Test
    @DisplayName("NETWORK_ERROR: UnknownHostException 처리 시 실제 로그인 이력이 기록된다")
    fun handleSystemException_UnknownHostException_ShouldRecordHistory() {
        // Given
        val exception = UnknownHostException("oauth.google.com")
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.NETWORK_ERROR,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123"
        )
    }

    @Test
    @DisplayName("UNKNOWN: 예상치 못한 예외 처리 시 실제 로그인 이력이 기록된다")
    fun handleSystemException_UnexpectedException_ShouldRecordHistory() {
        // Given
        val exception = RuntimeException("Unexpected system error")
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.UNKNOWN,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123"
        )
    }

    @Test
    @DisplayName("OAuth2 요청 URI에 따라 로그인 타입을 SOCIAL로 설정한다")
    fun handleSystemException_OAuth2Request_ShouldRecordSocialLoginType() {
        // Given
        val exception = RuntimeException("OAuth2 error")
        val mockRequest = AuthenticationTestUtils.createMockRequestWithUri("/oauth2/authorization/google")

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.UNKNOWN,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123",
            expectedLoginType = LoginType.SOCIAL
        )
    }

    @Test
    @DisplayName("SSO 요청 URI에 따라 로그인 타입을 SSO로 설정한다")
    fun handleSystemException_SsoRequest_ShouldRecordSsoLoginType() {
        // Given
        val exception = RuntimeException("SSO error")
        val mockRequest = AuthenticationTestUtils.createMockRequestWithUri("/sso/saml/login")

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.UNKNOWN,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123",
            expectedLoginType = LoginType.SSO
        )
    }

    @Test
    @DisplayName("기본 요청 URI에 따라 로그인 타입을 BASIC으로 설정한다")
    fun handleSystemException_BasicRequest_ShouldRecordBasicLoginType() {
        // Given
        val exception = RuntimeException("Basic auth error")
        val mockRequest = AuthenticationTestUtils.createMockRequestWithUri("/login")

        // When
        sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.UNKNOWN,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123",
            expectedLoginType = LoginType.BASIC
        )
    }

    @Test
    @DisplayName("HttpServletRequest가 null인 경우 기본값으로 처리한다")
    fun handleSystemException_NullRequest_ShouldUseDefaultValues() {
        // Given
        val exception = RuntimeException("System error")

        // When
        sut.handleSystemException(exception, null, "CLIENT001", "user123")

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.UNKNOWN,
            expectedShoplClientId = "CLIENT001",
            expectedShoplUserId = "user123",
            expectedLoginType = LoginType.BASIC,
            expectedSessionId = "unknown-session"
        )
    }

    @Test
    @DisplayName("기본 매개변수를 사용한 호출로 UNKNOWN 클라이언트와 사용자로 기록한다")
    fun handleSystemException_DefaultParameters_ShouldUseUnknownValues() {
        // Given
        val exception = RuntimeException("System error")

        // When
        sut.handleSystemException(exception, AuthenticationTestUtils.createMockRequest())

        // Then
        assertLoginHistory(
            expectedResult = LoginResult.FAIL,
            expectedFailureReason = FailureReasonType.UNKNOWN,
            expectedShoplClientId = "UNKNOWN",
            expectedShoplUserId = "unknown",
            expectedLoginType = LoginType.BASIC
        )
    }

    @Test
    @DisplayName("여러 예외를 연속으로 처리해도 각각 별도의 이력이 기록된다")
    fun handleSystemException_MultipleExceptions_ShouldRecordSeparateHistories() {
        // Given
        val mockRequest = AuthenticationTestUtils.createMockRequest()
        val exceptions = listOf(
            RestClientException("External error"),
            SQLException("DB error"),
            ConnectException("Network error"),
            RuntimeException("Unknown error")
        )

        // When
        exceptions.forEach { exception ->
            sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")
        }

        // Then
        val histories = loginHistoryRepository.findAll()
        assertEquals(4, histories.size)

        // 모든 이력이 실패로 기록되어야 함
        assertTrue(histories.all { it.result == LoginResult.FAIL })
        assertTrue(histories.all { it.shoplClientId == "CLIENT001" })
        assertTrue(histories.all { it.shoplUserId == "user123" })

        val failureReasons = histories.map { it.failureReason }.toSet()
        val expectedReasons = setOf(
            FailureReasonType.EXTERNAL_PROVIDER_ERROR,
            FailureReasonType.SYSTEM_ERROR,
            FailureReasonType.NETWORK_ERROR,
            FailureReasonType.UNKNOWN
        )
        assertEquals(expectedReasons, failureReasons)
    }

    @Test
    @DisplayName("System Error 타입별 정확한 매핑 확인")
    fun handleSystemException_SystemErrorTypes_ShouldMapCorrectly() {
        // Given
        val mockRequest = AuthenticationTestUtils.createMockRequest()
        val systemErrors = listOf(
            DataIntegrityViolationException("Constraint violation"),
            BadSqlGrammarException("Bad SQL", "sql", SQLException("syntax error")),
            RuntimeException("Java heap space"),
            SecurityException("Security violation")
        )

        // When
        systemErrors.forEach { exception ->
            sut.handleSystemException(exception, mockRequest, "CLIENT001", "user123")
        }

        // Then
        val histories = loginHistoryRepository.findAll()
        assertEquals(4, histories.size)

        // 모든 시스템 오류는 SYSTEM_ERROR로 매핑되어야 함
        assertTrue(histories.all { it.failureReason == FailureReasonType.SYSTEM_ERROR })
        assertTrue(histories.all { it.result == LoginResult.FAIL })
        assertTrue(histories.all { it.shoplClientId == "CLIENT001" })
        assertTrue(histories.all { it.shoplUserId == "user123" })
    }

}