package me.practice.oauth2.mapper

import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.ProviderType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error

/**
 * DON-52: 제공자별 OAuth2 예외 매핑 테스트
 * Chain of Responsibility 패턴으로 구현된 매퍼들의 동작 검증
 */
class OAuth2FailureMappersTest {

    @Test
    @DisplayName("Google 매퍼: access_denied 오류 매핑")
    fun testGoogleAccessDeniedMapping() {
        // given
        val sut = GoogleOAuth2FailureMapper()
        val error = OAuth2Error("access_denied", "User denied access", null)
        val exception = OAuth2AuthenticationException(error)

        // when
        val result = sut.mapException(exception, ProviderType.GOOGLE)

        // then
        assertEquals(FailureReasonType.ACCESS_DENIED, result)
    }

    @Test
    @DisplayName("Kakao 매퍼: KOE101 오류 매핑")
    fun testKakaoKOE101Mapping() {
        // given
        val sut = KakaoOAuth2FailureMapper()
        val error = OAuth2Error("KOE101", "Invalid client", null)
        val exception = OAuth2AuthenticationException(error)

        // when
        val result = sut.mapException(exception, ProviderType.KAKAO)

        // then
        assertEquals(FailureReasonType.INVALID_CLIENT, result)
    }

    @Test
    @DisplayName("Microsoft 매퍼: AADSTS50001 오류 매핑")
    fun testMicrosoftAADSTSMapping() {
        // given
        val sut = MicrosoftOAuth2FailureMapper()
        val error = OAuth2Error("AADSTS50001", "Application not found", null)
        val exception = OAuth2AuthenticationException(error)

        // when
        val result = sut.mapException(exception, ProviderType.MICROSOFT)

        // then
        assertEquals(FailureReasonType.INVALID_CLIENT, result)
    }

    @Test
    @DisplayName("GitHub 매퍼: bad_verification_code 오류 매핑")
    fun testGitHubBadVerificationCodeMapping() {
        // given
        val sut = GitHubOAuth2FailureMapper()
        val error = OAuth2Error("bad_verification_code", "Code expired", null)
        val exception = OAuth2AuthenticationException(error)

        // when
        val result = sut.mapException(exception, ProviderType.GITHUB)

        // then
        assertEquals(FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED, result)
    }

    @Test
    @DisplayName("기본 매퍼: 표준 OAuth2 오류 매핑")
    fun testDefaultMapperStandardErrors() {
        // given
        val sut = DefaultOAuth2FailureMapper()

        // when & then
        val invalidClientError = OAuth2Error("invalid_client", "Invalid client", null)
        val invalidClientException = OAuth2AuthenticationException(invalidClientError)
        assertEquals(FailureReasonType.INVALID_CLIENT,
            sut.mapException(invalidClientException, ProviderType.OIDC))

        val accessDeniedError = OAuth2Error("access_denied", "Access denied", null)
        val accessDeniedException = OAuth2AuthenticationException(accessDeniedError)
        assertEquals(FailureReasonType.ACCESS_DENIED,
            sut.mapException(accessDeniedException, ProviderType.OIDC))
    }

    @Test
    @DisplayName("제공자별 매퍼: canHandle 메서드 테스트")
    fun testMapperCanHandle() {
        // given
        val googleMapper = GoogleOAuth2FailureMapper()
        val kakaoMapper = KakaoOAuth2FailureMapper()
        val microsoftMapper = MicrosoftOAuth2FailureMapper()
        val githubMapper = GitHubOAuth2FailureMapper()
        val defaultMapper = DefaultOAuth2FailureMapper()

        // when & then
        assertTrue(googleMapper.canHandle(ProviderType.GOOGLE))
        assertFalse(googleMapper.canHandle(ProviderType.KAKAO))

        assertTrue(kakaoMapper.canHandle(ProviderType.KAKAO))
        assertFalse(kakaoMapper.canHandle(ProviderType.GOOGLE))

        assertTrue(microsoftMapper.canHandle(ProviderType.MICROSOFT))
        assertFalse(microsoftMapper.canHandle(ProviderType.GITHUB))

        assertTrue(githubMapper.canHandle(ProviderType.GITHUB))
        assertFalse(githubMapper.canHandle(ProviderType.MICROSOFT))

        // Default mapper는 모든 제공자를 처리할 수 있음
        assertTrue(defaultMapper.canHandle(ProviderType.GOOGLE))
        assertTrue(defaultMapper.canHandle(ProviderType.KAKAO))
        assertTrue(defaultMapper.canHandle(ProviderType.OIDC))
    }

    @Test
    @DisplayName("매퍼 체인: 처리할 수 없는 오류는 null 반환")
    fun testMapperReturnsNullForUnhandledErrors() {
        // given
        val sut = GoogleOAuth2FailureMapper()
        val error = OAuth2Error("unknown_error", "Unknown error", null)
        val exception = OAuth2AuthenticationException(error)

        // when
        val result = sut.mapException(exception, ProviderType.GOOGLE)

        // then
        assertNull(result)
    }

    @Test
    @DisplayName("Rate limit 오류 매핑 테스트")
    fun testRateLimitMapping() {
        // given
        val googleMapper = GoogleOAuth2FailureMapper()
        val error = OAuth2Error("quota_exceeded", "API quota exceeded", null)
        val exception = OAuth2AuthenticationException(error)

        // when
        val result = googleMapper.mapException(exception, ProviderType.GOOGLE)

        // then
        assertEquals(FailureReasonType.RATE_LIMIT_EXCEEDED, result)
    }
}