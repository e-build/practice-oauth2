package me.practice.oauth2.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class PasswordConfiguration {

	/**
	 * 비밀번호 암호화 설정
	 * - BCrypt를 기본으로 하고 개발용 NoOp 지원
	 */
	@Bean
	fun passwordEncoder(): PasswordEncoder {
		val idForEncode = "bcrypt"
		val encoders = mutableMapOf<String, PasswordEncoder>()
		encoders[idForEncode] = BCryptPasswordEncoder()
		encoders["noop"] = NoOpPasswordEncoder.getInstance() // 개발용
		return DelegatingPasswordEncoder(idForEncode, encoders)
	}
}