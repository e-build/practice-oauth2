package me.practice.oauth2.testbase

import me.practice.oauth2.configuration.CustomUserDetails
import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.ProviderType
import org.mockito.Mockito.*
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import java.time.Instant

/**
 * 인증 관련 테스트 유틸리티
 */
object AuthenticationTestUtils {

    /**
     * 테스트용 IoIdpAccount 생성
     */
    fun createTestAccount(
        id: String = "TEST_ACCOUNT_001",
        shoplClientId: String = IntegrationTestBase.TEST_CLIENT_ID,
        shoplUserId: String = IntegrationTestBase.TEST_USER_ID,
        shoplLoginId: String = "test@example.com",
        email: String? = "test@example.com",
        pwd: String? = "encodedPassword",
        status: String = "ACTIVE"
    ): IoIdpAccount {
        return IoIdpAccount(
            id = id,
            shoplClientId = shoplClientId,
            shoplUserId = shoplUserId,
            shoplLoginId = shoplLoginId,
            email = email,
            pwd = pwd,
            status = status
        )
    }

    /**
     * 테스트용 CustomUserDetails 생성
     */
    fun createTestUserDetails(
        account: IoIdpAccount = createTestAccount()
    ): CustomUserDetails {
        return CustomUserDetails(account)
    }

    /**
     * 모킹된 HttpServletRequest 생성
     */
    fun createMockRequest(
        sessionId: String = IntegrationTestBase.TEST_SESSION_ID,
        userAgent: String = IntegrationTestBase.TEST_USER_AGENT,
        ipAddress: String = IntegrationTestBase.TEST_IP_ADDRESS,
        clientId: String? = IntegrationTestBase.TEST_CLIENT_ID
    ): HttpServletRequest {
        val request = mock(HttpServletRequest::class.java)
        val session = mock(HttpSession::class.java)

        `when`(request.session).thenReturn(session)
        `when`(session.id).thenReturn(sessionId)
        `when`(request.getHeader("User-Agent")).thenReturn(userAgent)
        `when`(request.remoteAddr).thenReturn(ipAddress)

        if (clientId != null) {
            `when`(request.getParameter("client_id")).thenReturn(clientId)
        }

        return request
    }

    /**
     * 특정 URI를 가진 모킹된 HttpServletRequest 생성
     */
    fun createMockRequestWithUri(
        uri: String,
        sessionId: String = IntegrationTestBase.TEST_SESSION_ID,
        userAgent: String = IntegrationTestBase.TEST_USER_AGENT,
        ipAddress: String = IntegrationTestBase.TEST_IP_ADDRESS,
        clientId: String? = IntegrationTestBase.TEST_CLIENT_ID
    ): HttpServletRequest {
        val request = createMockRequest(sessionId, userAgent, ipAddress, clientId)
        `when`(request.requestURI).thenReturn(uri)
        return request
    }

    /**
     * 모킹된 OAuth2User 생성
     */
    fun createMockOAuth2User(
        providerId: String = "google-123456",
        email: String = "test@example.com",
        name: String = "Test User"
    ): OAuth2User {
        val oauth2User = mock(OAuth2User::class.java)
        val attributes = mapOf(
            "sub" to providerId,
            "email" to email,
            "name" to name
        )

        `when`(oauth2User.attributes).thenReturn(attributes)
        `when`(oauth2User.name).thenReturn(providerId)

        return oauth2User
    }

    /**
     * 모킹된 OidcUser 생성
     */
    fun createMockOidcUser(
        providerId: String = "google-123456",
        email: String = "test@example.com",
        name: String = "Test User"
    ): OidcUser {
        val oidcUser = mock(OidcUser::class.java)
        val attributes = mapOf(
            "sub" to providerId,
            "email" to email,
            "name" to name,
            "email_verified" to true
        )

        val idToken = mock(OidcIdToken::class.java)
        `when`(idToken.subject).thenReturn(providerId)
        `when`(idToken.email).thenReturn(email)
        `when`(idToken.issuedAt).thenReturn(Instant.now())
        `when`(idToken.expiresAt).thenReturn(Instant.now().plusSeconds(3600))

        `when`(oidcUser.attributes).thenReturn(attributes)
        `when`(oidcUser.name).thenReturn(providerId)
        `when`(oidcUser.idToken).thenReturn(idToken)

        return oidcUser
    }
}