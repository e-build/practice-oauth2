package me.practice.oauth2.configuration

import me.practice.oauth2.entity.IoIdpClientRepository
import me.practice.oauth2.service.DatabaseRegisteredClientRepository
import me.practice.oauth2.service.ShoplClientMappingService
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration
import java.util.*

/**
 * 초기 OAuth 클라이언트 데이터를 설정하는 Configuration
 * Shopl Client별로 다양한 OAuth Client를 생성
 */
@Configuration
class ClientDataInitializer {

    @Bean
    fun initializeClients(
        clientRepository: IoIdpClientRepository,
        registeredClientRepository: DatabaseRegisteredClientRepository,
        mappingService: ShoplClientMappingService
    ) = ApplicationRunner {
        
        initializeShoplClient001Clients(registeredClientRepository, mappingService)
        initializeShoplClient002Clients(registeredClientRepository, mappingService)
        
        println("✅ 초기 OAuth2 클라이언트 설정이 완료되었습니다.")
        printMappingSummary(mappingService)
    }

    /**
     * CLIENT001 (삼성)의 OAuth 클라이언트들 초기화
     */
    private fun initializeShoplClient001Clients(
        registeredClientRepository: DatabaseRegisteredClientRepository,
        mappingService: ShoplClientMappingService
    ) {
        val shoplClientId = "CLIENT001"

        // 1. 웹 애플리케이션 클라이언트
        val webClientId = "client001-web"
        if (registeredClientRepository.findByClientId(webClientId) == null) {
            val webClient = createRegisteredClient(
                clientId = webClientId,
                clientName = "CLIENT001 Web Application",
                redirectUris = listOf(
                    "http://localhost:9001/dashboard",
                    "http://localhost:9001/callback"
                ),
                scopes = listOf("openid", "profile", "read", "write")
            )
            registeredClientRepository.save(webClient)
            mappingService.createMapping(shoplClientId, webClient.id)
            println("✨ 생성됨: $webClientId")
        } else {
            println("⚠️ 이미 존재함: $webClientId")
        }

        // 2. 모바일 앱 클라이언트  
        val mobileClientId = "client001-mobile"
        if (registeredClientRepository.findByClientId(mobileClientId) == null) {
            val mobileClient = createRegisteredClient(
                clientId = mobileClientId,
                clientName = "CLIENT001 Mobile App",
                redirectUris = listOf(
                    "client001mobile://callback",
                    "http://localhost:9001/mobile/callback"
                ),
                scopes = listOf("openid", "profile", "read"),
                accessTokenTtl = Duration.ofHours(1) // 모바일은 더 긴 TTL
            )
            registeredClientRepository.save(mobileClient)
            mappingService.createMapping(shoplClientId, mobileClient.id)
            println("✨ 생성됨: $mobileClientId")
        } else {
            println("⚠️ 이미 존재함: $mobileClientId")
        }

        // 3. API 전용 클라이언트 (Client Credentials Grant)
        val apiClientId = "client001-api"
        if (registeredClientRepository.findByClientId(apiClientId) == null) {
            val apiClient = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId(apiClientId)
                .clientName("CLIENT001 API Client")
                .clientSecret(NoOpPasswordEncoder.getInstance().encode("{noop}api-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api:read")
                .scope("api:write")
                .clientSettings(
                    ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build()
                )
                .tokenSettings(
                    TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(2))
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .build()
                )
                .build()
            registeredClientRepository.save(apiClient)
            mappingService.createMapping(shoplClientId, apiClient.id)
            println("✨ 생성됨: $apiClientId")
        } else {
            println("⚠️ 이미 존재함: $apiClientId")
        }
    }

    /**
     * CLIENT002 (LG)의 OAuth 클라이언트들 초기화
     */
    private fun initializeShoplClient002Clients(
        registeredClientRepository: DatabaseRegisteredClientRepository,
        mappingService: ShoplClientMappingService
    ) {
        val shoplClientId = "CLIENT002"

        // 1. 통합 웹/모바일 클라이언트
        val unifiedClientId = "client002-unified"
        if (registeredClientRepository.findByClientId(unifiedClientId) == null) {
            val unifiedClient = createRegisteredClient(
                clientId = unifiedClientId,
                clientName = "CLIENT002 Unified Application",
                redirectUris = listOf(
                    "http://localhost:9002/dashboard",
                    "http://localhost:9002/callback",
                    "client002app://callback"
                ),
                scopes = listOf("openid", "profile", "read", "write", "admin")
            )
            registeredClientRepository.save(unifiedClient)
            mappingService.createMapping(shoplClientId, unifiedClient.id)
            println("✨ 생성됨: $unifiedClientId")
        } else {
            println("⚠️ 이미 존재함: $unifiedClientId")
        }

        // 2. 제3자 통합 클라이언트
        val thirdPartyClientId = "client002-integration"
        if (registeredClientRepository.findByClientId(thirdPartyClientId) == null) {
            val thirdPartyClient = createRegisteredClient(
                clientId = thirdPartyClientId,
                clientName = "CLIENT002 Third Party Integration",
                redirectUris = listOf(
                    "https://client002-partner.com/callback",
                    "http://localhost:9002/integration/callback"
                ),
                scopes = listOf("integration:read", "integration:write"),
                requireConsent = true // 제3자는 동의 화면 필요
            )
            registeredClientRepository.save(thirdPartyClient)
            mappingService.createMapping(shoplClientId, thirdPartyClient.id)
            println("✨ 생성됨: $thirdPartyClientId")
        } else {
            println("⚠️ 이미 존재함: $thirdPartyClientId")
        }
    }

    /**
     * 공통 RegisteredClient 생성 헬퍼 메서드
     */
    private fun createRegisteredClient(
        clientId: String,
        clientName: String,
        redirectUris: List<String>,
        scopes: List<String>,
        accessTokenTtl: Duration = Duration.ofMinutes(5),
        refreshTokenTtl: Duration = Duration.ofMinutes(60),
        requireConsent: Boolean = false
    ): RegisteredClient {
        val builder = RegisteredClient
            .withId(UUID.randomUUID().toString())
            .clientId(clientId)
            .clientName(clientName)
            .clientSecret(NoOpPasswordEncoder.getInstance().encode("{noop}secret"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)

        redirectUris.forEach { builder.redirectUri(it) }
        scopes.forEach { builder.scope(it) }

        builder
            .clientSettings(
                ClientSettings.builder()
                    .requireAuthorizationConsent(requireConsent)
                    .requireProofKey(false)
                    .build()
            )
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(accessTokenTtl)
                    .refreshTokenTimeToLive(refreshTokenTtl)
                    .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                    .reuseRefreshTokens(true)
                    .build()
            )

        return builder.build()
    }

    /**
     * 매핑 결과 출력
     */
    private fun printMappingSummary(mappingService: ShoplClientMappingService) {
        val mappings = mappingService.getAllMappings()
        println("=== Shopl Client ↔ OAuth Client 매핑 현황 ===")
        mappings.forEach { (shoplClient, oauthClients) ->
            println("📋 $shoplClient:")
            oauthClients.forEach { oauthClient ->
                println("   └─ $oauthClient")
            }
        }
        println("============================================")
    }
}