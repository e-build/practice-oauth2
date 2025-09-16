package me.practice.oauth2.exception

import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.domain.IdpClient
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Component
import java.net.ConnectException
import java.sql.SQLException

/**
 * 전역 인증 예외 처리기
 * 시스템 레벨의 인증 관련 예외를 처리하고 로그인 실패 이력을 기록
 */
@Component
class GlobalAuthenticationExceptionHandler(
    private val loginHistoryService: LoginHistoryService
) {

    private val logger = LoggerFactory.getLogger(GlobalAuthenticationExceptionHandler::class.java)

    /**
     * 시스템 예외를 처리하고 로그인 실패 이력을 기록
     */
    fun handleSystemException(
        exception: Exception,
        request: HttpServletRequest?,
        shoplClientId: String = "UNKNOWN",
        shoplUserId: String = "unknown"
    ) {
        val failureReason = mapExceptionToFailureReason(exception)

        try {
            val sessionId = request?.session?.id ?: "unknown-session"

            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = IdpClient.Platform.DASHBOARD,
                loginType = determineLoginType(request),
                failureReason = failureReason,
                sessionId = sessionId,
                request = request
            )

            logger.warn("Recorded system authentication failure: reason=$failureReason, exception=${exception.javaClass.simpleName}")
        } catch (e: Exception) {
            logger.error("Failed to record system authentication failure", e)
        }
    }

    /**
     * 예외를 실패 사유로 매핑
     */
    private fun mapExceptionToFailureReason(exception: Exception): FailureReasonType {
        return when (exception) {
            // 데이터베이스 관련 오류
            is DataAccessException,
            is SQLException -> FailureReasonType.SYSTEM_ERROR

            // 네트워크 연결 오류
            is ConnectException,
            is java.net.SocketTimeoutException,
            is java.net.UnknownHostException -> FailureReasonType.NETWORK_ERROR

            // 외부 서비스 오류
            is org.springframework.web.client.RestClientException -> FailureReasonType.EXTERNAL_PROVIDER_ERROR

            // 시스템 리소스 관련 오류
            is OutOfMemoryError,
            is StackOverflowError -> FailureReasonType.SYSTEM_ERROR

            // 보안 관련 예외
            is SecurityException -> FailureReasonType.SYSTEM_ERROR

            // 기타 모든 예외
            else -> FailureReasonType.UNKNOWN
        }
    }

    /**
     * 요청을 기반으로 로그인 타입 결정
     */
    private fun determineLoginType(request: HttpServletRequest?): LoginType {
        if (request == null) return LoginType.BASIC

        val requestUri = request.requestURI
        return when {
            requestUri.contains("/oauth2/") -> LoginType.SOCIAL
            requestUri.contains("/sso/") -> LoginType.SSO
            else -> LoginType.BASIC
        }
    }

    /**
     * 인증 예외인지 확인
     */
    fun isAuthenticationRelated(exception: Exception): Boolean {
        return when (exception) {
            is AuthenticationException,
            is DataAccessException,
            is SQLException,
            is SecurityException -> true
            else -> false
        }
    }
}