package me.practice.oauth2.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.practice.oauth2.entity.*
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 사용자 프로비저닝 서비스
 * SSO를 통한 사용자 계정 생성 및 연결 관리
 */
@Service
class UserProvisioningService(
    private val accountRepository: IoIdpAccountRepository,
    private val oauthLinkRepository: IoIdpAccountOauthLinkRepository,
    private val ssoConfigurationService: SsoConfigurationService,
    private val objectMapper: ObjectMapper,
) {

    /**
     * OAuth2 사용자로부터 계정 프로비저닝
     */
    @Transactional
    fun provisionUser(
        oauth2User: OAuth2User,
        shoplClientId: String,
        providerType: ProviderType,
        registrationId: String
    ): IoIdpAccount {
        val providerUserId = extractProviderUserId(oauth2User, providerType)
        val email = extractEmail(oauth2User)
        val name = extractName(oauth2User)

        // 기존 OAuth 연결 확인
        val existingLink = oauthLinkRepository.findByShoplClientIdAndProviderTypeAndProviderUserId(
            shoplClientId, providerType, providerUserId
        )

        if (existingLink != null) {
            // 기존 연결이 있으면 해당 계정 반환
            val account = accountRepository.findById(existingLink.accountId)
                .orElseThrow { IllegalStateException("Account not found for existing OAuth link") }
            
            // OAuth 링크 정보 업데이트
            updateOAuthLink(existingLink, oauth2User, email, name)
            
            return account
        }

        // 이메일로 기존 계정 검색
        val existingAccount = if (email != null) {
            accountRepository.findByShoplClientIdAndEmail(shoplClientId, email)
        } else null

        val account = if (existingAccount != null) {
            // 기존 계정이 있으면 OAuth 연결 추가
            createOAuthLink(existingAccount, oauth2User, shoplClientId, providerType, providerUserId, email, name)
            existingAccount
        } else {
            // 새 계정 생성
            val ssoSettings = ssoConfigurationService.getSsoSettings(shoplClientId)
            if (ssoSettings?.autoProvision != true) {
                throw IllegalStateException("Auto-provisioning is disabled for client: $shoplClientId")
            }
            
            val newAccount = createNewAccount(shoplClientId, email, name, ssoSettings)
            createOAuthLink(newAccount, oauth2User, shoplClientId, providerType, providerUserId, email, name)
            newAccount
        }

        return account
    }

    /**
     * OIDC 사용자로부터 계정 프로비저닝
     */
    @Transactional
    fun provisionOidcUser(
        oidcUser: OidcUser,
        shoplClientId: String,
        registrationId: String
    ): IoIdpAccount {
        return provisionUser(oidcUser, shoplClientId, ProviderType.OIDC, registrationId)
    }

    /**
     * 새 계정 생성
     */
    private fun createNewAccount(
        shoplClientId: String,
        email: String?,
        name: String?,
        ssoSettings: IoIdpShoplClientSsoSetting
    ): IoIdpAccount {
        val accountId = generateAccountId()
        val now = LocalDateTime.now()

        val account = IoIdpAccount(
            id = accountId,
            shoplClientId = shoplClientId,
            shoplUserId = generateKey(),
            shoplLoginId = generateKey(),
            email = email,
            phone = null,
            name = name,
            pwd = null,
            isTempPwd = false,
            pwdUpdateDt = null,
            status = "ACTIVE",
            isCertEmail = email != null,
            regDt = now,
            modDt = now,
            delDt = null
        )

        return accountRepository.save(account)
    }

    /**
     * OAuth 연결 생성
     */
    private fun createOAuthLink(
        account: IoIdpAccount,
        oauth2User: OAuth2User,
        shoplClientId: String,
        providerType: ProviderType,
        providerUserId: String,
        email: String?,
        name: String?
    ): IoIdpAccountOauthLink {
        val now = LocalDateTime.now()
        val rawClaims = serializeOAuth2Attributes(oauth2User.attributes)

        val oauthLink = IoIdpAccountOauthLink(
            id = generateKey(),
            accountId = account.id,
            shoplClientId = shoplClientId,
            providerType = providerType,
            providerUserId = providerUserId,
            emailAtProvider = email,
            nameAtProvider = name,
            rawClaims = rawClaims,
            regDt = now,
            modDt = now
        )

        return oauthLinkRepository.save(oauthLink)
    }

    /**
     * 기존 OAuth 연결 업데이트
     */
    private fun updateOAuthLink(
        oauthLink: IoIdpAccountOauthLink,
        oauth2User: OAuth2User,
        email: String?,
        name: String?
    ) {
        val rawClaims = serializeOAuth2Attributes(oauth2User.attributes)
        
        val updatedLink = oauthLink.copy(
            emailAtProvider = email,
            nameAtProvider = name,
            rawClaims = rawClaims,
            modDt = LocalDateTime.now()
        )
        
        oauthLinkRepository.save(updatedLink)
    }

    /**
     * 제공자별 사용자 ID 추출
     */
    private fun extractProviderUserId(oauth2User: OAuth2User, providerType: ProviderType): String {
        return when (providerType) {
            ProviderType.GOOGLE -> oauth2User.getAttribute<String>("sub")
                ?: oauth2User.getAttribute<String>("id")
                ?: throw IllegalArgumentException("Google user ID not found")
            ProviderType.KAKAO -> oauth2User.getAttribute<Any>("id")?.toString()
                ?: throw IllegalArgumentException("Kakao user ID not found")
            ProviderType.NAVER -> {
                val response = oauth2User.getAttribute<Map<String, Any>>("response")
                response?.get("id")?.toString()
                    ?: throw IllegalArgumentException("Naver user ID not found")
            }
            ProviderType.APPLE -> oauth2User.getAttribute<String>("sub")
                ?: throw IllegalArgumentException("Apple user ID not found")
            ProviderType.MICROSOFT -> oauth2User.getAttribute<String>("oid")
                ?: oauth2User.getAttribute<String>("id")
                ?: throw IllegalArgumentException("Microsoft user ID not found")
            ProviderType.GITHUB -> oauth2User.getAttribute<Any>("id")?.toString()
                ?: throw IllegalArgumentException("GitHub user ID not found")
            ProviderType.OIDC -> oauth2User.getAttribute<String>("sub")
                ?: throw IllegalArgumentException("OIDC user ID not found")
            ProviderType.SAML -> oauth2User.getAttribute<String>("nameID")
                ?: oauth2User.getAttribute<String>("subject")
                ?: throw IllegalArgumentException("SAML user ID not found")
            ProviderType.OAUTH2 -> oauth2User.getAttribute<String>("id")
                ?: oauth2User.getAttribute<String>("sub")
                ?: throw IllegalArgumentException("OAuth2 user ID not found")
            ProviderType.BASIC -> throw IllegalArgumentException("BASIC provider does not support OAuth2 user extraction")
        }
    }

    /**
     * 이메일 추출
     */
    private fun extractEmail(oauth2User: OAuth2User): String? {
        return oauth2User.getAttribute<String>("email")
            ?: oauth2User.getAttribute<String>("mail")
            ?: oauth2User.getAttribute<Map<String, Any>>("response")?.get("email")?.toString()
    }

    /**
     * 이름 추출
     */
    private fun extractName(oauth2User: OAuth2User): String? {
        return oauth2User.getAttribute<String>("name")
            ?: oauth2User.getAttribute<String>("given_name")
            ?: oauth2User.getAttribute<String>("nickname")
            ?: oauth2User.getAttribute<Map<String, Any>>("response")?.get("name")?.toString()
    }

    /**
     * OAuth2 속성을 JSON으로 직렬화
     */
    private fun serializeOAuth2Attributes(attributes: Map<String, Any>): String {
        return try {
            objectMapper.writeValueAsString(attributes)
        } catch (e: Exception) {
            "{}"
        }
    }

    /**
     * 계정 ID 생성
     */
    private fun generateAccountId(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20)
    }

    /**
     * Shopl 사용자 ID 생성
     */
    private fun generateKey(): String {
        return UUID.randomUUID().toString().substring(0, 20)
    }

    /**
     * OAuth 링크 ID 생성
     */
    private fun generateOAuthLinkId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * 계정과 연결된 모든 OAuth 링크 조회
     */
    @Transactional(readOnly = true)
    fun getOAuthLinks(accountId: String): List<IoIdpAccountOauthLink> {
        return oauthLinkRepository.findByAccountId(accountId)
    }

    /**
     * OAuth 링크 해제
     */
    @Transactional
    fun unlinkOAuthProvider(accountId: String, providerType: ProviderType) {
        val links = oauthLinkRepository.findByAccountId(accountId)
        links.filter { it.providerType == providerType }
            .forEach { oauthLinkRepository.delete(it) }
    }
}