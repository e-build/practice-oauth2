package me.practice.oauth2.client.controller

import me.practice.oauth2.client.exception.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    data class ErrorResponse(
        val success: Boolean = false,
        val error: String,
        val message: String,
        val field: String? = null,
        val timestamp: LocalDateTime = LocalDateTime.now()
    )

    /**
     * SSO 검증 오류 처리
     */
    @ExceptionHandler(SsoValidationException::class)
    fun handleSsoValidationException(ex: SsoValidationException): ResponseEntity<ErrorResponse> {
        logger.warn("SSO 검증 오류: {}", ex.message)

        val errorResponse = ErrorResponse(
            error = "VALIDATION_ERROR",
            message = ex.message ?: "검증 오류가 발생했습니다",
            field = ex.field
        )

        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * SSO 설정을 찾을 수 없는 경우 처리
     */
    @ExceptionHandler(SsoConfigurationNotFoundException::class)
    fun handleSsoConfigurationNotFoundException(ex: SsoConfigurationNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("SSO 설정을 찾을 수 없음: {}", ex.message)

        val errorResponse = ErrorResponse(
            error = "NOT_FOUND",
            message = ex.message ?: "SSO 설정을 찾을 수 없습니다"
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    /**
     * 중복된 SSO 설정 처리
     */
    @ExceptionHandler(DuplicateSsoConfigurationException::class)
    fun handleDuplicateSsoConfigurationException(ex: DuplicateSsoConfigurationException): ResponseEntity<ErrorResponse> {
        logger.warn("중복된 SSO 설정: {}", ex.message)

        val errorResponse = ErrorResponse(
            error = "DUPLICATE_ERROR",
            message = ex.message ?: "중복된 설정이 존재합니다",
            field = ex.field
        )

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    /**
     * SSO 연결 테스트 실패 처리
     */
    @ExceptionHandler(SsoConnectionTestException::class)
    fun handleSsoConnectionTestException(ex: SsoConnectionTestException): ResponseEntity<ErrorResponse> {
        logger.warn("SSO 연결 테스트 실패: {}", ex.message)

        val errorResponse = ErrorResponse(
            error = "CONNECTION_TEST_FAILED",
            message = ex.message ?: "연결 테스트에 실패했습니다"
        )

        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * 일반적인 SSO 설정 오류 처리
     */
    @ExceptionHandler(SsoConfigurationException::class)
    fun handleSsoConfigurationException(ex: SsoConfigurationException): ResponseEntity<ErrorResponse> {
        logger.error("SSO 설정 오류: {}", ex.message, ex)

        val errorResponse = ErrorResponse(
            error = "CONFIGURATION_ERROR",
            message = ex.message ?: "SSO 설정 오류가 발생했습니다"
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("예상치 못한 오류 발생: {}", ex.message, ex)

        val errorResponse = ErrorResponse(
            error = "INTERNAL_SERVER_ERROR",
            message = "서버에서 오류가 발생했습니다"
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    /**
     * 잘못된 인수 처리
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("잘못된 인수: {}", ex.message)

        val errorResponse = ErrorResponse(
            error = "INVALID_ARGUMENT",
            message = ex.message ?: "잘못된 요청입니다"
        )

        return ResponseEntity.badRequest().body(errorResponse)
    }
}