package me.practice.oauth2.configuration

import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.AccountValidator
import me.practice.oauth2.service.PasswordValidator
import me.practice.oauth2.service.RequestContextService
import me.practice.oauth2.domain.IdpClient
import org.springframework.security.authentication.*
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import me.practice.oauth2.entity.IoIdpAccount
import org.slf4j.LoggerFactory

/**
 * 데이터베이스 기반 사용자 인증을 처리하는 AuthenticationProvider
 * 단일 책임: 인증 프로세스 조율 및 결과 반환
 */
@Component
class BasicAuthenticationProvider(
	private val userDetailsService: CustomUserDetailsService,
	private val accountValidator: AccountValidator,
	private val passwordValidator: PasswordValidator,
	private val loginHistoryService: LoginHistoryService,
	private val requestContextService: RequestContextService
) : AuthenticationProvider {

	private val logger = LoggerFactory.getLogger(BasicAuthenticationProvider::class.java)

	override fun authenticate(authentication: Authentication): Authentication {
		val username = authentication.name
		val rawPassword = authentication.credentials.toString()

		// 데이터베이스에서 사용자 정보 조회
		val userDetails = userDetailsService.loadUserByUsername(username) as CustomUserDetails

		// 계정 상태 검증 (별도 클래스에서 처리)
		accountValidator.validateAccountStatus(userDetails)

		try {
			// 비밀번호 검증 (별도 클래스에서 처리)
			passwordValidator.validatePassword(userDetails, rawPassword)

			// 로그인 성공 처리
			handleAuthenticationSuccess(userDetails.getAccount())
		} catch (e: BadCredentialsException) {
			// 로그인 실패 처리
			handleAuthenticationFailure(userDetails.getAccount())
			throw e
		}

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
	 * - 로그인 이력 기록
	 */
	private fun handleAuthenticationSuccess(account: IoIdpAccount) {
		try {
			val request = requestContextService.getCurrentRequest()
			val sessionId = requestContextService.getCurrentSessionId()

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
	}

	/**
	 * 로그인 실패 시 처리
	 * - 로그인 실패 이력 기록
	 */
	private fun handleAuthenticationFailure(account: IoIdpAccount) {
		try {
			val request = requestContextService.getCurrentRequest()
			val sessionId = requestContextService.getCurrentSessionId()

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
	}
}