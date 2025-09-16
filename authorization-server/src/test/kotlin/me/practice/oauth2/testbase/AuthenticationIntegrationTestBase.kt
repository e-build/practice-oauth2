package me.practice.oauth2.testbase

import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.entity.*
import me.practice.oauth2.domain.IdpClient
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 인증 관련 통합 테스트를 위한 공통 베이스 클래스
 *
 * 주요 기능:
 * - 공통 테스트 상수 제공
 * - 로그인 이력 검증 헬퍼 메서드
 * - 테스트 계정 생성 헬퍼 메서드
 * - Spring Security 테스트 컨텍스트 설정
 */
@SpringBootTest
@TestPropertySource(locations = ["classpath:application-test.yml"])
@Transactional
abstract class AuthenticationIntegrationTestBase {

    companion object {
        const val TEST_CLIENT_ID = "CLIENT001"
        const val TEST_USER_ID = "USER001"
        const val TEST_SESSION_ID = "SESSION001"
        const val TEST_USER_AGENT = "Mozilla/5.0 Test Browser"
        const val TEST_IP_ADDRESS = "127.0.0.1"
    }

    @Autowired
    protected lateinit var loginHistoryRepository: IoIdpLoginHistoryRepository

    @BeforeEach
    open fun setUp() {
        loginHistoryRepository.deleteAll()
    }

    /**
     * 로그인 이력 검증을 위한 헬퍼 메서드
     */
    protected fun assertLoginHistory(
        expectedCount: Int = 1,
        expectedResult: LoginResult,
        expectedFailureReason: FailureReasonType? = null,
        expectedShoplClientId: String = TEST_CLIENT_ID,
        expectedShoplUserId: String = TEST_USER_ID,
        expectedPlatform: IdpClient.Platform = IdpClient.Platform.DASHBOARD,
        expectedLoginType: LoginType = LoginType.BASIC,
        expectedSessionId: String = TEST_SESSION_ID
    ): List<IoIdpUserLoginHistory> {
        val histories = loginHistoryRepository.findAll()
        assertEquals(expectedCount, histories.size, "로그인 이력 개수가 예상과 다릅니다")

        if (expectedCount > 0) {
            val history = histories[0]
            assertEquals(expectedShoplClientId, history.shoplClientId)
            assertEquals(expectedShoplUserId, history.shoplUserId)
            assertEquals(expectedPlatform, history.platform)
            assertEquals(expectedLoginType, history.loginType)
            assertEquals(expectedResult, history.result)
            assertEquals(expectedSessionId, history.sessionId)

            if (expectedFailureReason != null) {
                assertEquals(expectedFailureReason, history.failureReason)
            }

            assertNotNull(history.regDt)
        }

        return histories
    }

    /**
     * Spring Security 요청 컨텍스트 설정
     */
    protected fun setupRequestContext(mockRequest: HttpServletRequest) {
        val requestAttributes = ServletRequestAttributes(mockRequest)
        RequestContextHolder.setRequestAttributes(requestAttributes)
    }

    /**
     * 요청 컨텍스트 정리
     */
    protected fun cleanupRequestContext() {
        RequestContextHolder.resetRequestAttributes()
    }

    /**
     * 테스트 실행 후 자동으로 요청 컨텍스트를 정리하는 헬퍼 메서드
     */
    protected fun <T> withRequestContext(mockRequest: HttpServletRequest, block: () -> T): T {
        setupRequestContext(mockRequest)
        return try {
            block()
        } finally {
            cleanupRequestContext()
        }
    }
}