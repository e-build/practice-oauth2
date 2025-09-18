package me.practice.oauth2.service

import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.*
import me.practice.oauth2.service.history.*
import me.practice.oauth2.service.http.HttpRequestInfoExtractor
import me.practice.oauth2.service.platform.PlatformDetectionService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 로그인 이력 관리 서비스 (리팩토링됨)
 *
 * OAuth2 인증 서버에서 사용자의 로그인 이력을 기록하고 조회하는 기능을 제공합니다.
 *
 * 리팩토링 후:
 * - HTTP 요청 정보 추출: HttpRequestInfoExtractor에 위임
 * - 통계 관련 기능: LoginHistoryStatisticsService에 위임
 * - 조회 조건: LoginHistoryQuery 클래스들로 캡슐화
 *
 * @author OAuth2 Team
 * @since 2.0.0 (리팩토링됨)
 */
@Service
@Transactional
class LoginHistoryService(
    private val loginHistoryRepository: IoIdpLoginHistoryRepository,
    private val httpRequestInfoExtractor: HttpRequestInfoExtractor,
    private val statisticsService: LoginHistoryStatisticsService,
    private val platformDetectionService: PlatformDetectionService,
) {

    private val logger = LoggerFactory.getLogger(LoginHistoryService::class.java)

    /**
     * 로그인 성공 이력을 저장합니다.
     *
     * 사용자가 성공적으로 로그인했을 때 호출되어 이력을 데이터베이스에 기록합니다.
     * IP 주소, User-Agent 등의 부가 정보도 함께 저장됩니다.
     * 플랫폼은 HTTP 요청을 분석하여 자동으로 감지됩니다.
     *
     * @param shoplClientId Shopl 클라이언트 ID (nullable - 유효하지 않은 경우 null)
     * @param shoplUserId Shopl 사용자 ID (nullable - 유효하지 않은 경우 null)
     * @param loginType 로그인 타입 (BASIC, SOCIAL, SSO)
     * @param providerType 로그인 제공자 타입 (optional)
     * @param sessionId 세션 ID
     * @param request HTTP 요청 객체 (IP, User-Agent 추출용 및 플랫폼 감지용)
     * @return 저장된 로그인 이력 엔티티
     */
    fun recordSuccessfulLogin(
        shoplClientId: String?,
        shoplUserId: String?,
        loginType: LoginType,
        providerType: ProviderType? = null,
        sessionId: String,
        request: HttpServletRequest? = null,
    ): IoIdpUserLoginHistory {
        val requestSource = httpRequestInfoExtractor.extract(request)
        val detectedPlatform = platformDetectionService.detectPlatform(request)
        val idpClientId = determineIdpClientId(request, shoplClientId)

        val history = IoIdpUserLoginHistory(
            idpClientId = idpClientId,
            shoplClientId = shoplClientId,
            shoplUserId = shoplUserId,
            platform = detectedPlatform,
            loginType = loginType,
            providerType = providerType,
            result = LoginResult.SUCCESS,
            sessionId = sessionId,
            ipAddress = requestSource.ipAddress,
            userAgent = requestSource.userAgent
        )

        val savedHistory = loginHistoryRepository.save(history)
        logger.info(
            "Recorded successful login: userId={}, clientId={}, platform={}, type={}, provider={}",
            shoplUserId, shoplClientId, detectedPlatform, loginType, providerType
        )

        return savedHistory
    }

    /**
     * 로그인 실패 이력을 저장합니다.
     *
     * 사용자의 로그인 시도가 실패했을 때 호출되어 실패 이력과 원인을 데이터베이스에 기록합니다.
     * 보안 모니터링과 분석을 위해 실패 원인도 함께 저장됩니다.
     * 플랫폼은 HTTP 요청을 분석하여 자동으로 감지됩니다.
     *
     * @param shoplClientId Shopl 클라이언트 ID (nullable - 유효하지 않은 경우 null)
     * @param shoplUserId Shopl 사용자 ID (nullable - 유효하지 않은 경우 null)
     * @param loginType 로그인 타입 (BASIC, SOCIAL, SSO)
     * @param providerType 로그인 제공자 타입 (optional)
     * @param failureReason 로그인 실패 원인
     * @param sessionId 세션 ID
     * @param request HTTP 요청 객체 (IP, User-Agent 추출용 및 플랫폼 감지용)
     * @return 저장된 로그인 실패 이력 엔티티
     */
    fun recordFailedLogin(
        shoplClientId: String?,
        shoplUserId: String?,
        loginType: LoginType,
        providerType: ProviderType? = null,
        failureReason: FailureReasonType,
        sessionId: String,
        request: HttpServletRequest? = null,
    ): IoIdpUserLoginHistory {
        val clientInfo = httpRequestInfoExtractor.extract(request)
        val detectedPlatform = platformDetectionService.detectPlatform(request)
        val idpClientId = determineIdpClientId(request, shoplClientId)

        val history = IoIdpUserLoginHistory(
            idpClientId = idpClientId,
            shoplClientId = shoplClientId,
            shoplUserId = shoplUserId,
            platform = detectedPlatform,
            loginType = loginType,
            providerType = providerType,
            result = LoginResult.FAIL,
            failureReason = failureReason,
            sessionId = sessionId,
            ipAddress = clientInfo.ipAddress,
            userAgent = clientInfo.userAgent
        )

        val savedHistory = loginHistoryRepository.save(history)
        logger.warn(
            "Recorded failed login: userId={}, clientId={}, platform={}, type={}, reason={}",
            shoplUserId, shoplClientId, detectedPlatform, loginType, failureReason
        )

        return savedHistory
    }

    // ===== 조회 메서드들 =====

    /**
     * 사용자의 로그인 이력 조회
     */
    @Transactional(readOnly = true)
    fun getUserLoginHistory(query: BasicLoginHistoryQuery): Page<IoIdpUserLoginHistory> {
        return if (query.shoplClientId != null) {
            loginHistoryRepository.findByShoplUserIdAndShoplClientIdOrderByRegDtDesc(
                query.shoplUserId, query.shoplClientId, query.pageable
            )
        } else {
            loginHistoryRepository.findByShoplUserIdOrderByRegDtDesc(
                query.shoplUserId, query.pageable
            )
        }
    }

    /**
     * 특정 기간 내 사용자의 로그인 이력 조회
     */
    @Transactional(readOnly = true)
    fun getUserLoginHistory(query: DateRangeLoginHistoryQuery): Page<IoIdpUserLoginHistory> {
        return loginHistoryRepository.findByShoplUserIdAndRegDtBetweenOrderByRegDtDesc(
            query.shoplUserId,
            query.startTime,
            query.endTime,
            query.pageable
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

    // ===== 통계 관련 메서드들 (StatisticsService에 위임) =====

    /**
     * 최근 실패한 로그인 시도 횟수를 조회합니다.
     */
    @Transactional(readOnly = true)
    fun getRecentFailedLoginAttempts(
		shoplUserId: String,
		minutesBack: Long,
	): Long {
        return statisticsService.getRecentFailedLoginAttempts(
			shoplUserId = shoplUserId,
			minutesBack = minutesBack
		)
    }

    /**
     * 클라이언트별 로그인 통계를 조회합니다.
     */
    @Transactional(readOnly = true)
    fun getClientLoginStats(shoplClientId: String, daysBack: Long = 30): LoginStatistics {
        val query = StatisticsQuery(shoplClientId, daysBack)
        return statisticsService.getClientLoginStats(query)
    }

    /**
     * 로그인 타입별 통계 조회
     */
    @Transactional(readOnly = true)
    fun getLoginTypeStats(shoplClientId: String, daysBack: Long = 30): List<LoginTypeStatistics> {
        val query = StatisticsQuery(shoplClientId, daysBack)
        return statisticsService.getLoginTypeStats(query)
    }

    /**
     * IP 주소별 최근 로그인 시도 횟수를 조회합니다.
     */
    @Transactional(readOnly = true)
    fun getRecentLoginAttemptsByIp(ipAddress: String, hoursBack: Long = 1): Long {
        val query = IpBasedLoginQuery(ipAddress, hoursBack)
        return statisticsService.getRecentLoginAttemptsByIp(query)
    }

    // ===== 편의 메서드들 (기존 API 호환성 유지) =====

    /**
     * 사용자의 로그인 이력 조회 (기존 메서드 호환성 유지)
     */
    @Transactional(readOnly = true)
    fun getUserLoginHistory(
        shoplUserId: String,
        pageable: Pageable,
    ): Page<IoIdpUserLoginHistory> {
        return getUserLoginHistory(BasicLoginHistoryQuery(shoplUserId, pageable = pageable))
    }

    /**
     * 사용자 + 클라이언트별 로그인 이력 조회 (기존 메서드 호환성 유지)
     */
    @Transactional(readOnly = true)
    fun getUserLoginHistory(
        shoplUserId: String,
        shoplClientId: String,
        pageable: Pageable,
    ): Page<IoIdpUserLoginHistory> {
        return getUserLoginHistory(BasicLoginHistoryQuery(shoplUserId, shoplClientId, pageable))
    }

    /**
     * 특정 기간 내 사용자의 로그인 이력 조회 (기존 메서드 호환성 유지)
     */
    @Transactional(readOnly = true)
    fun getUserLoginHistory(
        shoplUserId: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageable: Pageable,
    ): Page<IoIdpUserLoginHistory> {
        return getUserLoginHistory(DateRangeLoginHistoryQuery(shoplUserId, null, startTime, endTime, pageable))
    }

    // ===== 유틸리티 메서드들 =====

    /**
     * IDP 클라이언트 ID를 결정합니다.
     *
     * 다음 우선순위로 클라이언트 ID를 결정합니다:
     * 1. 유효한 shopl 클라이언트 ID가 있는 경우
     * 2. HTTP 요청에서 client_id 파라미터 추출
     * 3. 세션에서 클라이언트 정보 추출
     * 4. 기본값: "SYSTEM"
     *
     * @param request HTTP 요청 객체
     * @param shoplClientId Shopl 클라이언트 ID
     * @return 결정된 IDP 클라이언트 ID
     */
    private fun determineIdpClientId(request: HttpServletRequest?, shoplClientId: String?): String {
        return when {
            // 1. 유효한 shopl 클라이언트 ID가 있는 경우
            !shoplClientId.isNullOrBlank() && shoplClientId != "UNKNOWN" -> shoplClientId

            // 2. HTTP 요청에서 client_id 파라미터 추출
            request?.getParameter("client_id")?.takeIf { it.isNotBlank() } != null ->
                request.getParameter("client_id")

            // 3. 세션에서 클라이언트 정보 추출
            else -> request?.session?.getAttribute("client_id") as? String ?: "SYSTEM"
        }
    }
}