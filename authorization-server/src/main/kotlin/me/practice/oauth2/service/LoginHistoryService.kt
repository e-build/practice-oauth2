package me.practice.oauth2.service

import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 로그인 이력 관리 서비스
 *
 * OAuth2 인증 서버에서 사용자의 로그인 이력을 기록하고 조회하는 기능을 제공합니다.
 * 성공/실패 로그인 이력, 통계, 보안 모니터링 기능을 포함합니다.
 *
 * @author OAuth2 Team
 * @since 1.0.0
 */
@Service
@Transactional
class LoginHistoryService(
	private val loginHistoryRepository: IoIdpLoginHistoryRepository,
) {

	private val logger = LoggerFactory.getLogger(LoginHistoryService::class.java)

	/**
	 * 로그인 성공 이력을 저장합니다.
	 *
	 * 사용자가 성공적으로 로그인했을 때 호출되어 이력을 데이터베이스에 기록합니다.
	 * IP 주소, User-Agent 등의 부가 정보도 함께 저장됩니다.
	 *
	 * @param shoplClientId 클라이언트 ID
	 * @param shoplUserId 사용자 ID
	 * @param platform 플랫폼 (DASHBOARD, APP)
	 * @param loginType 로그인 타입 (BASIC, SOCIAL, SSO)
	 * @param provider 소셜 로그인 제공자 (optional)
	 * @param sessionId 세션 ID
	 * @param request HTTP 요청 객체 (IP, User-Agent 추출용)
	 * @return 저장된 로그인 이력 엔티티
	 */
	fun recordSuccessfulLogin(
		shoplClientId: String,
		shoplUserId: String,
		platform: IdpClient.Platform,
		loginType: LoginType,
		provider: String? = null,
		sessionId: String,
		request: HttpServletRequest? = null,
	): IoIdpUserLoginHistory {
		val history = IoIdpUserLoginHistory(
			shoplClientId = shoplClientId,
			shoplUserId = shoplUserId,
			platform = platform,
			loginType = loginType,
			provider = provider,
			result = LoginResult.SUCCESS,
			sessionId = sessionId,
			ipAddress = extractIpAddress(request),
			userAgent = extractUserAgent(request)
		)

		val savedHistory = loginHistoryRepository.save(history)
		logger.info(
			"Recorded successful login: userId={}, clientId={}, type={}, provider={}",
			shoplUserId, shoplClientId, loginType, provider
		)

		return savedHistory
	}

	/**
	 * 로그인 실패 이력을 저장합니다.
	 *
	 * 사용자의 로그인 시도가 실패했을 때 호출되어 실패 이력과 원인을 데이터베이스에 기록합니다.
	 * 보안 모니터링과 분석을 위해 실패 원인도 함께 저장됩니다.
	 *
	 * @param shoplClientId 클라이언트 ID
	 * @param shoplUserId 사용자 ID
	 * @param platform 플랫폼 (DASHBOARD, APP)
	 * @param loginType 로그인 타입 (BASIC, SOCIAL, SSO)
	 * @param provider 소셜 로그인 제공자 (optional)
	 * @param failureReason 로그인 실패 원인
	 * @param sessionId 세션 ID
	 * @param request HTTP 요청 객체 (IP, User-Agent 추출용)
	 * @return 저장된 로그인 실패 이력 엔티티
	 */
	fun recordFailedLogin(
		shoplClientId: String,
		shoplUserId: String,
		platform: IdpClient.Platform,
		loginType: LoginType,
		provider: String? = null,
		failureReason: FailureReasonType,
		sessionId: String,
		request: HttpServletRequest? = null,
	): IoIdpUserLoginHistory {
		val history = IoIdpUserLoginHistory(
			shoplClientId = shoplClientId,
			shoplUserId = shoplUserId,
			platform = platform,
			loginType = loginType,
			provider = provider,
			result = LoginResult.FAIL,
			failureReason = failureReason,
			sessionId = sessionId,
			ipAddress = extractIpAddress(request),
			userAgent = extractUserAgent(request)
		)

		val savedHistory = loginHistoryRepository.save(history)
		logger.warn(
			"Recorded failed login: userId={}, clientId={}, type={}, reason={}",
			shoplUserId, shoplClientId, loginType, failureReason
		)

		return savedHistory
	}

	/**
	 * 사용자의 로그인 이력 조회
	 */
	@Transactional(readOnly = true)
	fun getUserLoginHistory(
		shoplUserId: String,
		pageable: Pageable,
	): Page<IoIdpUserLoginHistory> {
		return loginHistoryRepository.findByShoplUserIdOrderByRegDtDesc(shoplUserId, pageable)
	}

	/**
	 * 사용자 + 클라이언트별 로그인 이력 조회
	 */
	@Transactional(readOnly = true)
	fun getUserLoginHistory(
		shoplUserId: String,
		shoplClientId: String,
		pageable: Pageable,
	): Page<IoIdpUserLoginHistory> {
		return loginHistoryRepository.findByShoplUserIdAndShoplClientIdOrderByRegDtDesc(
			shoplUserId, shoplClientId, pageable
		)
	}

	/**
	 * 특정 기간 내 사용자의 로그인 이력 조회
	 */
	@Transactional(readOnly = true)
	fun getUserLoginHistory(
		shoplUserId: String,
		startTime: LocalDateTime,
		endTime: LocalDateTime,
		pageable: Pageable,
	): Page<IoIdpUserLoginHistory> {
		return loginHistoryRepository.findByShoplUserIdAndRegDtBetweenOrderByRegDtDesc(
			shoplUserId,
			startTime,
			endTime,
			pageable
		)
	}

	/**
	 * 사용자의 마지막 성공한 로그인 조회
	 */
	@Transactional(readOnly = true)
	fun getLastSuccessfulLogin(shoplUserId: String): IoIdpUserLoginHistory? {
		return loginHistoryRepository.findFirstByShoplUserIdAndResultOrderByRegDtDesc(
			shoplUserId, LoginResult.SUCCESS
		)
	}

	/**
	 * 최근 실패한 로그인 시도 횟수를 조회합니다.
	 *
	 * 보안 목적으로 특정 사용자의 최근 실패 시도 횟수를 확인합니다.
	 * 계정 잠금이나 추가 보안 검증 시 사용됩니다.
	 *
	 * @param shoplUserId 사용자 ID
	 * @param hoursBack 조회할 시간 범위 (시간 단위, 기본값: 24시간)
	 * @return 실패한 로그인 시도 횟수
	 */
	@Transactional(readOnly = true)
	fun getRecentFailedLoginAttempts(shoplUserId: String, hoursBack: Long = 24): Long {
		val since = LocalDateTime.now().minusHours(hoursBack)
		return loginHistoryRepository.countFailedLoginAttempts(shoplUserId, LoginResult.FAIL, since)
	}

	/**
	 * 클라이언트별 로그인 통계를 조회합니다.
	 *
	 * 지정된 기간 동안의 성공/실패 로그인 통계와 성공률을 계산하여 반환합니다.
	 * 대시보드나 모니터링 화면에서 사용됩니다.
	 *
	 * @param shoplClientId 조회할 클라이언트 ID
	 * @param daysBack 조회할 일수 (기본값: 30일)
	 * @return 로그인 통계 정보 (성공/실패 횟수, 총 횟수, 성공률)
	 */
	@Transactional(readOnly = true)
	fun getClientLoginStats(shoplClientId: String, daysBack: Long = 30): LoginStatistics {
		val since = LocalDateTime.now().minusDays(daysBack)
		val stats = loginHistoryRepository.getLoginStatsByClient(shoplClientId, since)

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
			successRate = if (successCount + failCount > 0) {
				(successCount.toDouble() / (successCount + failCount) * 100)
			} else 0.0
		)
	}

	/**
	 * 로그인 타입별 통계 조회
	 */
	@Transactional(readOnly = true)
	fun getLoginTypeStats(shoplClientId: String, daysBack: Long = 30): List<LoginTypeStatistics> {
		val since = LocalDateTime.now().minusDays(daysBack)
		val stats = loginHistoryRepository.getLoginTypeStats(shoplClientId, since)

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
				successRate = if (total > 0) (success.toDouble() / total * 100) else 0.0
			)
		}
	}

	/**
	 * IP 주소별 최근 로그인 시도 횟수를 조회합니다.
	 *
	 * 보안 모니터링 목적으로 특정 IP에서의 로그인 시도 패턴을 분석합니다.
	 * DDoS 공격이나 무차별 대입 공격 탐지에 사용됩니다.
	 *
	 * @param ipAddress 조회할 IP 주소
	 * @param hoursBack 조회할 시간 범위 (시간 단위, 기본값: 1시간)
	 * @return 해당 IP에서의 로그인 시도 총 횟수
	 */
	@Transactional(readOnly = true)
	fun getRecentLoginAttemptsByIp(ipAddress: String, hoursBack: Long = 1): Long {
		val since = LocalDateTime.now().minusHours(hoursBack)
		return loginHistoryRepository.countLoginAttemptsByIp(ipAddress, since)
	}


	/**
	 * HTTP 요청에서 클라이언트의 실제 IP 주소를 추출합니다.
	 *
	 * 프록시나 로드밸런서를 고려하여 다음 순서로 IP 주소를 확인합니다:
	 * 1. X-Forwarded-For 헤더 (첫 번째 IP)
	 * 2. X-Real-IP 헤더
	 * 3. HttpServletRequest.remoteAddr
	 *
	 * @param request HTTP 요청 객체
	 * @return 클라이언트 IP 주소 (null 가능)
	 */
	private fun extractIpAddress(request: HttpServletRequest?): String? {
		if (request == null) return null

		// X-Forwarded-For 헤더 확인 (프록시/로드밸런서 뒤에 있는 경우)
		val xForwardedFor = request.getHeader("X-Forwarded-For")
		if (!xForwardedFor.isNullOrBlank()) {
			return xForwardedFor.split(",")[0].trim()
		}

		// X-Real-IP 헤더 확인
		val xRealIp = request.getHeader("X-Real-IP")
		if (!xRealIp.isNullOrBlank()) {
			return xRealIp
		}

		// 기본 remote address
		return request.remoteAddr
	}

	/**
	 * HTTP 요청에서 User-Agent 정보를 추출합니다.
	 *
	 * @param request HTTP 요청 객체
	 * @return User-Agent 문자열 (null 가능)
	 */
	private fun extractUserAgent(request: HttpServletRequest?): String? {
		return request?.getHeader("User-Agent")
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