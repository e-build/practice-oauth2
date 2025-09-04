package me.practice.oauth2.client.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class ResourceServerSecurityConfiguration(
//	private val cacheManager: CacheManager,
) {

	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http
			// Stateless 유지 (JWT 토큰 기반)
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests { authorize ->
				authorize
					// 공개 엔드포인트 (OAuth2 로그인 관련 제거)
					.requestMatchers("/public/**").permitAll()
					.requestMatchers("/health", "/actuator/health").permitAll()
					.requestMatchers("/favicon.ico").permitAll()
					.requestMatchers("/error").permitAll()
					.requestMatchers("/dashboard").permitAll()
					.requestMatchers("/error").permitAll()
					// 나머지는 인증 필요
					.anyRequest().authenticated()
			}
			// Resource Server 설정만 유지하고 개선
			.oauth2ResourceServer { oauth2 ->
				oauth2.jwt { jwt ->
					// JWK Set URI 포트 수정
					jwt.decoder(jwtDecoder())
					// JWT 인증 변환기 추가
					jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
				}
			}
			// CSRF 비활성화 (API 서버이므로)
			.csrf { it.disable() }

		return http.build()
	}

	/**
	 * JWT 디코더 설정
	 * - Authorization Server(9000)에서 JWT 검증
	 */
	@Bean
	fun jwtDecoder(): JwtDecoder {
		val jwtDecoder = NimbusJwtDecoder
			.withJwkSetUri("http://localhost:9000/oauth2/jwks")
//			.cache(cacheManager.getCache("jwks"))
			.build()

		// JWT 검증 규칙 설정
		jwtDecoder.setJwtValidator(jwtValidator())
		return jwtDecoder
	}

	/**
	 * JWT 검증 규칙
	 */
	@Bean
	fun jwtValidator(): OAuth2TokenValidator<Jwt> {
		val validators = mutableListOf<OAuth2TokenValidator<Jwt>>()
		validators.add(JwtTimestampValidator()) // 만료시간 검증
		validators.add(JwtIssuerValidator("http://localhost:9000")) // 발급자 검증
		return DelegatingOAuth2TokenValidator(validators)
	}

	/**
	 * JWT를 Spring Security Authentication으로 변환
	 */
	@Bean
	fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
		val converter = JwtAuthenticationConverter()
		converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter())
		converter.setPrincipalClaimName("username") // 사용자명 클레임 설정
		return converter
	}

	/**
	 * JWT 클레임을 권한으로 변환
	 * - Authorization Server에서 설정한 user_id, role 클레임 활용
	 */
	@Bean
	fun jwtGrantedAuthoritiesConverter(): Converter<Jwt, Collection<GrantedAuthority>> {
		return Converter { jwt ->
			val authorities = mutableListOf<GrantedAuthority>()

			// role 클레임을 ROLE_ 권한으로 변환
			jwt.getClaimAsString("role")?.let { role ->
				authorities.add(SimpleGrantedAuthority("ROLE_$role"))
			}

			// scope 클레임을 SCOPE_ 권한으로 변환
			jwt.getClaimAsStringList("scope")?.forEach { scope ->
				authorities.add(SimpleGrantedAuthority("SCOPE_$scope"))
			}

			authorities
		}
	}
}