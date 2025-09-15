package me.practice.oauth2.infrastructure.redis.converter

import me.practice.oauth2.configuration.CustomUserDetails
import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.infrastructure.redis.reconstructor.SsoAccountReconstructor
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component
import java.security.Principal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * OAuth2 속성 복원 담당 클래스
 * 단일 책임: 직렬화된 속성들을 올바른 타입으로 복원
 */
@Component
class OAuth2AttributeCoercer(
    private val ssoAccountReconstructor: SsoAccountReconstructor
) {

    private val logger = LoggerFactory.getLogger(OAuth2AttributeCoercer::class.java)

    companion object {
        private val AUTHZ_REQ = OAuth2AuthorizationRequest::class.java.name
        private val PRINCIPAL = Principal::class.java.name
    }

    /**
     * 직렬화된 속성들을 올바른 타입으로 복원
     */
    fun coerceAttributes(
        attrs: Map<String, Any?>,
        ioIdpAccountRepository: IoIdpAccountRepository
    ): Map<String, Any?> {
        logger.trace("Coercing attributes with keys: ${attrs.keys}")

        val authzReq = coerceAuthorizationRequest(attrs[AUTHZ_REQ])
        val principal = coercePrincipal(attrs[PRINCIPAL], ioIdpAccountRepository)

        if (authzReq == null && principal == null) {
            logger.trace("No attributes to coerce")
            return attrs
        }

        return attrs.toMutableMap().apply {
            if (authzReq != null) {
                put(AUTHZ_REQ, authzReq)
                logger.trace("Coerced authorization request")
            }
            if (principal != null) {
                put(PRINCIPAL, principal)
                logger.debug("Coerced principal to ${principal.javaClass.simpleName}")
            }
        }
    }

    /**
     * OAuth2AuthorizationRequest 복원
     */
    private fun coerceAuthorizationRequest(authzReqRaw: Any?): OAuth2AuthorizationRequest? {
        return when (authzReqRaw) {
            null -> {
                logger.trace("No authorization request found in attributes")
                null
            }
            is OAuth2AuthorizationRequest -> {
                logger.trace("Authorization request already in correct type")
                authzReqRaw
            }
            is Map<*, *> -> {
                runCatching {
                    rebuildAuthorizationRequest(authzReqRaw)
                }.onFailure { e ->
                    logger.warn("Failed to rebuild authorization request: ${e.message}")
                }.getOrNull()
            }
            else -> {
                logger.warn("Unknown authorization request type: ${authzReqRaw?.javaClass}")
                null
            }
        }
    }

    /**
     * Principal 복원
     */
    private fun coercePrincipal(
        principalRaw: Any?,
        ioIdpAccountRepository: IoIdpAccountRepository
    ): Principal? {
        return when (principalRaw) {
            null -> {
                logger.trace("No principal found in attributes")
                null
            }
            is Principal -> {
                logger.trace("Principal already in correct type: ${principalRaw.javaClass.simpleName}")
                principalRaw
            }
            is Map<*, *> -> {
                runCatching {
                    rebuildPrincipal(principalRaw, ioIdpAccountRepository)
                }.onFailure { e ->
                    logger.error("Failed to rebuild principal from map: ${e.message}", e)
                }.getOrNull()
            }
            else -> {
                logger.warn("Unknown principal type: ${principalRaw?.javaClass}")
                null
            }
        }
    }

    /**
     * Principal 재구성
     */
    private fun rebuildPrincipal(
        src: Map<*, *>,
        ioIdpAccountRepository: IoIdpAccountRepository
    ): Principal {
        val principalData = src["principal"] ?: src
        val accountMap = when (principalData) {
            is Map<*, *> -> principalData["account"] as? Map<*, *>
            else -> null
        }

        return if (accountMap != null) {
            rebuildBasicPrincipal(src, accountMap)
        } else {
            rebuildSsoPrincipal(src, principalData, ioIdpAccountRepository)
        }
    }

    /**
     * 기본 인증 Principal 재구성
     */
    private fun rebuildBasicPrincipal(src: Map<*, *>, accountMap: Map<*, *>): Principal {
        val authorities = extractAuthorities(src)
        val account = createBasicAccount(accountMap)
        val userDetails = CustomUserDetails(account)

        return UsernamePasswordAuthenticationToken(userDetails, null, authorities).apply {
            src["details"]?.let { this.details = it }
        }
    }

    /**
     * SSO Principal 재구성
     */
    private fun rebuildSsoPrincipal(
        src: Map<*, *>,
        principalData: Any?,
        ioIdpAccountRepository: IoIdpAccountRepository
    ): Principal {
        val authorities = extractAuthorities(src)
        val principalMap = principalData as? Map<*, *>
            ?: error("SSO principal data is not a map")

        logger.debug("Rebuilding SSO principal from data: ${principalMap.keys}")

        // SSO 계정 재구성을 전용 클래스에 위임
        val account = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)
        val userDetails = CustomUserDetails(account)

        return UsernamePasswordAuthenticationToken(userDetails, null, authorities).apply {
            src["details"]?.let { this.details = it }
        }
    }

    /**
     * 권한 정보 추출
     */
    private fun extractAuthorities(src: Map<*, *>): List<SimpleGrantedAuthority> {
        val authoritiesSrc: Collection<*>? = src["authorities"] as? Collection<*>
        return authoritiesSrc
            ?.mapNotNull { auth ->
                when (auth) {
                    is String -> auth
                    is Map<*, *> -> auth["authority"] as? String
                    else -> null
                }
            }
            ?.map(::SimpleGrantedAuthority)
            ?: emptyList()
    }

    /**
     * 기본 계정 생성
     */
    private fun createBasicAccount(accountMap: Map<*, *>): IoIdpAccount {
        return IoIdpAccount(
            id = accountMap["id"] as String,
            shoplClientId = accountMap["shoplClientId"] as String,
            shoplUserId = accountMap["shoplUserId"] as String,
            shoplLoginId = accountMap["shoplLoginId"] as String,
            email = accountMap["email"] as String,
            phone = accountMap["phone"] as String,
            name = accountMap["name"] as String,
            status = accountMap["status"] as String,
            isCertEmail = accountMap["isCertEmail"] as Boolean,
            isTempPwd = accountMap["isTempPwd"] as Boolean,
            pwd = accountMap["pwd"] as String,
            beforePwd = accountMap["beforePwd"] as? String,
            pwdUpdateDt = (accountMap["pwdUpdateDt"] as? String)?.toLdtOrNull(),
            regDt = (accountMap["regDt"] as String).toLdtOrNull()
                ?: error("regDt missing or invalid"),
            modDt = (accountMap["modDt"] as? String)?.toLdtOrNull(),
            delDt = (accountMap["delDt"] as? String)?.toLdtOrNull(),
        )
    }

    /**
     * OAuth2AuthorizationRequest 재구성
     */
    private fun rebuildAuthorizationRequest(src: Map<*, *>): OAuth2AuthorizationRequest {
        val authorizationUri = src["authorizationUri"] as? String
            ?: error("authorizationUri missing")
        val clientId = src["clientId"] as? String
            ?: error("clientId missing")
        val redirectUri = src["redirectUri"] as? String
        val scopes: Set<String> =
            (src["scopes"] as? Collection<*>)?.filterIsInstance<String>()?.toSet()
                ?: emptySet()
        val state = src["state"] as? String
        val additional = (src["additionalParameters"] as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
        val attrs = (src["attributes"] as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

        return OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(authorizationUri)
            .clientId(clientId)
            .redirectUri(redirectUri)
            .scopes(scopes)
            .state(state)
            .additionalParameters(additional)
            .attributes { it.putAll(attrs) }
            .build()
    }

    /**
     * 문자열을 LocalDateTime으로 변환
     */
    private fun String?.toLdtOrNull(): LocalDateTime? =
        this?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
}