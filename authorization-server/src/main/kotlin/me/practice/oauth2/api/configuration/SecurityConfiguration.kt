package me.practice.oauth2.api.configuration

import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import java.time.Duration
import java.util.*


@Configuration
@EnableWebSecurity
class SecurityConfiguration(
	private val oAuth2AuthorizationServerProperties: OAuth2AuthorizationServerProperties,
) {

	@Bean
	@Order(1)
	@Throws(Exception::class)
	fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
		OAuth2AuthorizationServerConfigurer.authorizationServer()
			.let { authorizationServerConfigurer ->
				http.securityMatcher(authorizationServerConfigurer.endpointsMatcher)
					.authorizeHttpRequests {
						it.anyRequest().authenticated()
					}
					.csrf { csrf ->
						csrf.ignoringRequestMatchers(authorizationServerConfigurer.endpointsMatcher)
					}
					.with(authorizationServerConfigurer) {
						it
							.authorizationServerSettings(authorizationServerSettings())
							.registeredClientRepository(registeredClientRepository())
							// OIDC 활성화
							.oidc(Customizer.withDefaults())
					}
			}

		// 로그인 폼 설정
		http.formLogin(Customizer.withDefaults())

		return http.build()
	}

	/**
	 * 2. 일반적인 애플리케이션 인증을 위한 SecurityFilterChain.
	 * @Order(2)로 두 번째 우선순위를 가집니다.
	 */
	@Bean
	@Order(2)
	fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.authorizeHttpRequests {
				it
//					.requestMatchers("/public/**").permitAll()
//					.requestMatchers("/oauth2/**").permitAll()
					.requestMatchers("/favicon.ico").permitAll()
					.requestMatchers("/error").permitAll()
					.requestMatchers("/.well-known/**").permitAll()
					.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
					.anyRequest().authenticated()
			}
			.formLogin{ form ->
				form
					.defaultSuccessUrl("http://localhost:9001/home", true)  // 로그인 성공 시 홈으로 리다이렉트
					.permitAll()
			}
			.csrf { it.disable() } // 개발 편의상 비활성화

		return http.build()
	}

	@Bean
	fun authorizationServerSettings(): AuthorizationServerSettings {
		return AuthorizationServerSettings.builder()
			.issuer(oAuth2AuthorizationServerProperties.issuer)
			.build()
	}

	/**
	 * RegisteredClientRepository 빈 설정
	 * OAuth2 클라이언트 정보를 관리합니다.
	 */
	@Bean
	fun registeredClientRepository(): RegisteredClientRepository {
		val registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
			.clientId("oauth2-client")
			.clientSecret("{noop}secret")
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
			.redirectUri("http://localhost:9001/login/oauth2/code/my-oauth2-server")
//			.redirectUri("http://localhost:9001/home")
			.scope(OidcScopes.OPENID)
			.scope(OidcScopes.PROFILE)
			.scope("read")
			.scope("write")
			.clientSettings(
				ClientSettings.builder()
					.requireAuthorizationConsent(true)   // 동의 화면 요구
					.build()
			)
			.tokenSettings(
				TokenSettings.builder()
					.accessTokenTimeToLive(Duration.ofMinutes(5))
					.refreshTokenTimeToLive(Duration.ofMinutes(60))
					.build()
			)
			.build()

		return InMemoryRegisteredClientRepository(registeredClient)
	}

	/**
	 * PasswordEncoder 빈 설정
	 * 비밀번호 암호화를 위해 사용됩니다.
	 */
	@Bean
	fun passwordEncoder(): PasswordEncoder {
		val idForEncode = "bcrypt"
		val encoders = mutableMapOf<String, PasswordEncoder>()
		encoders[idForEncode] = BCryptPasswordEncoder()
		return DelegatingPasswordEncoder(idForEncode, encoders)
	}

	/**
	 * 사용자 정보 설정 (테스트용)
	 */
	@Bean
	fun userDetailsService(): UserDetailsService {
		val staff = User.builder()
			.username("staff")
			.password(passwordEncoder().encode("staff"))
			.roles("STAFF")
			.build()

		val leader = User.builder()
			.username("leader")
			.password(passwordEncoder().encode("leader"))
			.roles("LEADER", "STAFF")
			.build()

		return InMemoryUserDetailsManager(staff, leader)
	}

}