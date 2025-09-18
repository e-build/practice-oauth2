package me.practice.oauth2.configuration

import me.practice.oauth2.entity.*
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.testbase.IntegrationTestBase
import me.practice.oauth2.testbase.AuthenticationTestUtils
import me.practice.oauth2.domain.IdpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.mockito.Mockito.*
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.*

class CustomAuthenticationProviderIT(
	private val sut: BasicAuthenticationProvider,
	private val loginHistoryService: LoginHistoryService,
	private val loginHistoryRepository: IoIdpLoginHistoryRepository
) : IntegrationTestBase() {

    @MockBean
    private lateinit var userDetailsService: CustomUserDetailsService

    @MockBean
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    override fun setUp() {
        super.setUp()
        loginHistoryRepository.deleteAll()
        reset(userDetailsService, passwordEncoder)
    }

    @Test
    @DisplayName("인증 성공 시 SUCCESS 이력이 저장된다")
    fun authenticateSuccess_ShouldRecordSuccessHistory() {
        // Given
        val loginId = "test@example.com"
        val rawPassword = "password123"
        val encodedPassword = "encodedPassword123"
        val account = AuthenticationTestUtils.createTestAccount(
            pwd = encodedPassword,
            status = "ACTIVE"
        )
        val userDetails = AuthenticationTestUtils.createTestUserDetails(account)
        val authentication = UsernamePasswordAuthenticationToken(loginId, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // Mock 설정
        `when`(userDetailsService.loadUserByUsername(loginId)).thenReturn(userDetails)
        `when`(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true)

        // RequestContextHolder 설정
        val requestAttributes = ServletRequestAttributes(mockRequest)
        RequestContextHolder.setRequestAttributes(requestAttributes)

        try {
            // When
            val result = sut.authenticate(authentication)

            // Then
            assertNotNull(result)
            assertTrue(result.isAuthenticated)

            // 로그인 이력 확인
            val histories = loginHistoryRepository.findAll()
            assertEquals(1, histories.size)

            val history = histories[0]
            assertEquals(TEST_CLIENT_ID, history.shoplClientId)
            assertEquals(TEST_USER_ID, history.shoplUserId)
            assertEquals(IdpClient.Platform.DASHBOARD, history.platform)
            assertEquals(LoginType.BASIC, history.loginType)
            assertEquals(LoginResult.SUCCESS, history.result)
            assertEquals(TEST_SESSION_ID, history.sessionId)
            assertNull(history.failureReason)
            assertNotNull(history.regDt)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("인증 실패 시 FAIL 이력이 저장되고 실패 원인이 기록된다")
    fun authenticateFailure_ShouldRecordFailHistoryWithReason() {
        // Given
        val loginId = "test@example.com"
        val rawPassword = "wrongPassword"
        val encodedPassword = "encodedPassword123"
        val account = AuthenticationTestUtils.createTestAccount(
            pwd = encodedPassword,
            status = "ACTIVE"
        )
        val userDetails = AuthenticationTestUtils.createTestUserDetails(account)
        val authentication = UsernamePasswordAuthenticationToken(loginId, rawPassword)
        val mockRequest = AuthenticationTestUtils.createMockRequest()

        // Mock 설정
        `when`(userDetailsService.loadUserByUsername(loginId)).thenReturn(userDetails)
        `when`(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false)

        // RequestContextHolder 설정
        val requestAttributes = ServletRequestAttributes(mockRequest)
        RequestContextHolder.setRequestAttributes(requestAttributes)

        try {
            // When & Then
            assertFailsWith<BadCredentialsException> {
                sut.authenticate(authentication)
            }

            // 로그인 이력 확인
            val histories = loginHistoryRepository.findAll()
            assertEquals(1, histories.size)

            val history = histories[0]
            assertEquals(TEST_CLIENT_ID, history.shoplClientId)
            assertEquals(TEST_USER_ID, history.shoplUserId)
            assertEquals(IdpClient.Platform.DASHBOARD, history.platform)
            assertEquals(LoginType.BASIC, history.loginType)
            assertEquals(LoginResult.FAIL, history.result)
            assertEquals(FailureReasonType.INVALID_CREDENTIALS, history.failureReason)
            assertEquals(TEST_SESSION_ID, history.sessionId)
            assertNotNull(history.regDt)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    @DisplayName("계정 비활성화 시 DisabledException 발생하고 이력이 기록되지 않는다")
    fun authenticateDisabledAccount_ShouldThrowDisabledException() {
        // Given
        val loginId = "test@example.com"
        val rawPassword = "password123"
        val account = AuthenticationTestUtils.createTestAccount(
            status = "INACTIVE" // 비활성화된 계정
        )
        val userDetails = AuthenticationTestUtils.createTestUserDetails(account)
        val authentication = UsernamePasswordAuthenticationToken(loginId, rawPassword)

        // Mock 설정
        `when`(userDetailsService.loadUserByUsername(loginId)).thenReturn(userDetails)

        // When & Then
        assertFailsWith<DisabledException> {
            sut.authenticate(authentication)
        }

        // 로그인 이력이 기록되지 않았는지 확인
        val histories = loginHistoryRepository.findAll()
        assertEquals(0, histories.size)
    }

    // 계정 잠김 기능은 현재 IoIdpAccount에 구현되지 않음
    // 필요시 추후 추가 구현

    @Test
    @DisplayName("RequestContextHolder가 없어도 인증은 성공한다 (기본 세션 ID 사용)")
    fun authenticateSuccess_WithoutRequestContext_ShouldStillSucceed() {
        // Given
        val loginId = "test@example.com"
        val rawPassword = "password123"
        val encodedPassword = "encodedPassword123"
        val account = AuthenticationTestUtils.createTestAccount(
            pwd = encodedPassword,
            status = "ACTIVE"
        )
        val userDetails = AuthenticationTestUtils.createTestUserDetails(account)
        val authentication = UsernamePasswordAuthenticationToken(loginId, rawPassword)

        // Mock 설정
        `when`(userDetailsService.loadUserByUsername(loginId)).thenReturn(userDetails)
        `when`(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true)

        // RequestContextHolder를 설정하지 않음 (기본 세션 ID "unknown-session" 사용)

        // When
        val result = sut.authenticate(authentication)

        // Then
        assertNotNull(result)
        assertTrue(result.isAuthenticated)

        // 로그인 이력이 기본 세션 ID로 기록되어야 함
        val histories = loginHistoryRepository.findAll()
        assertEquals(1, histories.size)
        assertEquals("unknown-session", histories[0].sessionId)
    }

    @Test
    @DisplayName("supports 메서드가 올바르게 동작한다")
    fun supports_ShouldReturnTrueForUsernamePasswordAuthenticationToken() {
        // Given & When & Then
        assertTrue(sut.supports(UsernamePasswordAuthenticationToken::class.java))
        assertFalse(sut.supports(String::class.java))
    }
}