package me.practice.oauth2.service.history

import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.IoIdpLoginHistoryRepository
import me.practice.oauth2.entity.LoginResult
import me.practice.oauth2.entity.LoginType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 로그인 이력 통계 서비스
 * 단일 책임: 로그인 통계 계산 및 분석
 */
@Service
@Transactional(readOnly = true)
class LoginHistoryStatisticsService(
	private val loginHistoryRepository: IoIdpLoginHistoryRepository,
) {

	/**
	 * 클라이언트별 로그인 통계를 조회합니다.
	 *
	 * 지정된 기간 동안의 성공/실패 로그인 통계와 성공률을 계산하여 반환합니다.
	 * 대시보드나 모니터링 화면에서 사용됩니다.
	 *
	 * @param query 통계 조회 조건
	 * @return 로그인 통계 정보 (성공/실패 횟수, 총 횟수, 성공률)
	 */
	fun getClientLoginStats(query: StatisticsQuery): LoginStatistics {
		val since = LocalDateTime.now().minusDays(query.daysBack)
		val stats = loginHistoryRepository.getLoginStatsByClient(query.shoplClientId, since)

		var successCount = 0L
		var failCount = 0L

		stats.forEach { row ->
			val result = row[0] as LoginResult
			val count = (row[1] as Number).toLong()
			when (result) {
				LoginResult.SUCCESS -> successCount = count
				LoginResult.FAIL -> failCount = count
			}
		}

		return LoginStatistics(
			successCount = successCount,
			failCount = failCount,
			totalCount = successCount + failCount,
			successRate = calculateSuccessRate(successCount, successCount + failCount)
		)
	}

	/**
	 * 로그인 타입별 통계 조회
	 *
	 * @param query 통계 조회 조건
	 * @return 로그인 타입별 통계 목록
	 */
	fun getLoginTypeStats(query: StatisticsQuery): List<LoginTypeStatistics> {
		val since = LocalDateTime.now().minusDays(query.daysBack)
		val stats = loginHistoryRepository.getLoginTypeStats(query.shoplClientId, since)

		val typeStatsMap = mutableMapOf<LoginType, MutableMap<LoginResult, Long>>()

		stats.forEach { row ->
			val loginType = row[0] as LoginType
			val result = row[1] as LoginResult
			val count = (row[2] as Number).toLong()

			typeStatsMap.computeIfAbsent(loginType) { mutableMapOf() }[result] = count
		}

		return typeStatsMap.map { (type, resultMap) ->
			val success = resultMap[LoginResult.SUCCESS] ?: 0L
			val fail = resultMap[LoginResult.FAIL] ?: 0L
			val total = success + fail

			LoginTypeStatistics(
				loginType = type,
				successCount = success,
				failCount = fail,
				totalCount = total,
				successRate = calculateSuccessRate(success, total)
			)
		}
	}

	/**
	 * 최근 실패한 로그인 시도 횟수를 조회합니다.
	 *
	 * 보안 목적으로 특정 사용자의 최근 실패 시도 횟수를 확인합니다.
	 * 계정 잠금이나 추가 보안 검증 시 사용됩니다.
	 *
	 * @return 실패한 로그인 시도 횟수
	 */
	fun getRecentFailedLoginAttempts(
		shoplUserId: String,
		minutesBack: Long,
	): Long {
		return loginHistoryRepository.findByShoplUserIdAndResultAndRegDtAfter(
			shoplUserId = shoplUserId,
			result = LoginResult.FAIL,
			since = LocalDateTime.now().minusMinutes(minutesBack)
		)
			.filter { it.failureReason === FailureReasonType.INVALID_CREDENTIALS }
			.size.toLong()
	}

	/**
	 * IP 주소별 최근 로그인 시도 횟수를 조회합니다.
	 *
	 * 보안 모니터링 목적으로 특정 IP에서의 로그인 시도 패턴을 분석합니다.
	 * DDoS 공격이나 무차별 대입 공격 탐지에 사용됩니다.
	 *
	 * @param query IP 기반 로그인 조회 조건
	 * @return 해당 IP에서의 로그인 시도 총 횟수
	 */
	fun getRecentLoginAttemptsByIp(query: IpBasedLoginQuery): Long {
		val since = LocalDateTime.now().minusHours(query.hoursBack)
		return loginHistoryRepository.countLoginAttemptsByIp(query.ipAddress, since)
	}

	/**
	 * 성공률 계산 (0.0 ~ 100.0)
	 *
	 * @param successCount 성공 횟수
	 * @param totalCount 총 횟수
	 * @return 성공률 (백분율)
	 */
	private fun calculateSuccessRate(successCount: Long, totalCount: Long): Double {
		return if (totalCount > 0) {
			(successCount.toDouble() / totalCount * 100)
		} else {
			0.0
		}
	}
}

/**
 * 로그인 통계 정보를 담는 데이터 클래스
 *
 * @property successCount 성공한 로그인 횟수
 * @property failCount 실패한 로그인 횟수
 * @property totalCount 전체 로그인 시도 횟수
 * @property successRate 로그인 성공률 (0.0 ~ 100.0)
 */
data class LoginStatistics(
	val successCount: Long,
	val failCount: Long,
	val totalCount: Long,
	val successRate: Double,
)

/**
 * 로그인 타입별 통계 정보를 담는 데이터 클래스
 *
 * @property loginType 로그인 타입 (BASIC, SOCIAL, SSO)
 * @property successCount 해당 타입의 성공한 로그인 횟수
 * @property failCount 해당 타입의 실패한 로그인 횟수
 * @property totalCount 해당 타입의 전체 로그인 시도 횟수
 * @property successRate 해당 타입의 로그인 성공률 (0.0 ~ 100.0)
 */
data class LoginTypeStatistics(
	val loginType: LoginType,
	val successCount: Long,
	val failCount: Long,
	val totalCount: Long,
	val successRate: Double,
)