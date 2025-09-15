package me.practice.oauth2.configuration

import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.domain.IdpClient
import org.springframework.security.authentication.*
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.entity.IoIdpAccount
import org.slf4j.LoggerFactory

/**
 * 데이터베이스 기반 사용자 인증을 처리하는 AuthenticationProvider
 */
@Component
class BasicAuthenticationProvider(
	private val userDetailsService: CustomUserDetailsService,
	private val passwordEncoder: PasswordEncoder,
	private val loginHistoryService: LoginHistoryService,
) : AuthenticationProvider {

	private val logger = LoggerFactory.getLogger(BasicAuthenticationProvider::class.java)

	override fun authenticate(authentication: Authentication): Authentication {
		val username = authentication.name
		val rawPassword = authentication.credentials.toString()

		// 데이터베이스에서 사용자 정보 조회
		val userDetails = userDetailsService.loadUserByUsername(username) as CustomUserDetails

		// 계정 활성화 상태 확인
		if (!userDetails.isEnabled) {
			throw DisabledException("Account is disabled: $username")
		}

		// 계정 잠김 상태 확인
		if (!userDetails.isAccountNonLocked) {
			throw LockedException("Account is locked: $username")
		}

		// 비밀번호 검증
		if (userDetails.password != null && !passwordEncoder.matches(rawPassword, userDetails.password)) {
			// 로그인 실패 처리 (실패 횟수 증가 등)
			handleAuthenticationFailure(userDetails.getAccount())
			throw BadCredentialsException("Invalid password")
		}

		// 로그인 성공 처리
		handleAuthenticationSuccess(userDetails.getAccount())

		return UsernamePasswordAuthenticationToken(
			userDetails,
			null, // 인증 후에는 비밀번호를 null로 설정
			userDetails.authorities
		)
	}

	override fun supports(authentication: Class<*>): Boolean {
		return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
	}

	/**
	 * 로그인 성공 시 처리
	 * - 마지막 로그인 시간 업데이트
	 * - 실패 횟수 초기화
	 * - 로그인 이력 기록
	 */
	private fun handleAuthenticationSuccess(account: IoIdpAccount) {
		try {
			val request = getCurrentRequest()
			val sessionId = request?.session?.id ?: "unknown-session"
			
			loginHistoryService.recordSuccessfulLogin(
				shoplClientId = account.shoplClientId,
				shoplUserId = account.shoplUserId,
				platform = IdpClient.Platform.DASHBOARD,
				loginType = LoginType.BASIC,
				sessionId = sessionId,
				request = request
			)
		} catch (e: Exception) {
			// 로그인 이력 기록 실패가 인증 자체를 방해하지 않도록 예외 처리
			logger.warn("Failed to record login history for user: ${account.shoplUserId}", e)
		}
		
		// 실제 구현시에는 별도 서비스에서 처리
		// accountService.updateLastLoginTime(accountId)
		// accountService.resetFailedAttempts(accountId)
	}

	/**
	 * 로그인 실패 시 처리
	 * - 실패 횟수 증가
	 * - 임계값 도달 시 계정 잠김
	 * - 로그인 실패 이력 기록
	 */
	private fun handleAuthenticationFailure(account: IoIdpAccount) {
		try {
			val request = getCurrentRequest()
			val sessionId = request?.session?.id ?: "unknown-session"
			
			loginHistoryService.recordFailedLogin(
				shoplClientId = account.shoplClientId,
				shoplUserId = account.shoplUserId,
				platform = IdpClient.Platform.DASHBOARD,
				loginType = LoginType.BASIC,
				failureReason = FailureReasonType.INVALID_CREDENTIALS,
				sessionId = sessionId,
				request = request
			)
		} catch (e: Exception) {
			// 로그인 이력 기록 실패가 인증 자체를 방해하지 않도록 예외 처리
			logger.warn("Failed to record failed login history for user: ${account.shoplUserId}", e)
		}
		
		// 실제 구현시에는 별도 서비스에서 처리
		// accountService.incrementFailedAttempts(accountId)
	}
	
	/**
	 * 현재 HTTP 요청 가져오기
	 */
	private fun getCurrentRequest(): HttpServletRequest? {
		return try {
			val attributes = RequestContextHolder.currentRequestAttributes() as? ServletRequestAttributes
			attributes?.request
		} catch (e: Exception) {
			null
		}
	}
}