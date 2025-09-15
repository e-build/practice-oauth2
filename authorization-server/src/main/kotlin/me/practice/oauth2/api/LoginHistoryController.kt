package me.practice.oauth2.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import me.practice.oauth2.dto.IpAttemptsResponse
import me.practice.oauth2.dto.RecentFailuresResponse
import me.practice.oauth2.entity.IoIdpUserLoginHistory
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.history.LoginStatistics
import me.practice.oauth2.service.history.LoginTypeStatistics
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@Tag(name = "로그인 이력 관리", description = "사용자 로그인 이력 조회 및 통계 관리 API")
@RestController
@RequestMapping("/api/login-history")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "oauth2", scopes = ["read"])
class LoginHistoryController(
	private val loginHistoryService: LoginHistoryService,
) {

	@Operation(
		summary = "사용자별 로그인 이력 조회",
		description = "특정 사용자의 로그인 이력을 페이징 형태로 조회합니다. 클라이언트 ID가 제공되면 해당 클라이언트에서의 로그인 이력만 조회됩니다."
	)
	@ApiResponses(
		value = [ApiResponse(responseCode = "200", description = "로그인 이력 조회 성공"), ApiResponse(
			responseCode = "400",
			description = "잘못된 요청 매개변수"
		), ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")]
	)
	@GetMapping("/users/{userId}")
	fun getUserLoginHistory(
		@Parameter(description = "조회할 사용자 ID", required = true, example = "user001") @PathVariable userId: String,
		@Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") page: Int,
		@Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") size: Int,
		@Parameter(
			description = "클라이언트 ID (선택적)",
			example = "SHOPL001"
		) @RequestParam(required = false) clientId: String?,
	): ResponseEntity<Page<IoIdpUserLoginHistory>> {
		val pageable = PageRequest.of(page, size, Sort.by("regDt").descending())

		val result = if (clientId != null) {
			loginHistoryService.getUserLoginHistory(userId, clientId, pageable)
		} else {
			loginHistoryService.getUserLoginHistory(userId, pageable)
		}

		return ResponseEntity.ok(result)
	}

	@Operation(
		summary = "특정 기간 내 사용자 로그인 이력 조회", description = "특정 사용자의 지정된 기간 내 로그인 이력을 조회합니다."
	)
	@GetMapping("/users/{userId}/period")
	fun getUserLoginHistoryByPeriod(
		@Parameter(description = "사용자 ID", required = true, example = "user001") @PathVariable userId: String,
		@Parameter(
			description = "조회 시작 시간",
			required = true,
			example = "2024-01-15T00:00:00"
		) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: LocalDateTime,
		@Parameter(
			description = "조회 종료 시간",
			required = true,
			example = "2024-01-18T23:59:59"
		) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: LocalDateTime,
		@Parameter(description = "페이지 번호", example = "0") @RequestParam(defaultValue = "0") page: Int,
		@Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") size: Int,
	): ResponseEntity<Page<IoIdpUserLoginHistory>> {
		val pageable = PageRequest.of(page, size, Sort.by("regDt").descending())
		val result = loginHistoryService.getUserLoginHistory(userId, startTime, endTime, pageable)
		return ResponseEntity.ok(result)
	}

	@Operation(
		summary = "사용자의 마지막 성공 로그인 조회", description = "특정 사용자의 가장 최근 성공한 로그인 기록을 조회합니다."
	)
	@ApiResponses(
		value = [ApiResponse(responseCode = "200", description = "마지막 성공 로그인 조회 성공"), ApiResponse(
			responseCode = "204",
			description = "성공한 로그인 기록 없음"
		)]
	)
	@GetMapping("/users/{userId}/last-success")
	fun getLastSuccessfulLogin(
		@Parameter(description = "사용자 ID", required = true, example = "user001") @PathVariable userId: String,
	): ResponseEntity<IoIdpUserLoginHistory?> {
		val result = loginHistoryService.getLastSuccessfulLogin(userId)
		return if (result != null) {
			ResponseEntity.ok(result)
		} else {
			ResponseEntity.noContent().build()
		}
	}

	@Operation(
		summary = "사용자의 최근 실패한 로그인 시도 횟수 조회", description = "특정 사용자의 최근 N시간 내 실패한 로그인 시도 횟수를 조회합니다."
	)
	@GetMapping("/users/{userId}/recent-failures")
	fun getRecentFailedLoginAttempts(
		@Parameter(description = "사용자 ID", required = true, example = "user002") @PathVariable userId: String,
		@Parameter(description = "조회할 시간 범위 (시간)", example = "24") @RequestParam(defaultValue = "24") hoursBack: Long,
	): ResponseEntity<RecentFailuresResponse> {
		val count = loginHistoryService.getRecentFailedLoginAttempts(userId, hoursBack)
		return ResponseEntity.ok(
			RecentFailuresResponse(
				userId = userId, failedAttempts = count, hoursBack = hoursBack
			)
		)
	}

	@Operation(
		summary = "클라이언트별 로그인 통계 조회", description = "특정 클라이언트의 로그인 성공/실패 통계를 조회합니다."
	)
	@ApiResponses(
		value = [ApiResponse(responseCode = "200", description = "통계 조회 성공"), ApiResponse(
			responseCode = "404",
			description = "클라이언트를 찾을 수 없음"
		)]
	)
	@GetMapping("/clients/{clientId}/statistics")
	fun getClientLoginStats(
		@Parameter(description = "클라이언트 ID", required = true, example = "SHOPL001") @PathVariable clientId: String,
		@Parameter(description = "조회할 일수 (과거 N일)", example = "30") @RequestParam(defaultValue = "30") daysBack: Long,
	): ResponseEntity<LoginStatistics> {
		val stats = loginHistoryService.getClientLoginStats(clientId, daysBack)
		return ResponseEntity.ok(stats)
	}

	@Operation(
		summary = "클라이언트별 로그인 타입 통계 조회", description = "특정 클라이언트의 로그인 타입별(BASIC, SOCIAL, SSO) 통계를 조회합니다."
	)
	@GetMapping("/clients/{clientId}/type-statistics")
	fun getLoginTypeStats(
		@Parameter(description = "클라이언트 ID", required = true, example = "SHOPL001") @PathVariable clientId: String,
		@Parameter(description = "조회할 일수 (과거 N일)", example = "30") @RequestParam(defaultValue = "30") daysBack: Long,
	): ResponseEntity<List<LoginTypeStatistics>> {
		val stats = loginHistoryService.getLoginTypeStats(clientId, daysBack)
		return ResponseEntity.ok(stats)
	}


	@Operation(
		summary = "IP별 최근 로그인 시도 횟수 조회", description = "특정 IP 주소에서의 최근 N시간 내 로그인 시도 횟수를 조회합니다. (보안 모니터링 목적)"
	)
	@GetMapping("/security/ip/{ipAddress}/attempts")
	fun getRecentLoginAttemptsByIp(
		@Parameter(description = "IP 주소", required = true, example = "192.168.1.10") @PathVariable ipAddress: String,
		@Parameter(description = "조회할 시간 범위 (시간)", example = "1") @RequestParam(defaultValue = "1") hoursBack: Long,
	): ResponseEntity<IpAttemptsResponse> {
		val count = loginHistoryService.getRecentLoginAttemptsByIp(ipAddress, hoursBack)
		return ResponseEntity.ok(
			IpAttemptsResponse(
				ipAddress = ipAddress, attempts = count, hoursBack = hoursBack
			)
		)
	}
}

@Tag(name = "테스트", description = "기본 테스트 API")
@RestController
@RequestMapping("/api/test")
class TestController {

	@Operation(summary = "헬스체크", description = "서버 상태를 확인합니다.")
	@GetMapping("/health")
	fun health(): ResponseEntity<Map<String, String>> {
		return ResponseEntity.ok(mapOf("status" to "OK"))
	}
}