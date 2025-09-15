package me.practice.oauth2.infrastructure.redis.converter

import me.practice.oauth2.configuration.CustomUserDetails
import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.infrastructure.redis.reconstructor.SsoAccountReconstructor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import java.security.Principal
import java.time.LocalDateTime

class OAuth2AttributeCoercerTest {

    private lateinit var ssoAccountReconstructor: SsoAccountReconstructor
    private lateinit var ioIdpAccountRepository: IoIdpAccountRepository
    private lateinit var coercer: OAuth2AttributeCoercer

    @BeforeEach
    fun setUp() {
        ssoAccountReconstructor = mock()
        ioIdpAccountRepository = mock()
        coercer = OAuth2AttributeCoercer(ssoAccountReconstructor)
    }

    @Test
    fun `변환할 속성이 없는 경우 원본 반환`() {
        // given
        val attrs = mapOf(
            "someKey" to "someValue",
            "anotherKey" to 123
        )

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        assertEquals(attrs, result)
    }

    @Test
    fun `이미 올바른 타입인 OAuth2AuthorizationRequest는 그대로 유지`() {
        // given
        val authzReq = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://example.com/oauth/authorize")
            .clientId("test-client")
            .build()

        val attrs = mapOf(
            OAuth2AuthorizationRequest::class.java.name to authzReq,
            "otherKey" to "value"
        )

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        assertEquals(authzReq, result[OAuth2AuthorizationRequest::class.java.name])
        assertEquals("value", result["otherKey"])
    }

    @Test
    fun `이미 올바른 타입인 Principal은 그대로 유지`() {
        // given
        val account = IoIdpAccount(
            id = "test-account",
            shoplClientId = "CLIENT001",
            shoplUserId = "test-user",
            shoplLoginId = "test@example.com",
            email = "test@example.com",
            name = "Test User",
            status = "ACTIVE",
            isCertEmail = true,
            isTempPwd = false,
            regDt = LocalDateTime.now()
        )
        val userDetails = CustomUserDetails(account)
        val principal: Principal = UsernamePasswordAuthenticationToken(
            userDetails, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )

        val attrs = mapOf(
            Principal::class.java.name to principal,
            "otherKey" to "value"
        )

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        assertEquals(principal, result[Principal::class.java.name])
        assertEquals("value", result["otherKey"])
    }

    @Test
    fun `Map에서 OAuth2AuthorizationRequest 재구성`() {
        // given
        val authzReqMap = mapOf(
            "authorizationUri" to "https://example.com/oauth/authorize",
            "clientId" to "test-client",
            "redirectUri" to "http://localhost:8080/callback",
            "scopes" to listOf("read", "write"),
            "state" to "test-state",
            "additionalParameters" to emptyMap<String, Any>(),
            "attributes" to emptyMap<String, Any>()
        )

        val attrs = mapOf(
            OAuth2AuthorizationRequest::class.java.name to authzReqMap
        )

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        val reconstructedReq = result[OAuth2AuthorizationRequest::class.java.name] as OAuth2AuthorizationRequest
        assertEquals("https://example.com/oauth/authorize", reconstructedReq.authorizationUri)
        assertEquals("test-client", reconstructedReq.clientId)
        assertEquals("http://localhost:8080/callback", reconstructedReq.redirectUri)
        assertEquals(setOf("read", "write"), reconstructedReq.scopes)
        assertEquals("test-state", reconstructedReq.state)
    }

    @Test
    fun `Map에서 기본 인증 Principal 재구성`() {
        // given
        val accountMap = mapOf(
            "id" to "test-account",
            "shoplClientId" to "CLIENT001",
            "shoplUserId" to "test-user",
            "shoplLoginId" to "test@example.com",
            "email" to "test@example.com",
            "phone" to "010-1234-5678",
            "name" to "Test User",
            "status" to "ACTIVE",
            "isCertEmail" to true,
            "isTempPwd" to false,
            "pwd" to "password",
            "beforePwd" to null,
            "pwdUpdateDt" to null,
            "regDt" to LocalDateTime.now().toString(),
            "modDt" to null,
            "delDt" to null
        )

        val principalMap = mapOf(
            "principal" to mapOf("account" to accountMap),
            "authorities" to listOf(
                mapOf("authority" to "ROLE_USER"),
                "ROLE_ADMIN"
            )
        )

        val attrs = mapOf(
            Principal::class.java.name to principalMap
        )

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        val reconstructedPrincipal = result[Principal::class.java.name] as UsernamePasswordAuthenticationToken
        val userDetails = reconstructedPrincipal.principal as CustomUserDetails
        assertEquals("test-account", userDetails.getAccount().id)
        assertEquals("test@example.com", userDetails.getAccount().email)
        assertEquals("Test User", userDetails.getAccount().name)

        val authorities = reconstructedPrincipal.authorities
        assertTrue(authorities.any { it.authority == "ROLE_USER" })
        assertTrue(authorities.any { it.authority == "ROLE_ADMIN" })
    }

    @Test
    fun `Map에서 SSO Principal 재구성`() {
        // given
        val ssoAccount = IoIdpAccount(
            id = "sso_test_user",
            shoplClientId = "CLIENT001",
            shoplUserId = "test_user",
            shoplLoginId = "test@example.com",
            email = "test@example.com",
            name = "SSO User",
            status = "ACTIVE",
            isCertEmail = true,
            isTempPwd = false,
            regDt = LocalDateTime.now()
        )

        val principalMap = mapOf(
            "sub" to "test_user",
            "email" to "test@example.com",
            "name" to "SSO User",
            "authorities" to listOf("ROLE_USER")
        )

        val attrs = mapOf(
            Principal::class.java.name to principalMap
        )

        whenever(ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository))
            .thenReturn(ssoAccount)

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        val reconstructedPrincipal = result[Principal::class.java.name] as UsernamePasswordAuthenticationToken
        val userDetails = reconstructedPrincipal.principal as CustomUserDetails
        assertEquals("sso_test_user", userDetails.getAccount().id)
        assertEquals("test@example.com", userDetails.getAccount().email)
        assertEquals("SSO User", userDetails.getAccount().name)
    }

    @Test
    fun `잘못된 타입의 OAuth2AuthorizationRequest는 무시`() {
        // given
        val attrs = mapOf(
            OAuth2AuthorizationRequest::class.java.name to "invalid_type"
        )

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        assertEquals(attrs, result)
    }

    @Test
    fun `잘못된 타입의 Principal은 무시`() {
        // given
        val attrs = mapOf(
            Principal::class.java.name to 12345
        )

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        assertEquals(attrs, result)
    }

    @Test
    fun `Principal 재구성 실패 시 원본 유지`() {
        // given
        val principalMap = mapOf(
            "invalid_structure" to "value"
        )

        val attrs = mapOf(
            Principal::class.java.name to principalMap
        )

        whenever(ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository))
            .thenThrow(RuntimeException("Reconstruction failed"))

        // when
        val result = coercer.coerceAttributes(attrs, ioIdpAccountRepository)

        // then
        assertEquals(attrs, result)
    }
}