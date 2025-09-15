package me.practice.oauth2.infrastructure.redis.converter

import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.infrastructure.redis.dto.RedisAuthorizationDTO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class OAuth2AuthorizationConverterTest {

    private lateinit var oauth2AttributeCoercer: OAuth2AttributeCoercer
    private lateinit var registeredClientRepository: RegisteredClientRepository
    private lateinit var ioIdpAccountRepository: IoIdpAccountRepository
    private lateinit var converter: OAuth2AuthorizationConverter

    @BeforeEach
    fun setUp() {
        oauth2AttributeCoercer = mock()
        registeredClientRepository = mock()
        ioIdpAccountRepository = mock()
        converter = OAuth2AuthorizationConverter(oauth2AttributeCoercer)
    }

    @Test
    fun `OAuth2Authorization을 DTO로 변환`() {
        // given
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val registeredClient = RegisteredClient.withId("client-id")
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:8080/callback")
            .scope("read")
            .build()

        val authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
            .id("auth-id")
            .principalName("test-user")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .attribute("state", "test-state")
            .token(OAuth2AuthorizationCode("auth-code", now, now.plusSeconds(600)))
            .accessToken(OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(3600), setOf("read")))
            .refreshToken(OAuth2RefreshToken("refresh-token", now, now.plusSeconds(7200)))
            .build()

        // when
        val dto = converter.toDTO(authorization)

        // then
        assertEquals("auth-id", dto.id)
        assertEquals(authorization.registeredClientId, dto.registeredClientId) // "client-id"
        assertEquals("test-user", dto.principalName)
        assertEquals(authorization.authorizationGrantType.value, dto.authorizationGrantType)
        assertEquals("test-state", dto.state)

        assertNotNull(dto.authorizationCode)
        assertEquals("auth-code", dto.authorizationCode!!.tokenValue)
        assertEquals(now, dto.authorizationCode!!.issuedAt)

        assertNotNull(dto.accessToken)
        assertEquals("access-token", dto.accessToken!!.tokenValue)
        assertEquals("Bearer", dto.accessToken!!.tokenType)
        assertEquals(setOf("read"), dto.accessToken!!.scopes)

        assertNotNull(dto.refreshToken)
        assertEquals("refresh-token", dto.refreshToken!!.tokenValue)
    }

    @Test
    fun `DTO를 OAuth2Authorization으로 변환`() {
        // given
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val registeredClient = RegisteredClient.withId("client-id")
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:8080/callback")
            .scope("read")
            .build()

        val dto = RedisAuthorizationDTO(
            id = "auth-id",
            registeredClientId = "client-id",
            principalName = "test-user",
            authorizationGrantType = "authorization_code",
            attributes = mapOf("state" to "test-state"),
            state = "test-state"
        )

        val coercedAttributes = mapOf("state" to "test-state")

        whenever(registeredClientRepository.findById("client-id"))
            .thenReturn(registeredClient)
        whenever(oauth2AttributeCoercer.coerceAttributes(dto.attributes, ioIdpAccountRepository))
            .thenReturn(coercedAttributes)

        // when
        val authorization = converter.fromDTO(dto, registeredClientRepository, ioIdpAccountRepository)

        // then
        assertEquals("auth-id", authorization.id)
        assertEquals("client-id", authorization.registeredClientId)
        assertEquals("test-user", authorization.principalName)
        assertEquals(AuthorizationGrantType.AUTHORIZATION_CODE, authorization.authorizationGrantType)
        assertEquals("test-state", authorization.getAttribute<String>("state"))
    }

    @Test
    fun `등록된 클라이언트를 찾을 수 없는 경우 예외 발생`() {
        // given
        val dto = RedisAuthorizationDTO(
            id = "auth-id",
            registeredClientId = "unknown-client",
            principalName = "test-user",
            authorizationGrantType = "authorization_code"
        )

        whenever(registeredClientRepository.findById("unknown-client"))
            .thenReturn(null)

        // when & then
        assertThrows(IllegalArgumentException::class.java) {
            converter.fromDTO(dto, registeredClientRepository, ioIdpAccountRepository)
        }
    }

    @Test
    fun `최소한의 정보로 변환`() {
        // given
        val registeredClient = RegisteredClient.withId("client-id")
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:8080/callback")
            .scope("read")
            .build()

        val authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
            .id("minimal-auth-id")
            .principalName("minimal-user")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build()

        // when
        val dto = converter.toDTO(authorization)

        // then
        assertEquals("minimal-auth-id", dto.id)
        assertEquals(authorization.registeredClientId, dto.registeredClientId) // "client-id"
        assertEquals("minimal-user", dto.principalName)
        assertEquals(authorization.authorizationGrantType.value, dto.authorizationGrantType)
        assertNull(dto.state)
        assertNull(dto.authorizationCode)
        assertNull(dto.accessToken)
        assertNull(dto.refreshToken)
        assertNull(dto.oidcIdToken)
    }
}