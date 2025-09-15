package me.practice.oauth2.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "최근 실패 로그인 시도 응답")
data class RecentFailuresResponse(
    @Schema(description = "사용자 ID", example = "user001")
    val userId: String,
    @Schema(description = "실패한 로그인 시도 횟수", example = "3")
    val failedAttempts: Long,
    @Schema(description = "조회 기간 (시간)", example = "24")
    val hoursBack: Long
)

@Schema(description = "IP별 로그인 시도 응답")
data class IpAttemptsResponse(
    @Schema(description = "IP 주소", example = "192.168.1.10")
    val ipAddress: String,
    @Schema(description = "로그인 시도 횟수", example = "5")
    val attempts: Long,
    @Schema(description = "조회 기간 (시간)", example = "1")
    val hoursBack: Long
)