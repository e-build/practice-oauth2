package me.practice.oauth2.entity

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface IoIdpLoginHistoryRepository : JpaRepository<IoIdpUserLoginHistory, Long> {

    /**
     * 특정 사용자의 로그인 이력 조회 (페이징)
     */
    fun findByShoplUserIdOrderByRegDtDesc(
        shoplUserId: String,
        pageable: Pageable
    ): Page<IoIdpUserLoginHistory>

    /**
     * 특정 사용자 + 클라이언트의 로그인 이력 조회
     */
    fun findByShoplUserIdAndShoplClientIdOrderByRegDtDesc(
        shoplUserId: String,
        shoplClientId: String,
        pageable: Pageable
    ): Page<IoIdpUserLoginHistory>

    /**
     * 특정 기간 내 사용자의 로그인 이력 조회
     */
    fun findByShoplUserIdAndRegDtBetweenOrderByRegDtDesc(
        shoplUserId: String,
        startTime: Instant,
        endTime: Instant,
        pageable: Pageable
    ): Page<IoIdpUserLoginHistory>

    /**
     * 특정 사용자의 최근 성공한 로그인 조회
     */
    fun findFirstByShoplUserIdAndResultOrderByRegDtDesc(
        shoplUserId: String,
        result: LoginResult
    ): IoIdpUserLoginHistory?

    /**
     * 특정 사용자의 실패한 로그인 시도 횟수 (특정 기간 내)
     */
    @Query(
		"""
        SELECT COUNT(h) FROM IoIdpUserLoginHistory h 
        WHERE h.shoplUserId = :shoplUserId 
        AND h.result = :result 
        AND h.regDt >= :since
    """
	)
    fun countFailedLoginAttempts(
        @Param("shoplUserId") shoplUserId: String,
        @Param("result") result: LoginResult,
        @Param("since") since: Instant
    ): Long

    /**
     * 클라이언트별 로그인 통계 (성공/실패 비율)
     */
    @Query(
		"""
        SELECT h.result, COUNT(h) FROM IoIdpUserLoginHistory h 
        WHERE h.shoplClientId = :shoplClientId 
        AND h.regDt >= :since 
        GROUP BY h.result
    """
	)
    fun getLoginStatsByClient(
        @Param("shoplClientId") shoplClientId: String,
        @Param("since") since: Instant
    ): List<Array<Any>>

    /**
     * 로그인 타입별 통계
     */
    @Query(
		"""
        SELECT h.loginType, h.result, COUNT(h) FROM IoIdpUserLoginHistory h 
        WHERE h.shoplClientId = :shoplClientId 
        AND h.regDt >= :since 
        GROUP BY h.loginType, h.result
    """
	)
    fun getLoginTypeStats(
        @Param("shoplClientId") shoplClientId: String,
        @Param("since") since: Instant
    ): List<Array<Any>>

    /**
     * 특정 IP에서의 로그인 시도 횟수 (보안 목적)
     */
    @Query(
		"""
        SELECT COUNT(h) FROM IoIdpUserLoginHistory h 
        WHERE h.ipAddress = :ipAddress 
        AND h.regDt >= :since
    """
	)
    fun countLoginAttemptsByIp(
        @Param("ipAddress") ipAddress: String,
        @Param("since") since: Instant
    ): Long

}