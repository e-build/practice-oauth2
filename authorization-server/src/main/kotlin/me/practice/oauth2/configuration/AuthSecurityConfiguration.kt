package me.practice.oauth2.configuration

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import me.practice.oauth2.handler.OAuth2AuthenticationFailureHandler
import me.practice.oauth2.handler.SsoAuthenticationSuccessHandler
import me.practice.oauth2.infrastructure.redis.AuthorizationJsonCodec
import me.practice.oauth2.infrastructure.redis.RedisAuthorizationProperties
import me.practice.oauth2.infrastructure.redis.RedisOAuth2AuthorizationService
import me.practice.oauth2.service.CompositeClientRegistrationRepository
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AppProperties::class)
class AuthSecurityConfiguration(
	private val oAuth2AuthorizationServerProperties: OAuth2AuthorizationServerProperties,
	private val basicAuthenticationProvider: BasicAuthenticationProvider,
	private val basicLoginUserDetailsService: BasicLoginUserDetailsService,
	private val appProperties: AppProperties,
	private val registeredClientRepository: RegisteredClientRepository,
	private val oAuth2AuthorizationConsentService: OAuth2AuthorizationConsentService,
	private val stringRedisTemplate: StringRedisTemplate,
	private val authorizationJsonCodec: AuthorizationJsonCodec,
	private val redisAuthorizationProperties: RedisAuthorizationProperties,
	private val compositeClientRegistrationRepository: CompositeClientRegistrationRepository,
	private val ssoAuthenticationSuccessHandler: SsoAuthenticationSuccessHandler,
	private val oAuth2AuthenticationFailureHandler: OAuth2AuthenticationFailureHandler,
	private val oAuth2ContextCaptureFilter: OAuth2ContextCaptureFilter,
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
	fun authorizationServerSecurityFilterChain(
		http: HttpSecurity,
	): SecurityFilterChain {
		val authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer()

		http.securityMatcher(authorizationServerConfigurer.endpointsMatcher)
			.cors(Customizer.withDefaults())
			.authorizeHttpRequests {
				it.anyRequest().authenticated()
			}.csrf { csrf ->
				// OAuth2 엔드포인트는 CSRF 보호에서 제외 (토큰 기반 인증이므로)
				csrf.ignoringRequestMatchers(authorizationServerConfigurer.endpointsMatcher)
			}.with(authorizationServerConfigurer) {
				it.authorizationServerSettings(authorizationServerSettings())
					.registeredClientRepository(registeredClientRepository)
					.authorizationService(redisOAuth2AuthorizationService())
					.authorizationConsentService(oAuth2AuthorizationConsentService)
					.oidc(Customizer.withDefaults())
			}

		// 사용자 인증을 위한 로그인 폼 설정
		http.formLogin(Customizer.withDefaults())

		return http.build()
	}

	@Bean
	fun redisOAuth2AuthorizationService(): OAuth2AuthorizationService {
		return RedisOAuth2AuthorizationService(
			redis = stringRedisTemplate,
			codec = authorizationJsonCodec,
			props = redisAuthorizationProperties
		)
	}

	/**
	 * 2. 일반적인 애플리케이션 인증을 위한 SecurityFilterChain
	 * - Authorization Server 자체의 관리 페이지나 기타 엔드포인트 보호
	 * - OAuth2 엔드포인트가 아닌 일반 웹 요청 처리
	 */
	@Bean
	@Order(2)
	fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http.authenticationProvider(basicAuthenticationProvider)
			.cors(Customizer.withDefaults())
			.authorizeHttpRequests {
				it.requestMatchers("/favicon.ico").permitAll()
					.requestMatchers("/error").permitAll()
					.requestMatchers("/.well-known/**").permitAll()
					// Swagger UI 관련 모든 경로 허용
					.requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
					.requestMatchers("/v3/api-docs/**", "/api-docs/**").permitAll()
					.requestMatchers("/swagger-resources/**").permitAll()
					.requestMatchers("/webjars/**").permitAll()
					.requestMatchers("/login").permitAll()
					.requestMatchers("/sso/**").permitAll() // SSO 관련 엔드포인트 허용
					.requestMatchers("/api/sso/**").permitAll() // Internal SSO API 허용
					.requestMatchers("/static/**", "/css/**", "/js/**", "/images/**").permitAll()
					.requestMatchers("/").permitAll() // 루트 경로 허용 추가
					.anyRequest().authenticated()
			}.formLogin { form ->
				form.loginPage("/login") // 커스텀 로그인 페이지 경로
					.defaultSuccessUrl("${appProperties.resourceServer.baseUrl}/admin/home", true) // 로그인 성공 시 리다이렉트 URL 명시적 설정
					.permitAll()
			}
			.oauth2Login { oauth2 ->
				oauth2.clientRegistrationRepository(compositeClientRegistrationRepository)
					.loginPage("/login") // 기본 로그인 페이지 사용
					.successHandler(ssoAuthenticationSuccessHandler)
					.failureHandler(oAuth2AuthenticationFailureHandler)
			}
			// OAuth2 컨텍스트 캡처 필터 추가 (DON-49: OAuth2 시작 시점 컨텍스트 저장)
			.addFilterBefore(oAuth2ContextCaptureFilter, UsernamePasswordAuthenticationFilter::class.java)
			.logout {
				it
					.logoutUrl("/logout")
					.logoutSuccessUrl("${appProperties.resourceServer.baseUrl}/admin/home")
					.invalidateHttpSession(true)
					.clearAuthentication(true)
					.deleteCookies("JSESSIONID")
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
	 * JWT 서명을 위한 키 설정
	 * - RSA 키 페어 생성 및 JWK Set 구성
	 * - 토큰 서명 및 검증에 사용
	 */
	@Bean
	fun jwkSource(): JWKSource<SecurityContext> {
		// RSA 키 페어 생성
		val keyPair = generateRsaKey()
		val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
			.privateKey(keyPair.private as RSAPrivateKey)
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
						.claim(TokenClaimConstants.CLAIM_ACCOUNT_ID, account.id)
						.claim(TokenClaimConstants.CLAIM_SHOPL_CLIENT_ID, account.shoplClientId)
						.claim(TokenClaimConstants.CLAIM_SHOPL_USER_ID, account.shoplUserId)
						.claim(TokenClaimConstants.CLAIM_EMAIL, account.email)
						.claim(TokenClaimConstants.CLAIM_NAME, account.name)
						.claim(TokenClaimConstants.CLAIM_ROLE, TokenClaimConstants.DEFAULT_ROLE)
						.claim(TokenClaimConstants.CLAIM_USERNAME, customUserDetails.username)
				}
			}
		}
	}

	/**
	 * 데이터베이스 기반 UserDetailsService Bean 등록
	 */
	@Bean
	fun userDetailsService(): UserDetailsService {
		return basicLoginUserDetailsService
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