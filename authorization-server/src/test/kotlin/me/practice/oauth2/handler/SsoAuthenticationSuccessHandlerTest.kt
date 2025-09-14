package me.practice.oauth2.handler

import io.mockk.*
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.entity.ProviderType
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.UserProvisioningService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import kotlin.test.*
import org.junit.jupiter.api.assertDoesNotThrow

@DisplayName("SSO 인증 성공 핸들러 - 간소화된 테스트")
class SsoAuthenticationSuccessHandlerTest {

    private lateinit var sut: SsoAuthenticationSuccessHandler
    private lateinit var userProvisioningService: UserProvisioningService
    private lateinit var loginHistoryService: LoginHistoryService
    
    private lateinit var mockAccount: IoIdpAccount

    @BeforeEach
    fun setUp() {
        // 핵심 의존성만 mock 처리
        userProvisioningService = mockk()
        loginHistoryService = mockk(relaxed = true)
        mockAccount = mockk(relaxed = true)
        
        every { mockAccount.shoplClientId } returns "CLIENT001"
        every { mockAccount.shoplUserId } returns "user123"
        every { mockAccount.id } returns "account-id-123"
        
        sut = SsoAuthenticationSuccessHandler(
            userProvisioningService,
            loginHistoryService
        )
    }

    @Test
    @DisplayName("Google OAuth2 로그인 시 SOCIAL 타입으로 이력이 기록된다")
    fun googleOAuth2LoginRecordsSocialHistory() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.session?.setAttribute("shopl_client_id", "CLIENT001")
        
        val mockOAuth2User = mockk<OAuth2User>(relaxed = true)
        val authentication = OAuth2AuthenticationToken(mockOAuth2User, emptyList(), "google")
        
        every { 
            userProvisioningService.provisionUser(
                mockOAuth2User, 
                "CLIENT001", 
                ProviderType.GOOGLE, 
                "google"
            ) 
        } returns mockAccount

        // When
        sut.onAuthenticationSuccess(request, response, authentication)

        // Then - 핵심만 검증
        verify {
            loginHistoryService.recordSuccessfulLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.SOCIAL, // Google은 SOCIAL 타입
                provider = "GOOGLE",
                sessionId = any(),
                request = request
            )
        }
        
        // 세션에 계정 정보가 저장되었는지 확인
        assertEquals(mockAccount, request.session?.getAttribute("authenticated_account"))
        assertEquals("GOOGLE", request.session?.getAttribute("sso_provider"))
    }

    @Test
    @DisplayName("Keycloak OIDC 로그인 시 SSO 타입으로 이력이 기록된다")
    fun keycloakOidcLoginRecordsSsoHistory() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        
        val mockOidcUser = mockk<OidcUser>(relaxed = true)
        val authentication = OAuth2AuthenticationToken(mockOidcUser, emptyList(), "keycloak")
        
        every { 
            userProvisioningService.provisionOidcUser(mockOidcUser, "CLIENT001", "keycloak") 
        } returns mockAccount

        // When
        sut.onAuthenticationSuccess(request, response, authentication)

        // Then - 핵심만 검증
        verify {
            loginHistoryService.recordSuccessfulLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.SSO, // OIDC는 SSO 타입
                provider = "OIDC",
                sessionId = any(),
                request = request
            )
        }
    }

    @Test
    @DisplayName("사용자 프로비저닝 실패 시 로그인 이력을 기록하지 않는다")
    fun provisioningFailureDoesNotRecordHistory() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        
        val mockOAuth2User = mockk<OAuth2User>(relaxed = true)
        val authentication = OAuth2AuthenticationToken(mockOAuth2User, emptyList(), "google")
        
        every { 
            userProvisioningService.provisionUser(any(), any(), any(), any()) 
        } throws RuntimeException("Provisioning failed")

        // When
        assertDoesNotThrow {
            sut.onAuthenticationSuccess(request, response, authentication)
        }

        // Then - 이력이 기록되지 않았는지 확인
        verify(exactly = 0) {
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("로그인 이력 기록 실패해도 인증 처리는 계속된다")
    fun authenticationSucceedsEvenWhenHistoryFails() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        
        val mockOAuth2User = mockk<OAuth2User>(relaxed = true)
        val authentication = OAuth2AuthenticationToken(mockOAuth2User, emptyList(), "google")
        
        every { 
            userProvisioningService.provisionUser(any(), any(), any(), any()) 
        } returns mockAccount
        
        every { 
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any()) 
        } throws RuntimeException("DB Error")

        // When
        assertDoesNotThrow {
            sut.onAuthenticationSuccess(request, response, authentication)
        }

        // Then - 세션에는 정상적으로 정보가 저장되었는지 확인
        assertEquals(mockAccount, request.session?.getAttribute("authenticated_account"))
        
        // 이력 기록 시도는 했는지 확인
        verify(exactly = 1) {
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any())
        }
    }
}