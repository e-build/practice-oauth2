package me.practice.oauth2.service

import me.practice.oauth2.entity.*
import me.practice.oauth2.domain.IdpClient
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import jakarta.servlet.http.HttpServletRequest
import java.time.LocalDateTime

@Service
@Transactional
class LoginHistoryService(
    private val loginHistoryRepository: IoIdpLoginHistoryRepository
) {

    private val logger = LoggerFactory.getLogger(LoginHistoryService::class.java)

    /**
     * 로그인 성공 이력 저장
     */
    fun recordSuccessfulLogin(
        shoplClientId: String,
        shoplUserId: String,
        platform: IdpClient.Platform,
        loginType: LoginType,
        provider: String? = null,
        sessionId: String,
        request: HttpServletRequest? = null
    ): IoIdpLoginHistory {
        val history = IoIdpLoginHistory(
            shoplClientId = shoplClientId,
            shoplUserId = shoplUserId,
            platform = platform,
            loginType = loginType,
            provider = provider,
            result = LoginResult.SUCCESS,
            sessionId = sessionId,
            ipAddress = extractIpAddress(request),
            userAgent = extractUserAgent(request),
            location = extractLocation(request)
        )

        val savedHistory = loginHistoryRepository.save(history)
        logger.info("Recorded successful login: userId={}, clientId={}, type={}, provider={}", 
            shoplUserId, shoplClientId, loginType, provider)
        
        return savedHistory
    }

    /**
     * 로그인 실패 이력 저장
     */
    fun recordFailedLogin(
        shoplClientId: String,
        shoplUserId: String,
        platform: IdpClient.Platform,
        loginType: LoginType,
        provider: String? = null,
        failureReason: FailureReasonType,
        sessionId: String,
        request: HttpServletRequest? = null
    ): IoIdpLoginHistory {
        val history = IoIdpLoginHistory(
            shoplClientId = shoplClientId,
            shoplUserId = shoplUserId,
            platform = platform,
            loginType = loginType,
            provider = provider,
            result = LoginResult.FAIL,
            failureReason = failureReason,
            sessionId = sessionId,
            ipAddress = extractIpAddress(request),
            userAgent = extractUserAgent(request),
            location = extractLocation(request)
        )

        val savedHistory = loginHistoryRepository.save(history)
        logger.warn("Recorded failed login: userId={}, clientId={}, type={}, reason={}", 
            shoplUserId, shoplClientId, loginType, failureReason)
        
        return savedHistory
    }

    /**
     * 사용자의 로그인 이력 조회
     */
    @Transactional(readOnly = true)
    fun getUserLoginHistory(
        shoplUserId: String,
        pageable: Pageable
    ): Page<IoIdpLoginHistory> {
        return loginHistoryRepository.findByShoplUserIdOrderByLoginTimeDesc(shoplUserId, pageable)
    }

    /**
     * 사용자 + 클라이언트별 로그인 이력 조회
     */
    @Transactional(readOnly = true)
    fun getUserLoginHistory(
        shoplUserId: String,
        shoplClientId: String,
        pageable: Pageable
    ): Page<IoIdpLoginHistory> {
        return loginHistoryRepository.findByShoplUserIdAndShoplClientIdOrderByLoginTimeDesc(
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
        pageable: Pageable
    ): Page<IoIdpLoginHistory> {
        return loginHistoryRepository.findByShoplUserIdAndLoginTimeBetweenOrderByLoginTimeDesc(
            shoplUserId, startTime, endTime, pageable
        )
    }

    /**
     * 사용자의 마지막 성공한 로그인 조회
     */
    @Transactional(readOnly = true)
    fun getLastSuccessfulLogin(shoplUserId: String): IoIdpLoginHistory? {
        return loginHistoryRepository.findFirstByShoplUserIdAndResultOrderByLoginTimeDesc(
            shoplUserId, LoginResult.SUCCESS
        )
    }

    /**
     * 최근 실패한 로그인 시도 횟수 조회 (보안 목적)
     */
    @Transactional(readOnly = true)
    fun getRecentFailedLoginAttempts(shoplUserId: String, hoursBack: Long = 24): Long {
        val since = LocalDateTime.now().minusHours(hoursBack)
        return loginHistoryRepository.countFailedLoginAttempts(shoplUserId, LoginResult.FAIL, since)
    }

    /**
     * 클라이언트별 로그인 통계 조회
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
     * IP 주소별 최근 로그인 시도 횟수 (보안 목적)
     */
    @Transactional(readOnly = true)
    fun getRecentLoginAttemptsByIp(ipAddress: String, hoursBack: Long = 1): Long {
        val since = LocalDateTime.now().minusHours(hoursBack)
        return loginHistoryRepository.countLoginAttemptsByIp(ipAddress, since)
    }

    /**
     * 사용자의 로그인 위치 기록 조회
     */
    @Transactional(readOnly = true)
    fun getUserLoginLocations(shoplUserId: String): List<String> {
        return loginHistoryRepository.getDistinctLoginLocations(shoplUserId, LoginResult.SUCCESS)
    }

    // Private helper methods
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

    private fun extractUserAgent(request: HttpServletRequest?): String? {
        return request?.getHeader("User-Agent")
    }

    private fun extractLocation(request: HttpServletRequest?): String? {
        // 실제 구현에서는 IP 기반 지리 정보 조회 서비스를 사용
        // 예: MaxMind GeoIP, IP2Location 등
        // 여기서는 간단히 null 반환
        return null
    }
}

data class LoginStatistics(
    val successCount: Long,
    val failCount: Long,
    val totalCount: Long,
    val successRate: Double
)

data class LoginTypeStatistics(
    val loginType: LoginType,
    val successCount: Long,
    val failCount: Long,
    val totalCount: Long,
    val successRate: Double
)