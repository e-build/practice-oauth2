package me.practice.oauth2.handler

import io.mockk.*
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.service.LoginHistoryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import kotlin.test.*
import org.junit.jupiter.api.assertDoesNotThrow

@DisplayName("OAuth2 인증 실패 핸들러 - 간소화된 테스트")
class OAuth2AuthenticationFailureHandlerTest {

    private lateinit var sut: OAuth2AuthenticationFailureHandler
    private lateinit var loginHistoryService: LoginHistoryService

    @BeforeEach
    fun setUp() {
        // 핵심 의존성만 mock 처리
        loginHistoryService = mockk(relaxed = true)
        
        sut = OAuth2AuthenticationFailureHandler(loginHistoryService)
    }

    @Test
    @DisplayName("Google OAuth2 인증 실패 시 SOCIAL 타입으로 실패 이력이 기록된다")
    fun googleOAuth2FailureRecordsSocialFailure() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.requestURI = "/oauth2/authorization/google"
        request.session?.setAttribute("shopl_client_id", "CLIENT001")
        
        val exception = AuthenticationServiceException("Google authentication failed")

        // When
        assertDoesNotThrow {
            sut.onAuthenticationFailure(request, response, exception)
        }

        // Then - 핵심만 검증
        verify {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "unknown", // 실패 시에는 사용자 ID를 알 수 없음
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.SOCIAL, // Google은 SOCIAL 타입
                provider = "GOOGLE",
                failureReason = FailureReasonType.SSO_ERROR,
                sessionId = any(),
                request = request
            )
        }
    }

    @Test
    @DisplayName("Keycloak OIDC 인증 실패 시 SSO 타입으로 실패 이력이 기록된다")
    fun keycloakOidcFailureRecordsSsoFailure() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.requestURI = "/oauth2/authorization/keycloak"
        
        val exception = AuthenticationServiceException("OIDC authentication failed")

        // When
        sut.onAuthenticationFailure(request, response, exception)

        // Then
        verify {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001", // 기본값
                shoplUserId = "unknown",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.SSO, // OIDC는 SSO 타입
                provider = "OIDC",
                failureReason = FailureReasonType.SSO_ERROR,
                sessionId = any(),
                request = request
            )
        }
    }

    @Test
    @DisplayName("OAuth2Error에 따라 적절한 실패 사유가 매핑된다")
    fun oAuth2ErrorMapsToCorrectFailureReason() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        
        val error = OAuth2Error("invalid_client", "invalid_client: The client credentials are invalid", null)
        val exception = OAuth2AuthenticationException(error)

        // When
        sut.onAuthenticationFailure(request, response, exception)

        // Then
        verify {
            loginHistoryService.recordFailedLogin(
                any(), any(), any(), any(), any(),
                failureReason = FailureReasonType.INVALID_CLIENT, // invalid_client 에러
                any(), any()
            )
        }
    }

    @Test
    @DisplayName("클라이언트 ID를 요청 파라미터에서 추출한다")
    fun extractsClientIdFromRequestParameter() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.setParameter("client_id", "CLIENT002")
        
        val exception = AuthenticationServiceException("Auth failed")

        // When
        sut.onAuthenticationFailure(request, response, exception)

        // Then - CLIENT002가 사용되었는지 확인
        verify {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT002", // 파라미터에서 추출된 값
                any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    @DisplayName("로그인 이력 기록 실패해도 에러 페이지로 리다이렉트된다")
    fun redirectsToErrorPageEvenWhenHistoryFails() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        
        val exception = AuthenticationServiceException("Auth failed")
        
        every { 
            loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any(), any()) 
        } throws RuntimeException("DB Error")

        // When
        assertDoesNotThrow {
            sut.onAuthenticationFailure(request, response, exception)
        }

        // Then - 이력 기록 시도는 했는지 확인
        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any(), any())
        }
        
        // 리다이렉트가 수행되었는지 확인 (실제 URL은 중요하지 않음)
        assertNotNull(response.redirectedUrl)
        assertTrue(response.redirectedUrl?.contains("error") ?: false)
    }
}