package me.practice.oauth2.service

import me.practice.oauth2.entity.*
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.testbase.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import kotlin.test.*

@Import(LoginHistoryService::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LoginHistoryServiceIT(
    private val sut: LoginHistoryService,
    private val loginHistoryRepository: IoIdpLoginHistoryRepository,
) : IntegrationTestBase() {

    @BeforeEach
    override fun setUp() {
        super.setUp()
        // 각 테스트 전에 기존 데이터 정리
        loginHistoryRepository.deleteAll()
    }

    @Test
    @DisplayName("성공한 로그인 이력 저장")
    fun testRecordSuccessfulLogin() {
        // Given
        val shoplClientId = TEST_CLIENT_ID
        val shoplUserId = TEST_USER_ID
        val platform = IdpClient.Platform.DASHBOARD
        val loginType = LoginType.BASIC
        val sessionId = TEST_SESSION_ID

        // When
        val savedHistory = sut.recordSuccessfulLogin(
            shoplClientId = shoplClientId,
            shoplUserId = shoplUserId,
            platform = platform,
            loginType = loginType,
            sessionId = sessionId
        )

        // Then
        assertNotNull(savedHistory.id)
        assertEquals(shoplClientId, savedHistory.shoplClientId)
        assertEquals(shoplUserId, savedHistory.shoplUserId)
        assertEquals(platform, savedHistory.platform)
        assertEquals(loginType, savedHistory.loginType)
        assertEquals(LoginResult.SUCCESS, savedHistory.result)
        assertEquals(sessionId, savedHistory.sessionId)
        assertNull(savedHistory.failureReason)
        assertNotNull(savedHistory.loginTime)

        // DB에서 직접 확인
        val dbHistory = loginHistoryRepository.findById(savedHistory.id!!)
        assertTrue(dbHistory.isPresent)
        assertEquals(LoginResult.SUCCESS, dbHistory.get().result)
    }

    @Test
    @DisplayName("실패한 로그인 이력 저장")
    fun testRecordFailedLogin() {
        // Given
        val shoplClientId = TEST_CLIENT_ID
        val shoplUserId = TEST_USER_ID
        val platform = IdpClient.Platform.APP
        val loginType = LoginType.SOCIAL
        val provider = "GOOGLE"
        val failureReason = FailureReasonType.INVALID_CREDENTIALS
        val sessionId = TEST_SESSION_ID

        // When
        val savedHistory = sut.recordFailedLogin(
            shoplClientId = shoplClientId,
            shoplUserId = shoplUserId,
            platform = platform,
            loginType = loginType,
            provider = provider,
            failureReason = failureReason,
            sessionId = sessionId
        )

        // Then
        assertNotNull(savedHistory.id)
        assertEquals(shoplClientId, savedHistory.shoplClientId)
        assertEquals(shoplUserId, savedHistory.shoplUserId)
        assertEquals(platform, savedHistory.platform)
        assertEquals(loginType, savedHistory.loginType)
        assertEquals(provider, savedHistory.provider)
        assertEquals(LoginResult.FAIL, savedHistory.result)
        assertEquals(failureReason, savedHistory.failureReason)
        assertEquals(sessionId, savedHistory.sessionId)

        // DB에서 직접 확인
        val dbHistory = loginHistoryRepository.findById(savedHistory.id!!)
        assertTrue(dbHistory.isPresent)
        assertEquals(LoginResult.FAIL, dbHistory.get().result)
        assertEquals(failureReason, dbHistory.get().failureReason)
    }

    @Test
    @DisplayName("사용자별 로그인 이력 조회")
    fun testGetUserLoginHistory() {
        // Given
        val userId = TEST_USER_ID
        val clientId = TEST_CLIENT_ID
        
        // 테스트 데이터 생성
        repeat(5) { i ->
            sut.recordSuccessfulLogin(
                shoplClientId = clientId,
                shoplUserId = userId,
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                sessionId = "SESSION_$i"
            )
        }
        
        repeat(3) { i ->
            sut.recordFailedLogin(
                shoplClientId = clientId,
                shoplUserId = userId,
                platform = IdpClient.Platform.APP,
                loginType = LoginType.SOCIAL,
                failureReason = FailureReasonType.SYSTEM_ERROR,
                sessionId = "FAIL_SESSION_$i"
            )
        }

        // When
        val pageable = PageRequest.of(0, 10)
        val result = sut.getUserLoginHistory(userId, pageable)

        // Then
        assertEquals(8, result.totalElements)
        assertEquals(8, result.content.size)
        
        // 최신순으로 정렬되어 있는지 확인
        for (i in 0 until result.content.size - 1) {
            assertTrue(
                result.content[i].loginTime.isAfter(result.content[i + 1].loginTime) ||
                result.content[i].loginTime.isEqual(result.content[i + 1].loginTime)
            )
        }
    }

    @Test
    @DisplayName("클라이언트별 로그인 이력 조회")
    fun testGetUserLoginHistoryByClient() {
        // Given
        val userId = TEST_USER_ID
        val clientId1 = "CLIENT001"
        val clientId2 = "CLIENT002"
        
        // 클라이언트 1의 이력
        repeat(3) { i ->
            sut.recordSuccessfulLogin(
                shoplClientId = clientId1,
                shoplUserId = userId,
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                sessionId = "SESSION_C1_$i"
            )
        }
        
        // 클라이언트 2의 이력
        repeat(2) { i ->
            sut.recordSuccessfulLogin(
                shoplClientId = clientId2,
                shoplUserId = userId,
                platform = IdpClient.Platform.APP,
                loginType = LoginType.SSO,
                sessionId = "SESSION_C2_$i"
            )
        }

        // When
        val pageable = PageRequest.of(0, 10)
        val result1 = sut.getUserLoginHistory(userId, clientId1, pageable)
        val result2 = sut.getUserLoginHistory(userId, clientId2, pageable)

        // Then
        assertEquals(3, result1.totalElements)
        assertEquals(2, result2.totalElements)
        
        result1.content.forEach { 
            assertEquals(clientId1, it.shoplClientId)
            assertEquals(LoginType.BASIC, it.loginType)
        }
        
        result2.content.forEach { 
            assertEquals(clientId2, it.shoplClientId)
            assertEquals(LoginType.SSO, it.loginType)
        }
    }

    @Test
    @DisplayName("기간별 로그인 이력 조회")
    fun testGetUserLoginHistoryByPeriod() {
        // Given
        val userId = TEST_USER_ID
        val clientId = TEST_CLIENT_ID
        val now = LocalDateTime.now()
        val startTime = now.minusDays(1)
        val endTime = now.plusDays(1)

        // 기간 내 이력 생성
        repeat(3) { i ->
            sut.recordSuccessfulLogin(
                shoplClientId = clientId,
                shoplUserId = userId,
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                sessionId = "PERIOD_SESSION_$i"
            )
        }

        // When
        val pageable = PageRequest.of(0, 10)
        val result = sut.getUserLoginHistory(userId, startTime, endTime, pageable)

        // Then
        assertEquals(3, result.totalElements)
        result.content.forEach { history ->
            assertTrue(history.loginTime.isAfter(startTime) || history.loginTime.isEqual(startTime))
            assertTrue(history.loginTime.isBefore(endTime) || history.loginTime.isEqual(endTime))
        }
    }

    @Test
    @DisplayName("마지막 성공한 로그인 조회")
    fun testGetLastSuccessfulLogin() {
        // Given
        val userId = TEST_USER_ID
        val clientId = TEST_CLIENT_ID

        // 실패 로그인
        sut.recordFailedLogin(
            shoplClientId = clientId,
            shoplUserId = userId,
            platform = IdpClient.Platform.DASHBOARD,
            loginType = LoginType.BASIC,
            failureReason = FailureReasonType.INVALID_CREDENTIALS,
            sessionId = "FAIL_SESSION"
        )

        Thread.sleep(10) // 시간 차이를 위해 잠시 대기

        // 성공 로그인
        val successfulLogin = sut.recordSuccessfulLogin(
            shoplClientId = clientId,
            shoplUserId = userId,
            platform = IdpClient.Platform.DASHBOARD,
            loginType = LoginType.BASIC,
            sessionId = "SUCCESS_SESSION"
        )

        Thread.sleep(10) // 시간 차이를 위해 잠시 대기

        // 또 다른 실패 로그인
        sut.recordFailedLogin(
            shoplClientId = clientId,
            shoplUserId = userId,
            platform = IdpClient.Platform.DASHBOARD,
            loginType = LoginType.BASIC,
            failureReason = FailureReasonType.TOO_MANY_ATTEMPTS,
            sessionId = "FAIL_SESSION_2"
        )

        // When
        val lastSuccessful = sut.getLastSuccessfulLogin(userId)

        // Then
        assertNotNull(lastSuccessful)
        assertEquals(successfulLogin.id, lastSuccessful!!.id)
        assertEquals(LoginResult.SUCCESS, lastSuccessful.result)
        assertEquals("SUCCESS_SESSION", lastSuccessful.sessionId)
    }

    @Test
    @DisplayName("최근 실패한 로그인 시도 횟수 조회")
    fun testGetRecentFailedLoginAttempts() {
        // Given
        val userId = TEST_USER_ID
        val clientId = TEST_CLIENT_ID

        // 성공 로그인 (카운트에 포함되지 않아야 함)
        sut.recordSuccessfulLogin(
            shoplClientId = clientId,
            shoplUserId = userId,
            platform = IdpClient.Platform.DASHBOARD,
            loginType = LoginType.BASIC,
            sessionId = "SUCCESS_SESSION"
        )

        // 실패 로그인들
        repeat(4) { i ->
            sut.recordFailedLogin(
                shoplClientId = clientId,
                shoplUserId = userId,
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.INVALID_CREDENTIALS,
                sessionId = "FAIL_SESSION_$i"
            )
        }

        // When
        val failCount = sut.getRecentFailedLoginAttempts(userId, 24)

        // Then
        assertEquals(4, failCount)
    }

    @Test
    @DisplayName("클라이언트별 로그인 통계")
    fun testGetClientLoginStats() {
        // Given
        val clientId = TEST_CLIENT_ID
        val userId = TEST_USER_ID

        // 성공 로그인 7개
        repeat(7) { i ->
            sut.recordSuccessfulLogin(
                shoplClientId = clientId,
                shoplUserId = "${userId}_$i",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                sessionId = "SUCCESS_$i"
            )
        }

        // 실패 로그인 3개
        repeat(3) { i ->
            sut.recordFailedLogin(
                shoplClientId = clientId,
                shoplUserId = "${userId}_fail_$i",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.SYSTEM_ERROR,
                sessionId = "FAIL_$i"
            )
        }

        // When
        val stats = sut.getClientLoginStats(clientId, 30)

        // Then
        assertEquals(7, stats.successCount)
        assertEquals(3, stats.failCount)
        assertEquals(10, stats.totalCount)
        assertEquals(70.0, stats.successRate, 0.01)
    }

    @Test
    @DisplayName("로그인 타입별 통계")
    fun testGetLoginTypeStats() {
        // Given
        val clientId = TEST_CLIENT_ID
        val userId = TEST_USER_ID

        // BASIC 로그인 - 성공 5개, 실패 2개
        repeat(5) { i ->
            sut.recordSuccessfulLogin(
                shoplClientId = clientId,
                shoplUserId = "${userId}_basic_$i",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                sessionId = "BASIC_SUCCESS_$i"
            )
        }
        
        repeat(2) { i ->
            sut.recordFailedLogin(
                shoplClientId = clientId,
                shoplUserId = "${userId}_basic_fail_$i",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.INVALID_CREDENTIALS,
                sessionId = "BASIC_FAIL_$i"
            )
        }

        // SOCIAL 로그인 - 성공 3개, 실패 1개
        repeat(3) { i ->
            sut.recordSuccessfulLogin(
                shoplClientId = clientId,
                shoplUserId = "${userId}_social_$i",
                platform = IdpClient.Platform.APP,
                loginType = LoginType.SOCIAL,
                provider = "GOOGLE",
                sessionId = "SOCIAL_SUCCESS_$i"
            )
        }
        
        sut.recordFailedLogin(
            shoplClientId = clientId,
            shoplUserId = "${userId}_social_fail",
            platform = IdpClient.Platform.APP,
            loginType = LoginType.SOCIAL,
            provider = "GOOGLE",
            failureReason = FailureReasonType.SSO_ERROR,
            sessionId = "SOCIAL_FAIL"
        )

        // When
        val stats = sut.getLoginTypeStats(clientId, 30)

        // Then
        assertEquals(2, stats.size)
        
        val basicStats = stats.find { it.loginType == LoginType.BASIC }
        val socialStats = stats.find { it.loginType == LoginType.SOCIAL }
        
        assertNotNull(basicStats)
        assertEquals(5, basicStats!!.successCount)
        assertEquals(2, basicStats.failCount)
        assertEquals(7, basicStats.totalCount)
        assertEquals(71.43, basicStats.successRate, 0.01)
        
        assertNotNull(socialStats)
        assertEquals(3, socialStats!!.successCount)
        assertEquals(1, socialStats.failCount)
        assertEquals(4, socialStats.totalCount)
        assertEquals(75.0, socialStats.successRate, 0.01)
    }
}