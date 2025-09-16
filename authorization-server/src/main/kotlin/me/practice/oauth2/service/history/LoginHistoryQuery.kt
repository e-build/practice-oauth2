package me.practice.oauth2.service.history

import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * 로그인 이력 조회 쿼리 데이터 클래스들
 * 단일 책임: 로그인 이력 조회 조건 캡슐화
 */

/**
 * 기본 로그인 이력 조회 조건
 *
 * @property shoplUserId 사용자 ID
 * @property shoplClientId 클라이언트 ID (선택사항)
 * @property pageable 페이징 정보
 */
data class BasicLoginHistoryQuery(
    val shoplUserId: String,
    val shoplClientId: String? = null,
    val pageable: Pageable
)

/**
 * 기간 조건이 포함된 로그인 이력 조회
 *
 * @property shoplUserId 사용자 ID
 * @property shoplClientId 클라이언트 ID (선택사항)
 * @property startTime 조회 시작 시간
 * @property endTime 조회 종료 시간
 * @property pageable 페이징 정보
 */
data class DateRangeLoginHistoryQuery(
    val shoplUserId: String,
    val shoplClientId: String? = null,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val pageable: Pageable
)

/**
 * IP 기반 로그인 시도 조회 조건
 *
 * @property ipAddress IP 주소
 * @property hoursBack 조회할 시간 범위 (시간 단위)
 */
data class IpBasedLoginQuery(
    val ipAddress: String,
    val hoursBack: Long = 1
)

/**
 * 통계 조회 조건
 *
 * @property shoplClientId 클라이언트 ID
 * @property daysBack 조회할 일수
 */
data class StatisticsQuery(
    val shoplClientId: String,
    val daysBack: Long = 30
)