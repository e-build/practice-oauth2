package me.practice.oauth2.infrastructure.redis.reconstructor

import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.infrastructure.redis.extractor.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import java.time.LocalDateTime
import java.util.*

/**
 * SsoAccountReconstructor 통합 테스트
 * 실제 ProviderUserIdExtractor 구현체들을 사용하여 테스트
 */
class SsoAccountReconstructorIntegrationTest {

    private lateinit var ioIdpAccountRepository: IoIdpAccountRepository
    private lateinit var ssoAccountReconstructor: SsoAccountReconstructor

    @BeforeEach
    fun setUp() {
        ioIdpAccountRepository = mock(IoIdpAccountRepository::class.java)

        // 실제 구현체들을 사용
        val extractors = listOf(
            GoogleUserIdExtractor(),
            KakaoUserIdExtractor(),
            NaverUserIdExtractor(),
            DefaultUserIdExtractor()
        )

        ssoAccountReconstructor = SsoAccountReconstructor(extractors)
    }

    @Test
    fun `Google 제공자 사용자 ID로 새 계정 생성`() {
        // given
        val googleUserId = "google_user_12345"
        val email = "user@gmail.com"
        val principalMap = mapOf(
            "iss" to "https://accounts.google.com",
            "sub" to googleUserId,
            "email" to email,
            "name" to "Google User"
        )

        // 기존 계정 없음
        whenever(ioIdpAccountRepository.findByShoplClientIdAndEmail("CLIENT001", email))
            .thenReturn(null)
        whenever(ioIdpAccountRepository.findById("sso_$googleUserId"))
            .thenReturn(Optional.empty())

        // when
        val result = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)

        // then
        assertEquals("sso_$googleUserId", result.id)
        assertEquals("CLIENT001", result.shoplClientId) // 기본값
        assertEquals(googleUserId, result.shoplUserId)
        assertEquals(email, result.shoplLoginId)
        assertEquals(email, result.email)
        assertEquals("Google User", result.name)
        assertEquals("ACTIVE", result.status)
        assertTrue(result.isCertEmail)
        assertFalse(result.isTempPwd)

        println("Successfully created Google SSO account: ${result.id}")
    }

    @Test
    fun `Kakao 제공자 사용자 ID로 새 계정 생성`() {
        // given
        val kakaoUserId = "kakao_user_67890"
        val principalMap = mapOf(
            "iss" to "https://kauth.kakao.com",
            "sub" to kakaoUserId,
            "nickname" to "카카오사용자"
        )

        // 기존 계정 없음 (이메일이 없으므로 이메일 검색 생략)
        whenever(ioIdpAccountRepository.findById("sso_$kakaoUserId"))
            .thenReturn(Optional.empty())

        // when
        val result = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)

        // then
        assertEquals("sso_$kakaoUserId", result.id)
        assertEquals("CLIENT001", result.shoplClientId)
        assertEquals(kakaoUserId, result.shoplUserId)
        assertEquals("${kakaoUserId}@sso.fallback", result.shoplLoginId)
        assertNull(result.email)
        assertEquals("카카오사용자", result.name)
        assertEquals("ACTIVE", result.status)
        assertFalse(result.isCertEmail) // 이메일이 없으므로
        assertFalse(result.isTempPwd)

        println("Successfully created Kakao SSO account: ${result.id}")
    }

    @Test
    fun `기존 계정을 이메일로 찾기`() {
        // given
        val email = "existing@example.com"
        val principalMap = mapOf(
            "email" to email,
            "name" to "Existing User"
        )

        val existingAccount = IoIdpAccount(
            id = "existing_account_123",
            shoplClientId = "CLIENT001",
            shoplUserId = "existing_user",
            shoplLoginId = email,
            email = email,
            name = "Existing User",
            status = "ACTIVE",
            isCertEmail = true,
            isTempPwd = false,
            regDt = LocalDateTime.now().minusDays(30)
        )

        whenever(ioIdpAccountRepository.findByShoplClientIdAndEmail("CLIENT001", email))
            .thenReturn(existingAccount)

        // when
        val result = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)

        // then
        assertEquals(existingAccount, result)
        assertEquals("existing_account_123", result.id)

        println("Successfully found existing account by email: ${result.id}")
    }

    @Test
    fun `알 수 없는 제공자일 때 예외 발생`() {
        // given - 모든 추출기가 처리할 수 없는 형태
        val principalMap = mapOf(
            "unknown_field" to "unknown_value",
            "weird_data" to 12345
        )

        // when & then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)
        }

        assertTrue(exception.message!!.contains("Could not extract provider user ID"))
        println("Successfully threw exception for unknown provider: ${exception.message}")
    }
}