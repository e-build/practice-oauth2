package me.practice.oauth2.configuration

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.*

@Configuration
@EnableWebSecurity
class AuthSecurityConfiguration(
	private val oAuth2AuthorizationServerProperties: OAuth2AuthorizationServerProperties,
	private val customAuthenticationProvider: CustomAuthenticationProvider,
	private val customUserDetailsService: CustomUserDetailsService,
) {

	/**
	 * 1. OAuth2 Authorization Server를 위한 SecurityFilterChain
	 * - JWT 토큰 발급 및 검증을 담당
	 * - Authorization Code Grant 플로우 처리
	 * - /oauth2/authorize, /oauth2/token 등의 OAuth2 엔드포인트 보호
	 */
	@Bean
	@Order(1)
	@Throws(Exception::class)
	fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
		OAuth2AuthorizationServerConfigurer.authorizationServer().let { authorizationServerConfigurer ->
			http.securityMatcher(authorizationServerConfigurer.endpointsMatcher).cors(Customizer.withDefaults())
				.authorizeHttpRequests {
					it.anyRequest().authenticated()
				}.csrf { csrf ->
					// OAuth2 엔드포인트는 CSRF 보호에서 제외 (토큰 기반 인증이므로)
					csrf.ignoringRequestMatchers(authorizationServerConfigurer.endpointsMatcher)
				}.with(authorizationServerConfigurer) {
					it.authorizationServerSettings(authorizationServerSettings())
						.registeredClientRepository(registeredClientRepository())

						// OIDC 지원 (OpenID Connect) - JWT ID 토큰 발급을 위해 필요
						.oidc(Customizer.withDefaults())
				}
		}

		// 사용자 인증을 위한 로그인 폼 설정
		http.formLogin(Customizer.withDefaults())

		return http.build()
	}

	/**
	 * 2. 일반적인 애플리케이션 인증을 위한 SecurityFilterChain
	 * - Authorization Server 자체의 관리 페이지나 기타 엔드포인트 보호
	 * - OAuth2 엔드포인트가 아닌 일반 웹 요청 처리
	 */
	@Bean
	@Order(2)
	fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http.authenticationProvider(customAuthenticationProvider).cors(Customizer.withDefaults())
			.authorizeHttpRequests {
				it.requestMatchers("/favicon.ico").permitAll().requestMatchers("/error").permitAll()
					.requestMatchers("/.well-known/**").permitAll().requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
					.permitAll().requestMatchers("/login").permitAll()
					.requestMatchers("/static/**", "/css/**", "/js/**", "/images/**").permitAll()
					.requestMatchers("/").permitAll() // 루트 경로 허용 추가
					.anyRequest().authenticated()
			}.formLogin { form ->
				form.loginPage("/login") // 커스텀 로그인 페이지 경로
					.defaultSuccessUrl("http://localhost:9001/dashboard", true) // 로그인 성공 시 리다이렉트 URL 명시적 설정
					.permitAll()
			}.logout {
				it.logoutUrl("/logout").logoutSuccessUrl("http://localhost:9001/dashboard").invalidateHttpSession(true)
					.clearAuthentication(true).deleteCookies("JSESSIONID")
			}.requestCache { cache ->
				cache.requestCache(HttpSessionRequestCache())
			}
			// 개발 편의상 CSRF 비활성화 (프로덕션에서는 활성화 권장)
			.csrf { it.disable() }

		return http.build()
	}

	/**
	 * Authorization Server 기본 설정
	 * - JWT 토큰의 issuer 설정 (토큰 검증 시 사용)
	 */
	@Bean
	fun authorizationServerSettings(): AuthorizationServerSettings {
		return AuthorizationServerSettings.builder().issuer(oAuth2AuthorizationServerProperties.issuer).build()
	}

	/**
	 * OAuth2 클라이언트 등록 정보 관리
	 * - 클라이언트 ID, Secret, 허용된 Grant Type, Scope 등 정의
	 * - Authorization Code Grant 플로우 사용
	 */
	@Bean
	fun registeredClientRepository(): RegisteredClientRepository {
		val registeredClient = RegisteredClient.withId(UUID.randomUUID().toString()).clientId("oauth2-client")
			.clientSecret(NoOpPasswordEncoder.getInstance().encode("{noop}secret")) // 개발용 - 프로덕션에서는 BCrypt 사용
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			// Authorization Code Grant 플로우만 허용
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			// 리프레시 토큰을 통한 토큰 갱신 허용
			.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
			// 인증 완료 후 리다이렉트될 URI (클라이언트 앱의 콜백 URL)
			.redirectUri("http://localhost:9001/dashboard")
			// 지원할 scope 정의
			.scope(OidcScopes.OPENID) // OpenID Connect 지원
			.scope(OidcScopes.PROFILE) // 프로필 정보 접근
			.scope("read") // 읽기 권한
			.scope("write") // 쓰기 권한
			.clientSettings(
				ClientSettings.builder()
					// 사용자 동의 화면 생략 (개발 편의상)
					.requireAuthorizationConsent(false).build()
			).tokenSettings(
				TokenSettings.builder()
					// 액세스 토큰 만료 시간 (5분)
					.accessTokenTimeToLive(Duration.ofMinutes(5))
					// 리프레시 토큰 만료 시간 (60분)
					.refreshTokenTimeToLive(Duration.ofMinutes(60))
					// JWT 토큰 형식 사용 (opaque 토큰 대신)
					.accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED).build()
			).build()

		return InMemoryRegisteredClientRepository(registeredClient)
	}

	/**
	 * JWT 서명을 위한 키 설정
	 * - RSA 키 페어 생성 및 JWK Set 구성
	 * - 토큰 서명 및 검증에 사용
	 */
	@Bean
	fun jwkSource(): JWKSource<SecurityContext> {
		// RSA 키 페어 생성
		val keyPair = generateRsaKey()
		val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey).privateKey(keyPair.private as RSAPrivateKey)
			.keyID(UUID.randomUUID().toString()) // 키 식별자
			.build()

		val jwkSet = JWKSet(rsaKey)
		return ImmutableJWKSet(jwkSet)
	}

	/**
	 * RSA 키 페어 생성
	 * - JWT 토큰 서명용 개인키와 검증용 공개키 생성
	 */
	private fun generateRsaKey(): KeyPair {
		val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
		keyPairGenerator.initialize(2048) // 2048비트 키 사용
		return keyPairGenerator.generateKeyPair()
	}

	/**
	 * JWT 디코더 설정
	 * - 발급한 JWT 토큰을 검증하고 파싱하는 역할
	 * - Authorization Server 내부에서 토큰 검증 시 사용
	 */
	@Bean
	fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)
	}

	/**
	 * JWT 토큰 커스터마이저 (개선됨 - 실제 DB 정보 활용)
	 * - 토큰에 포함될 클레임 정의 (account_id, shopl_client_id, role 등)
	 * - 사용자 정보를 토큰에 포함시키는 역할
	 */
	@Bean
	fun jwtCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> {
		return OAuth2TokenCustomizer { context ->
			if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN) {
				// 인증된 사용자 정보 가져오기
				val principal = context.getPrincipal<Authentication>()
				val customUserDetails = principal.principal as? CustomUserDetails

				if (customUserDetails != null) {
					val account = customUserDetails.getAccount()

					// JWT 클레임에 사용자 정보 추가
					context.claims
						.claim(SecurityConstants.CLAIM_ACCOUNT_ID, account.id)
						.claim(SecurityConstants.CLAIM_SHOPL_CLIENT_ID, account.shoplClientId)
						.claim(SecurityConstants.CLAIM_SHOPL_USER_ID, account.shoplUserId)
						.claim(SecurityConstants.CLAIM_EMAIL, account.email)
						.claim(SecurityConstants.CLAIM_NAME, account.name)
						.claim(SecurityConstants.CLAIM_ROLE, SecurityConstants.DEFAULT_ROLE)
						.claim(SecurityConstants.CLAIM_USERNAME, customUserDetails.username)
				}
			}
		}
	}

	/**
	 * 데이터베이스 기반 UserDetailsService Bean 등록
	 */
	@Bean
	fun userDetailsService(): UserDetailsService {
		return customUserDetailsService
	}

	/**
	 * CORS 설정
	 * - 프론트엔드에서 API 호출 시 필요
	 * - 개발 환경에서는 모든 Origin 허용, 프로덕션에서는 제한 필요
	 */
	@Bean
	fun corsConfigurationSource(): CorsConfigurationSource {
		val configuration = CorsConfiguration()

		// 개발 환경용 설정 (프로덕션에서는 구체적인 도메인 지정 필요)
		configuration.allowedOriginPatterns = listOf("http://localhost:*", "http://127.0.0.1:*")
		configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
		configuration.allowedHeaders = listOf("*")
		configuration.allowCredentials = true

		// Authorization 헤더 노출 (JWT 토큰 전달용)
		configuration.exposedHeaders = listOf("Authorization")

		val source = UrlBasedCorsConfigurationSource()
		source.registerCorsConfiguration("/**", configuration)
		return source
	}
}