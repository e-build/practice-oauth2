package me.practice.oauth2.service

import me.practice.oauth2.exception.TooManyAttemptsException
import me.practice.oauth2.service.history.LoginHistoryStatisticsService
import org.springframework.stereotype.Component

/**
 * 로그인 보안 검증 서비스
 * 단일 책임: 로그인 시도에 대한 보안 검증
 */
@Component
class LoginSecurityValidator(
	private val statisticsService: LoginHistoryStatisticsService,
) {

	companion object {
		private const val MAX_FAILED_ATTEMPTS = 5L // 최대 실패 시도 횟수
		private const val FAILED_ATTEMPTS_TIME_WINDOW = 5L // 실패 시도 횟수 확인 시간 윈도우 (분)
	}

	/**
	 * 로그인 시도 횟수를 검증합니다.
	 *
	 * @param shoplUserId 사용자 ID
	 * @throws TooManyAttemptsException 최대 시도 횟수 초과 시
	 */
	fun validateLoginAttempts(shoplUserId: String) {
		val failedAttempts = statisticsService.getRecentFailedLoginAttempts(shoplUserId, FAILED_ATTEMPTS_TIME_WINDOW)

		if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
			throw TooManyAttemptsException(shoplUserId, failedAttempts, FAILED_ATTEMPTS_TIME_WINDOW)
		}
	}
}