package me.practice.oauth2.infrastructure.redis.reconstructor

import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.infrastructure.redis.extractor.ProviderUserIdExtractor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.any
import java.time.LocalDateTime
import java.util.*

class SsoAccountReconstructorTest {

    private lateinit var ioIdpAccountRepository: IoIdpAccountRepository
    private lateinit var mockExtractor1: ProviderUserIdExtractor
    private lateinit var mockExtractor2: ProviderUserIdExtractor
    private lateinit var ssoAccountReconstructor: SsoAccountReconstructor

    @BeforeEach
    fun setUp() {
        ioIdpAccountRepository = mock(IoIdpAccountRepository::class.java)
        mockExtractor1 = mock(ProviderUserIdExtractor::class.java)
        mockExtractor2 = mock(ProviderUserIdExtractor::class.java)

        // 우선순위 설정
        whenever(mockExtractor1.getPriority()).thenReturn(10)
        whenever(mockExtractor2.getPriority()).thenReturn(20)

        // 기본적으로 모든 맵을 처리할 수 있도록 설정
        whenever(mockExtractor1.canHandle(any())).thenReturn(true)
        whenever(mockExtractor2.canHandle(any())).thenReturn(true)

        ssoAccountReconstructor = SsoAccountReconstructor(
            listOf(mockExtractor2, mockExtractor1) // 순서 바꿔서 입력해도 우선순위로 정렬됨
        )
    }

    @Test
    fun `이메일로 기존 계정 찾기 성공`() {
        // given
        val email = "test@example.com"
        val shoplClientId = "CLIENT001"
        val principalMap = mapOf(
            "email" to email,
            "name" to "Test User",
            "client_id" to shoplClientId
        )

        val existingAccount = IoIdpAccount(
            id = "existing_account_123",
            shoplClientId = shoplClientId,
            shoplUserId = "existing_user",
            shoplLoginId = email,
            email = email,
            name = "Test User",
            status = "ACTIVE",
            isCertEmail = true,
            isTempPwd = false,
            regDt = LocalDateTime.now()
        )

        whenever(ioIdpAccountRepository.findByShoplClientIdAndEmail(shoplClientId, email))
            .thenReturn(existingAccount)

        // when
        val result = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)

        // then
        assertEquals(existingAccount, result)
    }

    @Test
    fun `생성된 계정 ID로 기존 계정 찾기 성공`() {
        // given
        val providerUserId = "provider_user_123"
        val shoplClientId = "CLIENT001"
        val principalMap = mapOf(
            "sub" to providerUserId,
            "name" to "Test User",
            "client_id" to shoplClientId
        )

        val generatedAccountId = "sso_$providerUserId"
        val existingAccount = IoIdpAccount(
            id = generatedAccountId,
            shoplClientId = shoplClientId,
            shoplUserId = providerUserId,
            shoplLoginId = "user@example.com",
            email = null,
            name = "Test User",
            status = "ACTIVE",
            isCertEmail = false,
            isTempPwd = false,
            regDt = LocalDateTime.now()
        )

        // 이메일로 찾기는 실패 (이메일이 없음)
        whenever(ioIdpAccountRepository.findByShoplClientIdAndEmail(anyString(), any()))
            .thenReturn(null)

        // ID로 찾기는 성공
        whenever(ioIdpAccountRepository.findById(generatedAccountId))
            .thenReturn(Optional.of(existingAccount))

        // 추출기 설정
        whenever(mockExtractor1.extractUserId(principalMap)).thenReturn(providerUserId)

        // when
        val result = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)

        // then
        assertEquals(existingAccount, result)
    }

    @Test
    fun `새로운 계정 생성 - 이메일 있는 경우`() {
        // given
        val providerUserId = "new_provider_user_123"
        val email = "newuser@example.com"
        val name = "New User"
        val shoplClientId = "CLIENT001"
        val principalMap = mapOf(
            "sub" to providerUserId,
            "email" to email,
            "name" to name,
            "client_id" to shoplClientId
        )

        // 기존 계정 없음
        whenever(ioIdpAccountRepository.findByShoplClientIdAndEmail(anyString(), anyString()))
            .thenReturn(null)
        whenever(ioIdpAccountRepository.findById(anyString()))
            .thenReturn(Optional.empty())

        // 추출기 설정
        whenever(mockExtractor1.extractUserId(principalMap)).thenReturn(providerUserId)

        // when
        val result = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)

        // then
        assertEquals("sso_$providerUserId", result.id)
        assertEquals(shoplClientId, result.shoplClientId)
        assertEquals(providerUserId, result.shoplUserId)
        assertEquals(email, result.shoplLoginId)
        assertEquals(email, result.email)
        assertEquals(name, result.name)
        assertEquals("ACTIVE", result.status)
        assertTrue(result.isCertEmail)
        assertFalse(result.isTempPwd)
        assertNotNull(result.regDt)
    }

    @Test
    fun `새로운 계정 생성 - 이메일 없는 경우`() {
        // given
        val providerUserId = "new_provider_user_456"
        val shoplClientId = "CLIENT002"
        val principalMap = mapOf(
            "id" to providerUserId,
            "name" to "User Without Email",
            "aud" to shoplClientId
        )

        // 기존 계정 없음
        whenever(ioIdpAccountRepository.findByShoplClientIdAndEmail(anyString(), any()))
            .thenReturn(null)
        whenever(ioIdpAccountRepository.findById(anyString()))
            .thenReturn(Optional.empty())

        // 추출기 설정
        whenever(mockExtractor1.extractUserId(principalMap)).thenReturn(null)
        whenever(mockExtractor2.extractUserId(principalMap)).thenReturn(providerUserId)

        // when
        val result = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)

        // then
        assertEquals("sso_$providerUserId", result.id)
        assertEquals(shoplClientId, result.shoplClientId)
        assertEquals(providerUserId, result.shoplUserId)
        assertEquals("${providerUserId}@sso.fallback", result.shoplLoginId)
        assertNull(result.email)
        assertEquals("User Without Email", result.name)
        assertEquals("ACTIVE", result.status)
        assertFalse(result.isCertEmail)
        assertFalse(result.isTempPwd)
        assertNotNull(result.regDt)
    }

    @Test
    fun `모든 추출기가 실패하면 예외 발생`() {
        // given
        val principalMap = mapOf(
            "unknown_field" to "some_value"
        )

        // 추출기들이 모두 null 반환
        whenever(mockExtractor1.extractUserId(principalMap)).thenReturn(null)
        whenever(mockExtractor2.extractUserId(principalMap)).thenReturn(null)

        // when & then
        assertThrows(IllegalArgumentException::class.java) {
            ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)
        }
    }

    @Test
    fun `기본 클라이언트 ID 사용`() {
        // given
        val providerUserId = "user_123"
        val principalMap = mapOf(
            "sub" to providerUserId,
            "name" to "Test User"
            // client_id나 aud 없음
        )

        // 기존 계정 없음
        whenever(ioIdpAccountRepository.findByShoplClientIdAndEmail(anyString(), any()))
            .thenReturn(null)
        whenever(ioIdpAccountRepository.findById(anyString()))
            .thenReturn(Optional.empty())

        // 추출기 설정
        whenever(mockExtractor1.extractUserId(principalMap)).thenReturn(providerUserId)

        // when
        val result = ssoAccountReconstructor.findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)

        // then
        assertEquals("CLIENT001", result.shoplClientId) // 기본값
    }
}