package me.practice.oauth2.client.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.LocalDateTime

@ControllerAdvice
class JwtExceptionHandler {

	/**
	 * JWT 토큰이 유효하지 않은 경우
	 */
	@ExceptionHandler(JwtException::class)
	fun handleJwtException(ex: JwtException): ResponseEntity<ErrorResponse> {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ErrorResponse(
				code = "INVALID_TOKEN",
				message = "유효하지 않은 토큰입니다.",
				timestamp = LocalDateTime.now()
			))
	}

	/**
	 * JWT 토큰이 만료된 경우
	 */
//	@ExceptionHandler(JwtExpiredTokenException::class)
//	fun handleJwtExpiredTokenException(ex: JwtExpiredTokenException): ResponseEntity<ErrorResponse> {
//		log.warn("JWT Expired: {}", ex.message)
//		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//			.body(ErrorResponse(
//				code = "TOKEN_EXPIRED",
//				message = "토큰이 만료되었습니다.",
//				timestamp = LocalDateTime.now()
//			))
//	}

	/**
	 * JWT 토큰 검증 실패
	 */
	@ExceptionHandler(JwtValidationException::class)
	fun handleJwtValidationException(ex: JwtValidationException): ResponseEntity<ErrorResponse> {
//		log.warn("JWT Validation failed: {}", ex.message)
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ErrorResponse(
				code = "TOKEN_VALIDATION_FAILED",
				message = "토큰 검증에 실패했습니다.",
				timestamp = LocalDateTime.now()
			))
	}

	/**
	 * 인증되지 않은 접근
	 */
	@ExceptionHandler(AuthenticationException::class)
	fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
//		log.warn("Authentication failed: {}", ex.message)
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ErrorResponse(
				code = "AUTHENTICATION_FAILED",
				message = "인증에 실패했습니다.",
				timestamp = LocalDateTime.now()
			))
	}

	/**
	 * 권한이 없는 접근
	 */
	@ExceptionHandler(AccessDeniedException::class)
	fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
//		log.warn("Access denied: {}", ex.message)
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(ErrorResponse(
				code = "ACCESS_DENIED",
				message = "접근 권한이 없습니다.",
				timestamp = LocalDateTime.now()
			))
	}

	/**
	 * 정적 리소스를 찾을 수 없는 경우
	 */
	@ExceptionHandler(NoResourceFoundException::class)
	fun handleNoResourceFoundException(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ErrorResponse(
				code = "RESOURCE_NOT_FOUND",
				message = "요청한 리소스를 찾을 수 없습니다.",
				timestamp = LocalDateTime.now()
			))
	}

	/**
	 * 일반적인 예외 처리 (보안 관련 예외만)
	 */
	@ExceptionHandler(RuntimeException::class)
	fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
		// 보안 관련 예외가 아닌 경우 다시 던지기
		if (ex !is AuthenticationException &&
			ex !is AccessDeniedException &&
			ex !is JwtException) {
			throw ex
		}

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ErrorResponse(
				code = "INTERNAL_SERVER_ERROR",
				message = "서버 오류가 발생했습니다.",
				timestamp = LocalDateTime.now()
			))
	}
}

/**
 * 에러 응답 DTO
 */
data class ErrorResponse(
	val code: String,
	val message: String,
	val timestamp: LocalDateTime
)