package me.practice.oauth2.client.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
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
	 * 일반적인 예외 처리
	 */
	@ExceptionHandler(Exception::class)
	fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
//		log.error("Unexpected error: ", ex)
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