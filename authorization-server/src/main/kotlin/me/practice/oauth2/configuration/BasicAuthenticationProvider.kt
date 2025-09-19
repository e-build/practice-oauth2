package me.practice.oauth2.configuration

import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.AccountValidator
import me.practice.oauth2.service.PasswordValidator
import me.practice.oauth2.service.RequestContextService
import me.practice.oauth2.service.LoginSecurityValidator
import me.practice.oauth2.exception.AccountExpiredException
import me.practice.oauth2.exception.PasswordExpiredException
import me.practice.oauth2.exception.TooManyAttemptsException
import me.practice.oauth2.exception.GlobalAuthenticationExceptionHandler
import org.springframework.security.authentication.*
import org.springframework.dao.DataAccessException
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
	private val userDetailsService: BasicLoginUserDetailsService,
	private val accountValidator: AccountValidator,
	private val passwordValidator: PasswordValidator,
	private val loginHistoryService: LoginHistoryService,
	private val requestContextService: RequestContextService,
	private val loginSecurityValidator: LoginSecurityValidator,
	private val globalExceptionHandler: GlobalAuthenticationExceptionHandler
) : AuthenticationProvider {

	private val logger = LoggerFactory.getLogger(BasicAuthenticationProvider::class.java)

	override fun authenticate(authentication: Authentication): Authentication {
		val username = authentication.name
		val rawPassword = authentication.credentials.toString()

		// 데이터베이스에서 사용자 정보 조회
		val userDetails = userDetailsService.loadUserByUsername(username) as CustomUserDetails
		val account: IoIdpAccount = userDetails.getAccount()

		try {
			// 보안 검증 (로그인 시도 횟수 등)
			loginSecurityValidator.validateLoginAttempts(account.shoplUserId)

			// 계정 상태 검증 (별도 클래스에서 처리)
			accountValidator.validateAccountStatus(userDetails)

			// 비밀번호 검증 (별도 클래스에서 처리)
			passwordValidator.validatePassword(userDetails, rawPassword)

			// 로그인 성공 처리
			handleAuthenticationSuccess(account)
		} catch (e: TooManyAttemptsException) {
			// 로그인 시도 횟수 초과
			handleAuthenticationFailure(account, FailureReasonType.TOO_MANY_ATTEMPTS)
			throw e
		} catch (e: AccountExpiredException) {
			// 계정 만료
			handleAuthenticationFailure(account, FailureReasonType.ACCOUNT_EXPIRED)
			throw e
		} catch (e: LockedException) {
			// 계정 잠금
			handleAuthenticationFailure(account, FailureReasonType.ACCOUNT_LOCKED)
			throw e
		} catch (e: DisabledException) {
			// 계정 비활성화
			handleAuthenticationFailure(account, FailureReasonType.ACCOUNT_DISABLED)
			throw e
		} catch (e: PasswordExpiredException) {
			// 비밀번호 만료
			handleAuthenticationFailure(account, FailureReasonType.PASSWORD_EXPIRED)
			throw e
		} catch (e: BadCredentialsException) {
			// 잘못된 자격증명
			handleAuthenticationFailure(account, FailureReasonType.INVALID_CREDENTIALS)
			throw e
		} catch (e: DataAccessException) {
			// 데이터베이스 관련 시스템 오류
			handleSystemException(e, account)
			throw InternalAuthenticationServiceException("Database access error", e)
		} catch (e: Exception) {
			// 기타 시스템 오류
			handleSystemException(e, account)
			throw InternalAuthenticationServiceException("System error during authentication", e)
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
	private fun handleAuthenticationFailure(account: IoIdpAccount, failureReason: FailureReasonType) {
		try {
			val request = requestContextService.getCurrentRequest()
			val sessionId = requestContextService.getCurrentSessionId()

			loginHistoryService.recordFailedLogin(
				shoplClientId = account.shoplClientId,
				shoplUserId = account.shoplUserId,
				loginType = LoginType.BASIC,
				failureReason = failureReason,
				sessionId = sessionId,
				request = request
			)
		} catch (e: Exception) {
			// 로그인 이력 기록 실패가 인증 자체를 방해하지 않도록 예외 처리
			logger.warn("Failed to record failed login history for user: ${account.shoplUserId}", e)
		}
	}

	/**
	 * 시스템 예외 처리
	 * - 전역 예외 처리기에 위임하여 시스템 오류 이력 기록
	 */
	private fun handleSystemException(exception: Exception, account: IoIdpAccount) {
		try {
			val request = requestContextService.getCurrentRequest()
			globalExceptionHandler.handleSystemException(
				exception = exception,
				request = request,
				shoplClientId = account.shoplClientId,
				shoplUserId = account.shoplUserId
			)
		} catch (e: Exception) {
			logger.error("Failed to handle system exception for user: ${account.shoplUserId}", e)
		}
	}
}