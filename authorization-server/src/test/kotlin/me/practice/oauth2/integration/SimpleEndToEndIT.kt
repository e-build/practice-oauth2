package me.practice.oauth2.integration

import me.practice.oauth2.entity.*
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.testbase.IntegrationTestBase
import me.practice.oauth2.service.LoginHistoryService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.*

@Import(LoginHistoryService::class)
class SimpleEndToEndIT(
    private val loginHistoryService: LoginHistoryService,
    private val loginHistoryRepository: IoIdpLoginHistoryRepository
) : IntegrationTestBase() {

    @BeforeEach
    override fun setUp() {
        super.setUp()
        loginHistoryRepository.deleteAll()
    }

    @Test
    @DisplayName("End-to-End: 로그인 이력 기록부터 조회까지 전체 플로우")
    fun endToEndFlow_RecordAndRetrieveLoginHistory() {
        // Given: 다양한 로그인 이력 기록
        val clientId = "TEST_CLIENT"
        val userId1 = "USER001"
        val userId2 = "USER002"

        // When: 성공 로그인 기록
        val successHistory = loginHistoryService.recordSuccessfulLogin(
            shoplClientId = clientId,
            shoplUserId = userId1,
            platform = IdpClient.Platform.DASHBOARD,
            loginType = LoginType.BASIC,
            provider = null,
            sessionId = "SUCCESS_SESSION"
        )

        // 실패 로그인 기록
        val failureHistory = loginHistoryService.recordFailedLogin(
            shoplClientId = clientId,
            shoplUserId = userId2,
            platform = IdpClient.Platform.DASHBOARD,
            loginType = LoginType.SOCIAL,
            provider = "GOOGLE",
            failureReason = FailureReasonType.INVALID_CREDENTIALS,
            sessionId = "FAILURE_SESSION"
        )

        // Then: 기록된 데이터 검증
        assertNotNull(successHistory.id)
        assertEquals(LoginResult.SUCCESS, successHistory.result)
        assertNull(successHistory.failureReason)

        assertNotNull(failureHistory.id)
        assertEquals(LoginResult.FAIL, failureHistory.result)
        assertEquals(FailureReasonType.INVALID_CREDENTIALS, failureHistory.failureReason)

        // 전체 이력 조회
        val allHistories = loginHistoryRepository.findAll()
        assertEquals(2, allHistories.size)

        // 통계 조회
        val stats = loginHistoryService.getClientLoginStats(clientId, 1L)
        assertEquals(2L, stats.totalCount)
        assertEquals(1L, stats.successCount)
        assertEquals(1L, stats.failCount)
        assertEquals(50.0, stats.successRate)
    }

    @Test
    @DisplayName("대용량 데이터 성능 테스트 - 100건 삽입 및 조회")
    fun performanceTest_BatchInsertAndQuery() {
        // Given: 100건의 데이터 준비
        val totalRecords = 100
        val clientId = "PERF_CLIENT"

        val startTime = System.currentTimeMillis()

        // When: 배치로 데이터 삽입
        for (i in 1..totalRecords) {
            loginHistoryService.recordSuccessfulLogin(
                shoplClientId = clientId,
                shoplUserId = "USER${i % 10}",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.values()[i % 3],
                provider = if (i % 2 == 0) "GOOGLE" else null,
                sessionId = "SESSION_$i"
            )
        }

        val insertTime = System.currentTimeMillis() - startTime
        println("Insert time for $totalRecords records: ${insertTime}ms")

        // Then: 조회 성능 테스트
        val queryStartTime = System.currentTimeMillis()

        val stats = loginHistoryService.getClientLoginStats(clientId, 1L)
        assertEquals(totalRecords.toLong(), stats.totalCount)

        val typeStats = loginHistoryService.getLoginTypeStats(clientId, 1L)
        assertTrue(typeStats.isNotEmpty())

        val queryTime = System.currentTimeMillis() - queryStartTime
        println("Query time: ${queryTime}ms")

        // 성능 검증 (1초 이내)
        assertTrue(insertTime + queryTime < 1000, "Total time should be under 1 second")
    }
}