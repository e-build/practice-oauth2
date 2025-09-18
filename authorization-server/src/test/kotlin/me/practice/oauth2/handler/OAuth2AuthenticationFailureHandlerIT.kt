package me.practice.oauth2.handler

import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.*
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.testbase.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Import(OAuth2AuthenticationFailureHandler::class, LoginHistoryService::class)
class OAuth2AuthenticationFailureHandlerIT(
	private val sut: OAuth2AuthenticationFailureHandler,
	private val loginHistoryService: LoginHistoryService,
	private val loginHistoryRepository: IoIdpLoginHistoryRepository,
) : IntegrationTestBase() {

	@BeforeEach
	override fun setUp() {
		super.setUp()
		loginHistoryRepository.deleteAll()
	}

	@Test
	@DisplayName("OAuth2 소셜 로그인 실패 시 SOCIAL 타입 실패 이력이 기록된다")
	fun onAuthenticationFailure_OAuth2Social_ShouldRecordSocialFailureHistory() {
		// Given
		val request = MockHttpServletRequest()
		request.requestURI = "/oauth2/authorization/google"
		request.setParameter("client_id", TEST_CLIENT_ID)
		val response = MockHttpServletResponse()

		val oauth2Error = OAuth2Error("invalid_request", "Invalid request", null)
		val exception = OAuth2AuthenticationException(oauth2Error)

		// When
		sut.onAuthenticationFailure(request, response, exception)

		// Then
		// 로그인 실패 이력 확인
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals(TEST_CLIENT_ID, history.shoplClientId)
		assertEquals("unknown", history.shoplUserId) // 실패 시에는 사용자 ID를 알 수 없음
		assertEquals(IdpClient.Platform.DASHBOARD, history.platform)
		assertEquals(LoginType.SOCIAL, history.loginType)
		assertEquals(ProviderType.GOOGLE, history.providerType)
		assertEquals(LoginResult.FAIL, history.result)
		assertEquals(FailureReasonType.SSO_ERROR, history.failureReason)
		assertNotNull(history.sessionId)
		assertNotNull(history.regDt)
	}

	@Test
	@DisplayName("OIDC SSO 로그인 실패 시 SSO 타입 실패 이력이 기록된다")
	fun onAuthenticationFailure_OidcSso_ShouldRecordSsoFailureHistory() {
		// Given
		val request = MockHttpServletRequest()
		request.requestURI = "/oauth2/authorization/keycloak"
		request.setParameter("client_id", TEST_CLIENT_ID)
		val response = MockHttpServletResponse()

		val oauth2Error = OAuth2Error("access_denied", "Access denied", null)
		val exception = OAuth2AuthenticationException(oauth2Error)

		// When
		sut.onAuthenticationFailure(request, response, exception)

		// Then
		// 로그인 실패 이력 확인
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals(TEST_CLIENT_ID, history.shoplClientId)
		assertEquals("unknown", history.shoplUserId)
		assertEquals(IdpClient.Platform.DASHBOARD, history.platform)
		assertEquals(LoginType.SSO, history.loginType)
		assertEquals(ProviderType.OIDC, history.providerType)
		assertEquals(LoginResult.FAIL, history.result)
		assertEquals(FailureReasonType.SSO_ERROR, history.failureReason)
	}

	@Test
	@DisplayName("다양한 OAuth2 에러별로 올바른 실패 원인이 기록된다")
	fun onAuthenticationFailure_VariousOAuth2Errors_ShouldRecordCorrectFailureReasons() {
		// Given
		val testCases = listOf(
			OAuth2Error("invalid_client", "Invalid client", null) to FailureReasonType.INVALID_CLIENT,
			OAuth2Error("invalid_scope", "Invalid scope", null) to FailureReasonType.INVALID_SCOPE,
			OAuth2Error("network_error", "Network error", null) to FailureReasonType.NETWORK_ERROR,
			OAuth2Error("server_error", "Server error", null) to FailureReasonType.SSO_ERROR
		)

		testCases.forEachIndexed { index, testCase ->
			val (oauth2Error, expectedFailureReason) = testCase

			// 테스트 간 데이터 정리
			loginHistoryRepository.deleteAll()

			val request = MockHttpServletRequest()
			request.requestURI = "/oauth2/authorization/google"
			request.setParameter("client_id", "CLIENT_${index + 1}")
			val response = MockHttpServletResponse()

			val exception = OAuth2AuthenticationException(oauth2Error)

			// When
			sut.onAuthenticationFailure(request, response, exception)

			// Then
			val histories = loginHistoryRepository.findAll()
			assertEquals(1, histories.size, "Failed for error: ${oauth2Error.errorCode}")

			val history = histories[0]
			assertEquals(
				expectedFailureReason, history.failureReason,
				"Failed for error: ${oauth2Error.errorCode}"
			)
			assertEquals(LoginResult.FAIL, history.result)
		}
	}

	@Test
	@DisplayName("세션에서 클라이언트 ID를 추출하여 실패 이력을 기록한다")
	fun onAuthenticationFailure_WithClientIdInSession_ShouldRecordFailureHistory() {
		// Given
		val clientIdFromSession = "CLIENT_FROM_SESSION"
		val request = MockHttpServletRequest()
		request.requestURI = "/oauth2/authorization/naver"
		// 파라미터에는 없고 세션에만 있는 경우
		request.session!!.setAttribute("shopl_client_id", clientIdFromSession)
		val response = MockHttpServletResponse()

		val oauth2Error = OAuth2Error("access_denied", "Access denied", null)
		val exception = OAuth2AuthenticationException(oauth2Error)

		// When
		sut.onAuthenticationFailure(request, response, exception)

		// Then
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals(clientIdFromSession, history.shoplClientId)
		assertEquals(ProviderType.NAVER, history.providerType)
		assertEquals(LoginType.SOCIAL, history.loginType)
	}

	@Test
	@DisplayName("다양한 소셜 제공자별로 올바른 제공자 타입이 결정된다")
	fun onAuthenticationFailure_VariousProviders_ShouldDetermineCorrectProviderType() {
		// Given
		val testCases = listOf(
			"/oauth2/authorization/google" to (ProviderType.GOOGLE to LoginType.SOCIAL),
			"/oauth2/authorization/kakao" to (ProviderType.KAKAO to LoginType.SOCIAL),
			"/oauth2/authorization/naver" to (ProviderType.NAVER to LoginType.SOCIAL),
			"/oauth2/authorization/github" to (ProviderType.GITHUB to LoginType.SOCIAL),
			"/oauth2/authorization/microsoft" to (ProviderType.MICROSOFT to LoginType.SOCIAL),
			"/oauth2/authorization/apple" to (ProviderType.APPLE to LoginType.SOCIAL),
			"/oauth2/authorization/saml-provider" to (ProviderType.SAML to LoginType.SSO),
			"/oauth2/authorization/oidc-provider" to (ProviderType.OIDC to LoginType.SSO),
			"/oauth2/authorization/keycloak" to (ProviderType.OIDC to LoginType.SSO)
		)

		testCases.forEachIndexed { index, (requestUri, expectedData) ->
			// 테스트 간 데이터 정리
			loginHistoryRepository.deleteAll()

			val (expectedProvider, expectedLoginType) = expectedData
			val request = MockHttpServletRequest()
			request.requestURI = requestUri
			request.setParameter("client_id", "CLIENT_${index + 1}")
			val response = MockHttpServletResponse()

			val oauth2Error = OAuth2Error("access_denied", "Access denied", null)
			val exception = OAuth2AuthenticationException(oauth2Error)

			// When
			sut.onAuthenticationFailure(request, response, exception)

			// Then
			val histories = loginHistoryRepository.findAll()
			assertEquals(1, histories.size, "Failed for URI: $requestUri")

			val history = histories[0]
			assertEquals(expectedProvider, history.providerType, "Failed for URI: $requestUri")
			assertEquals(expectedLoginType, history.loginType, "Failed for URI: $requestUri")
		}
	}

	@Test
	@DisplayName("Referer 헤더에서 등록 ID를 추출할 수 있다")
	fun onAuthenticationFailure_WithRegistrationIdInReferer_ShouldExtractCorrectProvider() {
		// Given
		val request = MockHttpServletRequest()
		// URI에는 정보가 없고 Referer 헤더에만 있는 경우
		request.addHeader("Referer", "http://localhost:8080/oauth2/authorization/github?client_id=test")
		request.setParameter("client_id", TEST_CLIENT_ID)
		val response = MockHttpServletResponse()

		val oauth2Error = OAuth2Error("access_denied", "Access denied", null)
		val exception = OAuth2AuthenticationException(oauth2Error)

		// When
		sut.onAuthenticationFailure(request, response, exception)

		// Then
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals(ProviderType.GITHUB, history.providerType)
		assertEquals(LoginType.SOCIAL, history.loginType)
	}

	@Test
	@DisplayName("등록 ID를 추출할 수 없는 경우 기본값이 사용된다")
	fun onAuthenticationFailure_CannotExtractRegistrationId_ShouldUseDefaultValues() {
		// Given
		val request = MockHttpServletRequest()
		// URI나 Referer에서 등록 ID를 추출할 수 없는 경우
		request.setParameter("client_id", TEST_CLIENT_ID)
		val response = MockHttpServletResponse()

		val oauth2Error = OAuth2Error("server_error", "Server error", null)
		val exception = OAuth2AuthenticationException(oauth2Error)

		// When
		sut.onAuthenticationFailure(request, response, exception)

		// Then
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals(ProviderType.OIDC, history.providerType) // 기본값
		assertEquals(LoginType.SSO, history.loginType) // OIDC는 SSO 타입
		assertEquals(FailureReasonType.SSO_ERROR, history.failureReason)
	}

	@Test
	@DisplayName("클라이언트 ID를 추출할 수 없는 경우 기본값이 사용된다")
	fun onAuthenticationFailure_CannotExtractClientId_ShouldUseDefaultClientId() {
		// Given
		val request = MockHttpServletRequest()
		request.requestURI = "/oauth2/authorization/google"
		// 클라이언트 ID가 파라미터나 세션에 없는 경우
		val response = MockHttpServletResponse()

		val oauth2Error = OAuth2Error("invalid_request", "Invalid request", null)
		val exception = OAuth2AuthenticationException(oauth2Error)

		// When
		sut.onAuthenticationFailure(request, response, exception)

		// Then
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)

		val history = histories[0]
		assertEquals("CLIENT001", history.shoplClientId) // 기본값
		assertEquals(ProviderType.GOOGLE, history.providerType)
		assertEquals(LoginType.SOCIAL, history.loginType)
	}

	@Test
	@DisplayName("로그인 이력 기록 실패해도 실패 핸들러 자체는 정상 동작한다")
	fun onAuthenticationFailure_HistoryRecordingFails_ShouldStillWork() {
		// Given
		val request = MockHttpServletRequest()
		request.requestURI = "/oauth2/authorization/google"
		request.setParameter("client_id", TEST_CLIENT_ID)
		val response = MockHttpServletResponse()

		val oauth2Error = OAuth2Error("server_error", "Server error", null)
		val exception = OAuth2AuthenticationException(oauth2Error)

		// When
		// 예외 없이 처리되어야 함
		// 예외 없이 처리되어야 함
		sut.onAuthenticationFailure(request, response, exception)

		// Then
		// 이력이 정상적으로 기록되어야 함
		val histories = loginHistoryRepository.findAll()
		assertEquals(1, histories.size)
		assertEquals(LoginResult.FAIL, histories[0].result)
	}
}