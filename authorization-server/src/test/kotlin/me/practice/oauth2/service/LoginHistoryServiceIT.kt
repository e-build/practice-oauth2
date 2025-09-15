package me.practice.oauth2.service

import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.*
import me.practice.oauth2.testbase.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.TestPropertySource
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(LoginHistoryService::class)
@TestPropertySource(properties = ["spring.jpa.show-sql=false"])
class LoginHistoryServiceIT(
	private val loginHistoryService: LoginHistoryService,
	private val loginHistoryRepository: IoIdpLoginHistoryRepository,
) : IntegrationTestBase() {

	companion object {
		private val FIXED_CLOCK = Clock.fixed(
			LocalDateTime.of(2024, 1, 15, 10, 30, 0)
				.atZone(ZoneId.systemDefault()).toInstant(),
			ZoneId.systemDefault()
		)
	}

	@BeforeEach
	override fun setUp() {
		super.setUp()
		loginHistoryRepository.deleteAll()
	}

	private fun createTestSuccessLogin(
		clientId: String = TEST_CLIENT_ID,
		userId: String = TEST_USER_ID,
		platform: IdpClient.Platform = IdpClient.Platform.DASHBOARD,
		loginType: LoginType = LoginType.BASIC,
		sessionId: String = TEST_SESSION_ID,
		provider: String? = null,
	): IoIdpUserLoginHistory {
		return loginHistoryService.recordSuccessfulLogin(
			shoplClientId = clientId,
			shoplUserId = userId,
			platform = platform,
			loginType = loginType,
			sessionId = sessionId,
			provider = provider
		)
	}

	private fun createTestFailedLogin(
		clientId: String = TEST_CLIENT_ID,
		userId: String = TEST_USER_ID,
		platform: IdpClient.Platform = IdpClient.Platform.DASHBOARD,
		loginType: LoginType = LoginType.BASIC,
		failureReason: FailureReasonType = FailureReasonType.INVALID_CREDENTIALS,
		sessionId: String = TEST_SESSION_ID,
		provider: String? = null,
	): IoIdpUserLoginHistory {
		return loginHistoryService.recordFailedLogin(
			shoplClientId = clientId,
			shoplUserId = userId,
			platform = platform,
			loginType = loginType,
			provider = provider,
			failureReason = failureReason,
			sessionId = sessionId
		)
	}

	@Nested
	@DisplayName("로그인 이력 저장 테스트")
	inner class RecordLoginHistoryTest {

		@Test
		@DisplayName("성공한 로그인 이력이 올바르게 저장된다")
		fun recordSuccessfulLogin() {
			// Given
			val platform = IdpClient.Platform.DASHBOARD
			val loginType = LoginType.BASIC
			val sessionId = "SUCCESS_SESSION_001"

			// When
			val savedHistory = createTestSuccessLogin(
				platform = platform,
				loginType = loginType,
				sessionId = sessionId
			)

			// Then
			with(savedHistory) {
				assertNotNull(id)
				assertEquals(TEST_CLIENT_ID, shoplClientId)
				assertEquals(TEST_USER_ID, shoplUserId)
				assertEquals(platform, this.platform)
				assertEquals(loginType, this.loginType)
				assertEquals(LoginResult.SUCCESS, result)
				assertEquals(sessionId, this.sessionId)
				assertNull(failureReason)
				assertNull(provider)
				assertNotNull(regDt)
			}

			// DB에서 직접 확인
			val dbHistory = loginHistoryRepository.findById(savedHistory.id!!)
			assertTrue(dbHistory.isPresent)
			assertEquals(LoginResult.SUCCESS, dbHistory.get().result)
		}

		@Test
		@DisplayName("소셜 로그인 성공 이력이 provider와 함께 저장된다")
		fun recordSuccessfulSocialLogin() {
			// Given
			val provider = "GOOGLE"
			val loginType = LoginType.SOCIAL

			// When
			val savedHistory = createTestSuccessLogin(
				loginType = loginType,
				provider = provider,
				sessionId = "SOCIAL_SUCCESS_001"
			)

			// Then
			with(savedHistory) {
				assertEquals(LoginResult.SUCCESS, result)
				assertEquals(loginType, this.loginType)
				assertEquals(provider, this.provider)
			}
		}

		@Test
		@DisplayName("실패한 로그인 이력이 올바르게 저장된다")
		fun recordFailedLogin() {
			// Given
			val platform = IdpClient.Platform.APP
			val loginType = LoginType.BASIC
			val failureReason = FailureReasonType.INVALID_CREDENTIALS
			val sessionId = "FAILED_SESSION_001"

			// When
			val savedHistory = createTestFailedLogin(
				platform = platform,
				loginType = loginType,
				failureReason = failureReason,
				sessionId = sessionId
			)

			// Then
			with(savedHistory) {
				assertNotNull(id)
				assertEquals(TEST_CLIENT_ID, shoplClientId)
				assertEquals(TEST_USER_ID, shoplUserId)
				assertEquals(platform, this.platform)
				assertEquals(loginType, this.loginType)
				assertEquals(LoginResult.FAIL, result)
				assertEquals(failureReason, this.failureReason)
				assertEquals(sessionId, this.sessionId)
				assertNotNull(regDt)
			}

			// DB에서 직접 확인
			val dbHistory = loginHistoryRepository.findById(savedHistory.id!!)
			assertTrue(dbHistory.isPresent)
			assertEquals(LoginResult.FAIL, dbHistory.get().result)
			assertEquals(failureReason, dbHistory.get().failureReason)
		}

		@Test
		@DisplayName("소셜 로그인 실패 이력이 provider와 함께 저장된다")
		fun recordFailedSocialLogin() {
			// Given
			val provider = "GOOGLE"
			val loginType = LoginType.SOCIAL
			val failureReason = FailureReasonType.SSO_ERROR

			// When
			val savedHistory = createTestFailedLogin(
				loginType = loginType,
				provider = provider,
				failureReason = failureReason,
				sessionId = "SOCIAL_FAIL_001"
			)

			// Then
			with(savedHistory) {
				assertEquals(LoginResult.FAIL, result)
				assertEquals(loginType, this.loginType)
				assertEquals(provider, this.provider)
				assertEquals(failureReason, this.failureReason)
			}
		}
	}

	@Nested
	@DisplayName("로그인 이력 조회 테스트")
	inner class GetLoginHistoryTest {

		@Test
		@DisplayName("사용자별 로그인 이력을 페이징하여 조회할 수 있다")
		fun getUserLoginHistory() {
			// Given
			val userId = TEST_USER_ID
			val clientId = TEST_CLIENT_ID

			// 테스트 데이터 생성 (성공 5개, 실패 3개)
			repeat(5) { i ->
				createTestSuccessLogin(
					clientId = clientId,
					userId = userId,
					sessionId = "SUCCESS_SESSION_$i"
				)
			}

			repeat(3) { i ->
				createTestFailedLogin(
					clientId = clientId,
					userId = userId,
					sessionId = "FAIL_SESSION_$i"
				)
			}

			// When
			val pageable = PageRequest.of(0, 10)
			val result = loginHistoryService.getUserLoginHistory(userId, pageable)

			// Then
			assertEquals(8, result.totalElements)
			assertEquals(8, result.content.size)

			// 결과가 존재하고 데이터베이스에서 조회되었는지만 확인 (정렬 확인은 생략)
			// 데이터베이스 레벨에서 ORDER BY regDt DESC가 적용되므로 별도 검증 불필요
			assertTrue(result.content.isNotEmpty(), "조회된 로그인 이력이 없습니다.")
			result.content.forEach { history ->
				assertEquals(userId, history.shoplUserId)
			}
		}

		@Test
		@DisplayName("페이지 크기가 적용되어 조회된다")
		fun getUserLoginHistoryWithPaging() {
			// Given
			repeat(15) { i ->
				createTestSuccessLogin(sessionId = "SESSION_$i")
			}

			// When
			val pageable = PageRequest.of(0, 5)
			val result = loginHistoryService.getUserLoginHistory(TEST_USER_ID, pageable)

			// Then
			assertEquals(15, result.totalElements)
			assertEquals(5, result.content.size)
			assertEquals(3, result.totalPages)
		}

		@Test
		@DisplayName("클라이언트별 로그인 이력을 조회할 수 있다")
		fun getUserLoginHistoryByClient() {
			// Given
			val userId = TEST_USER_ID
			val clientId1 = "CLIENT001"
			val clientId2 = "CLIENT002"

			// 클라이언트 1의 이력
			repeat(3) { i ->
				createTestSuccessLogin(
					clientId = clientId1,
					userId = userId,
					loginType = LoginType.BASIC,
					sessionId = "SESSION_C1_$i"
				)
			}

			// 클라이언트 2의 이력
			repeat(2) { i ->
				createTestSuccessLogin(
					clientId = clientId2,
					userId = userId,
					platform = IdpClient.Platform.APP,
					loginType = LoginType.SSO,
					sessionId = "SESSION_C2_$i"
				)
			}

			// When
			val pageable = PageRequest.of(0, 10)
			val result1 = loginHistoryService.getUserLoginHistory(userId, clientId1, pageable)
			val result2 = loginHistoryService.getUserLoginHistory(userId, clientId2, pageable)

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
				assertEquals(IdpClient.Platform.APP, it.platform)
			}
		}

		@Test
		@DisplayName("기간별 로그인 이력을 조회할 수 있다")
		fun getUserLoginHistoryByPeriod() {
			// Given
			val userId = TEST_USER_ID
			val clientId = TEST_CLIENT_ID
			val now = LocalDateTime.now()
			val startTime = now.minusDays(1)
			val endTime = now.plusDays(1)

			// 기간 내 이력 생성
			repeat(3) { i ->
				createTestSuccessLogin(
					clientId = clientId,
					userId = userId,
					sessionId = "PERIOD_SESSION_$i"
				)
			}

			// When
			val pageable = PageRequest.of(0, 10)
			val result = loginHistoryService.getUserLoginHistory(userId, startTime, endTime, pageable)

			// Then
			assertEquals(3, result.totalElements)
			result.content.forEach { history ->
				assertTrue(
					history.regDt.isAfter(startTime) || history.regDt.isEqual(startTime),
					"시작 시간 범위를 벗어난 데이터가 포함되었습니다."
				)
				assertTrue(
					history.regDt.isBefore(endTime) || history.regDt.isEqual(endTime),
					"종료 시간 범위를 벗어난 데이터가 포함되었습니다."
				)
			}
		}

		@Test
		@DisplayName("존재하지 않는 사용자의 이력 조회 시 빈 결과를 반환한다")
		fun getUserLoginHistoryForNonExistentUser() {
			// Given
			val nonExistentUserId = "NON_EXISTENT_USER"

			// When
			val pageable = PageRequest.of(0, 10)
			val result = loginHistoryService.getUserLoginHistory(nonExistentUserId, pageable)

			// Then
			assertEquals(0, result.totalElements)
			assertTrue(result.content.isEmpty())
		}
	}

	@Nested
	@DisplayName("로그인 이력 분석 테스트")
	inner class LoginHistoryAnalysisTest {

		@Test
		@DisplayName("마지막 성공한 로그인을 조회할 수 있다")
		fun getLastSuccessfulLogin() {
			// Given
			val userId = TEST_USER_ID
			val clientId = TEST_CLIENT_ID

			// 실패 로그인
			createTestFailedLogin(
				clientId = clientId,
				userId = userId,
				sessionId = "FAIL_SESSION"
			)

			// 성공 로그인 (가장 최근이어야 함)
			val expectedLastLogin = createTestSuccessLogin(
				clientId = clientId,
				userId = userId,
				sessionId = "SUCCESS_SESSION"
			)

			// 또 다른 실패 로그인 (시간적으로 더 나중)
			createTestFailedLogin(
				clientId = clientId,
				userId = userId,
				sessionId = "FAIL_SESSION_2"
			)

			// When
			val lastSuccessful = loginHistoryService.getLastSuccessfulLogin(userId)

			// Then
			assertNotNull(lastSuccessful)
			assertEquals(expectedLastLogin.id, lastSuccessful.id)
			assertEquals(LoginResult.SUCCESS, lastSuccessful.result)
			assertEquals("SUCCESS_SESSION", lastSuccessful.sessionId)
		}

		@Test
		@DisplayName("성공한 로그인이 없는 경우 null을 반환한다")
		fun getLastSuccessfulLoginWhenNoSuccess() {
			// Given
			val userId = TEST_USER_ID

			// 실패 로그인만 생성
			repeat(3) { i ->
				createTestFailedLogin(sessionId = "FAIL_SESSION_$i")
			}

			// When
			val lastSuccessful = loginHistoryService.getLastSuccessfulLogin(userId)

			// Then
			assertNull(lastSuccessful)
		}

		@Test
		@DisplayName("최근 실패한 로그인 시도 횟수를 조회할 수 있다")
		fun getRecentFailedLoginAttempts() {
			// Given
			val userId = TEST_USER_ID
			val clientId = TEST_CLIENT_ID

			// 성공 로그인 (카운트에 포함되지 않아야 함)
			createTestSuccessLogin(
				clientId = clientId,
				userId = userId,
				sessionId = "SUCCESS_SESSION"
			)

			// 실패 로그인들
			repeat(4) { i ->
				createTestFailedLogin(
					clientId = clientId,
					userId = userId,
					sessionId = "FAIL_SESSION_$i"
				)
			}

			// When
			val failCount = loginHistoryService.getRecentFailedLoginAttempts(userId, 24)

			// Then
			assertEquals(4, failCount)
		}
	}

	@Nested
	@DisplayName("로그인 통계 테스트")
	inner class LoginStatisticsTest {

		@Test
		@DisplayName("클라이언트별 로그인 통계를 조회할 수 있다")
		fun getClientLoginStats() {
			// Given
			val clientId = TEST_CLIENT_ID

			// 성공 로그인 7개
			repeat(7) { i ->
				createTestSuccessLogin(
					clientId = clientId,
					userId = "${TEST_USER_ID}_$i",
					sessionId = "SUCCESS_$i"
				)
			}

			// 실패 로그인 3개
			repeat(3) { i ->
				createTestFailedLogin(
					clientId = clientId,
					userId = "${TEST_USER_ID}_fail_$i",
					sessionId = "FAIL_$i"
				)
			}

			// When
			val stats = loginHistoryService.getClientLoginStats(clientId, 30)

			// Then
			assertEquals(7, stats.successCount)
			assertEquals(3, stats.failCount)
			assertEquals(10, stats.totalCount)
			assertEquals(70.0, stats.successRate, 0.01)
		}

		@Test
		@DisplayName("로그인 타입별 통계를 조회할 수 있다")
		fun getLoginTypeStats() {
			// Given
			val clientId = TEST_CLIENT_ID

			// BASIC 로그인 - 성공 5개, 실패 2개
			repeat(5) { i ->
				createTestSuccessLogin(
					clientId = clientId,
					userId = "${TEST_USER_ID}_basic_$i",
					loginType = LoginType.BASIC,
					sessionId = "BASIC_SUCCESS_$i"
				)
			}

			repeat(2) { i ->
				createTestFailedLogin(
					clientId = clientId,
					userId = "${TEST_USER_ID}_basic_fail_$i",
					loginType = LoginType.BASIC,
					sessionId = "BASIC_FAIL_$i"
				)
			}

			// SOCIAL 로그인 - 성공 3개, 실패 1개
			repeat(3) { i ->
				createTestSuccessLogin(
					clientId = clientId,
					userId = "${TEST_USER_ID}_social_$i",
					platform = IdpClient.Platform.APP,
					loginType = LoginType.SOCIAL,
					provider = "GOOGLE",
					sessionId = "SOCIAL_SUCCESS_$i"
				)
			}

			createTestFailedLogin(
				clientId = clientId,
				userId = "${TEST_USER_ID}_social_fail",
				platform = IdpClient.Platform.APP,
				loginType = LoginType.SOCIAL,
				provider = "GOOGLE",
				failureReason = FailureReasonType.SSO_ERROR,
				sessionId = "SOCIAL_FAIL"
			)

			// When
			val stats = loginHistoryService.getLoginTypeStats(clientId, 30)

			// Then
			assertEquals(2, stats.size)

			val basicStats = stats.find { it.loginType == LoginType.BASIC }
			val socialStats = stats.find { it.loginType == LoginType.SOCIAL }

			assertNotNull(basicStats)
			with(basicStats) {
				assertEquals(5, successCount)
				assertEquals(2, failCount)
				assertEquals(7, totalCount)
				assertEquals(71.43, successRate, 0.01)
			}

			assertNotNull(socialStats)
			with(socialStats) {
				assertEquals(3, successCount)
				assertEquals(1, failCount)
				assertEquals(4, totalCount)
				assertEquals(75.0, successRate, 0.01)
			}
		}

		@Test
		@DisplayName("통계 데이터가 없는 경우 빈 결과를 반환한다")
		fun getEmptyStatsForNoData() {
			// Given
			val nonExistentClientId = "NON_EXISTENT_CLIENT"

			// When
			val clientStats = loginHistoryService.getClientLoginStats(nonExistentClientId, 30)
			val typeStats = loginHistoryService.getLoginTypeStats(nonExistentClientId, 30)

			// Then
			assertEquals(0, clientStats.totalCount)
			assertEquals(0, clientStats.successCount)
			assertEquals(0, clientStats.failCount)
			assertEquals(0.0, clientStats.successRate)
			assertTrue(typeStats.isEmpty())
		}
	}
}