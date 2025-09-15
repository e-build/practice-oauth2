package me.practice.oauth2.infrastructure.redis.converter

import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.infrastructure.redis.dto.AccessTokenDTO
import me.practice.oauth2.infrastructure.redis.dto.RedisAuthorizationDTO
import me.practice.oauth2.infrastructure.redis.dto.TokenDTO
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Component

/**
 * OAuth2Authorization과 RedisAuthorizationDTO 간 변환 담당
 * 단일 책임: 객체 변환
 */
@Component
class OAuth2AuthorizationConverter(
    private val oauth2AttributeCoercer: OAuth2AttributeCoercer
) {

    private val logger = LoggerFactory.getLogger(OAuth2AuthorizationConverter::class.java)

    /**
     * OAuth2Authorization을 RedisAuthorizationDTO로 변환
     */
    fun toDTO(auth: OAuth2Authorization): RedisAuthorizationDTO {
        val state = auth.getAttribute<String>("state")
        val code = auth.getToken(OAuth2AuthorizationCode::class.java)
        val access = auth.accessToken
        val refresh = auth.refreshToken
        val idToken = auth.getToken(OidcIdToken::class.java)

        return RedisAuthorizationDTO(
            id = auth.id,
            registeredClientId = auth.registeredClientId,
            principalName = auth.principalName,
            authorizationGrantType = auth.authorizationGrantType.value,
            attributes = auth.attributes.toMap(),  // 방어적 복사

            state = state,

            authorizationCode = code?.let { toTokenDTO(it) },
            accessToken = access?.let { toAccessDTO(it) },
            refreshToken = refresh?.let { toTokenDTO(it) },
            oidcIdToken = idToken?.let { toTokenDTO(it) },
        )
    }

    /**
     * RedisAuthorizationDTO를 OAuth2Authorization으로 변환
     */
    fun fromDTO(
        dto: RedisAuthorizationDTO,
        registeredClientRepository: RegisteredClientRepository,
        ioIdpAccountRepository: IoIdpAccountRepository,
    ): OAuth2Authorization {
        val rc = requireNotNull(registeredClientRepository.findById(dto.registeredClientId)) {
            "RegisteredClient not found: ${dto.registeredClientId}"
        }

        val coercedAttrs = oauth2AttributeCoercer.coerceAttributes(
            attrs = dto.attributes,
            ioIdpAccountRepository = ioIdpAccountRepository
        )

        val builder = OAuth2Authorization.withRegisteredClient(rc)
            .id(dto.id)
            .principalName(dto.principalName)
            .authorizationGrantType(AuthorizationGrantType(dto.authorizationGrantType))
            .attributes { it.putAll(coercedAttrs) }

        dto.authorizationCode?.let {
            builder.token(OAuth2AuthorizationCode(it.tokenValue, it.issuedAt, it.expiresAt)) { meta ->
                meta.putAll(it.metadata)
            }
        }

        dto.accessToken?.let {
            val type = if (it.tokenType.equals("Bearer", ignoreCase = true))
                OAuth2AccessToken.TokenType.BEARER else null
            builder.token(
                OAuth2AccessToken(type, it.tokenValue, it.issuedAt, it.expiresAt, it.scopes)
            ) { meta -> meta.putAll(it.metadata) }
        }

        dto.refreshToken?.let {
            builder.token(OAuth2RefreshToken(it.tokenValue, it.issuedAt, it.expiresAt)) { meta ->
                meta.putAll(it.metadata)
            }
        }

        dto.oidcIdToken?.let {
            val claims: Map<String, Any> =
                it.metadata["claims"] as? Map<String, Any>
                    ?: it.metadata["claimsSet"] as? Map<String, Any> // 혹시 다른 키로 저장된 경우
                    ?: emptyMap()
            builder.token(OidcIdToken(it.tokenValue, it.issuedAt, it.expiresAt, claims)) { meta ->
                meta.putAll(it.metadata)
            }
        }

        return builder.build()
    }

    /**
     * OAuth2Authorization.Token을 TokenDTO로 변환
     */
    private fun toTokenDTO(token: OAuth2Authorization.Token<out OAuth2Token>): TokenDTO {
        val raw = token.token
        val abs = raw as? AbstractOAuth2Token
        return TokenDTO(
            tokenValue = raw.tokenValue,
            issuedAt = abs?.issuedAt,
            expiresAt = abs?.expiresAt,
            metadata = token.metadata.toMap()
        )
    }

    /**
     * OAuth2AccessToken을 AccessTokenDTO로 변환
     */
    private fun toAccessDTO(token: OAuth2Authorization.Token<OAuth2AccessToken>): AccessTokenDTO {
        val raw = token.token
        return AccessTokenDTO(
            tokenValue = raw.tokenValue,
            issuedAt = raw.issuedAt,
            expiresAt = raw.expiresAt,
            tokenType = raw.tokenType?.value ?: "Bearer",
            scopes = raw.scopes,
            metadata = token.metadata.toMap()
        )
    }
}