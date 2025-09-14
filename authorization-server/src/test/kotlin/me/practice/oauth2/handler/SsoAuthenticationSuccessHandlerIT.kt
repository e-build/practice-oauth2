package me.practice.oauth2.handler

import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.IoIdpLoginHistoryRepository
import me.practice.oauth2.entity.LoginResult
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.entity.ProviderType
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.UserProvisioningService
import me.practice.oauth2.testbase.AuthenticationTestUtils
import me.practice.oauth2.testbase.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import org.mockito.Mockito.`when`
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Import(SsoAuthenticationSuccessHandler::class, LoginHistoryService::class)
class SsoAuthenticationSuccessHandlerIT(
	private val sut: SsoAuthenticationSuccessHandler,
	private val loginHistoryService: LoginHistoryService,
	private val loginHistoryRepository: IoIdpLoginHistoryRepository,
) : IntegrationTestBase() {

	@MockBean
	private lateinit var userProvisioningService: UserProvisioningService

	@BeforeEach
	override fun setUp() {
		super.setUp()
		loginHistoryRepository.deleteAll()
		reset(userProvisioningService)
	}

	@Test
	@DisplayName("OAuth2 소셜 로그인 성공 시 SOCIAL 타입 로그인 이력이 기록된다")
	fun onAuthenticationSuccess_OAuth2Social_ShouldRecordSocialLoginHistory() {
		// Given
		val registrationId = "google"
		val account = AuthenticationTestUtils.createTestAccount()
		val oauth2User = AuthenticationTestUtils.createMockOAuth2User()
		val authentication = OAuth2AuthenticationToken(oauth2User, emptyList(), registrationId)

		val request = MockHttpServletRequest()
		request.setParameter("client_id", TEST_CLIENT_ID)
		val response = MockHttpServletResponse()

		// Mock 설정
		`when`(
			userProvisioningService.provisionUser(
				oauth2User,
				TEST_CLIENT_ID,
				ProviderType.GOOGLE,
				registrationId
			)
		).thenReturn(account)

		// When
		sut.onAuthenticationSuccess(request, response, authentication)

		// Then
		// 로그인 이력 확인
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals(TEST_CLIENT_ID, history.shoplClientId)
		assertEquals(TEST_USER_ID, history.shoplUserId)
		assertEquals(IdpClient.Platform.DASHBOARD, history.platform)
		assertEquals(LoginType.SOCIAL, history.loginType)
		assertEquals("GOOGLE", history.provider)
		assertEquals(LoginResult.SUCCESS, history.result)
		assertNotNull(history.sessionId)
		assertNull(history.failureReason)
		assertNotNull(history.loginTime)
	}

	@Test
	@DisplayName("OIDC SSO 로그인 성공 시 SSO 타입 로그인 이력이 기록된다")
	fun onAuthenticationSuccess_OidcSso_ShouldRecordSsoLoginHistory() {
		// Given
		val registrationId = "keycloak"
		val account = AuthenticationTestUtils.createTestAccount()
		val oidcUser = AuthenticationTestUtils.createMockOidcUser()
		val authentication = OAuth2AuthenticationToken(oidcUser, emptyList(), registrationId)

		val request = MockHttpServletRequest()
		request.setParameter("client_id", TEST_CLIENT_ID)
		val response = MockHttpServletResponse()

		// Mock 설정
		`when`(
			userProvisioningService.provisionOidcUser(
				oidcUser,
				TEST_CLIENT_ID,
				registrationId
			)
		).thenReturn(account)

		// When
		sut.onAuthenticationSuccess(request, response, authentication)

		// Then
		// 로그인 이력 확인
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals(TEST_CLIENT_ID, history.shoplClientId)
		assertEquals(TEST_USER_ID, history.shoplUserId)
		assertEquals(IdpClient.Platform.DASHBOARD, history.platform)
		assertEquals(LoginType.SSO, history.loginType)
		assertEquals("OIDC", history.provider)
		assertEquals(LoginResult.SUCCESS, history.result)
		assertNotNull(history.sessionId)
		assertNull(history.failureReason)
		assertNotNull(history.loginTime)
	}

	@Test
	@DisplayName("세션에서 클라이언트 ID를 추출하여 로그인 이력을 기록한다")
	fun onAuthenticationSuccess_WithClientIdInSession_ShouldRecordHistory() {
		// Given
		val registrationId = "kakao"
		val clientIdFromSession = "CLIENT_FROM_SESSION"
		val account = AuthenticationTestUtils.createTestAccount(
			shoplClientId = clientIdFromSession
		)
		val oauth2User = AuthenticationTestUtils.createMockOAuth2User()
		val authentication = OAuth2AuthenticationToken(oauth2User, emptyList(), registrationId)

		val request = MockHttpServletRequest()
		// 파라미터에는 없고 세션에만 있는 경우
		request.session!!.setAttribute("shopl_client_id", clientIdFromSession)
		val response = MockHttpServletResponse()

		// Mock 설정
		`when`(
			userProvisioningService.provisionUser(
				oauth2User,
				clientIdFromSession,
				ProviderType.KAKAO,
				registrationId
			)
		).thenReturn(account)

		// When
		sut.onAuthenticationSuccess(request, response, authentication)

		// Then
		// 로그인 이력 확인
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals(clientIdFromSession, history.shoplClientId)
		assertEquals(TEST_USER_ID, history.shoplUserId)
		assertEquals(LoginType.SOCIAL, history.loginType)
		assertEquals("KAKAO", history.provider)
	}

	@Test
	@DisplayName("다양한 소셜 제공자별로 올바른 제공자 타입이 결정된다")
	fun onAuthenticationSuccess_VariousProviders_ShouldDetermineCorrectProviderType() {
		// Given
		val testCases = listOf(
			"google" to ProviderType.GOOGLE,
			"naver" to ProviderType.NAVER,
			"github" to ProviderType.GITHUB,
			"microsoft" to ProviderType.MICROSOFT,
			"apple" to ProviderType.APPLE,
			"saml-provider" to ProviderType.SAML
		)

		testCases.forEach { (registrationId, expectedProviderType) ->
			// 테스트 간 데이터 정리
			loginHistoryRepository.deleteAll()
			reset(userProvisioningService)

			val account = AuthenticationTestUtils.createTestAccount()
			val oauth2User = AuthenticationTestUtils.createMockOAuth2User()
			val authentication = OAuth2AuthenticationToken(oauth2User, emptyList(), registrationId)

			val request = MockHttpServletRequest()
			request.setParameter("client_id", TEST_CLIENT_ID)
			val response = MockHttpServletResponse()

			// Mock 설정
			`when`(
				userProvisioningService.provisionUser(
					oauth2User,
					TEST_CLIENT_ID,
					expectedProviderType,
					registrationId
				)
			).thenReturn(account)

			// When
			sut.onAuthenticationSuccess(request, response, authentication)

			// Then
			val histories = loginHistoryRepository.findAll()
			assertEquals(1, histories.size, "Failed for provider: $registrationId")

			val history = histories[0]
			assertEquals(expectedProviderType.name, history.provider, "Failed for provider: $registrationId")

			val expectedLoginType = if (expectedProviderType in listOf(
					ProviderType.GOOGLE, ProviderType.KAKAO, ProviderType.NAVER,
					ProviderType.APPLE, ProviderType.MICROSOFT, ProviderType.GITHUB
				)
			) LoginType.SOCIAL else LoginType.SSO

			assertEquals(expectedLoginType, history.loginType, "Failed for provider: $registrationId")
		}
	}

	// 프로비저닝 실패 및 이력 기록 실패 테스트는 단위 테스트에서 별도로 진행
	// 통합 테스트에서는 정상 플로우 위주로 테스트
}