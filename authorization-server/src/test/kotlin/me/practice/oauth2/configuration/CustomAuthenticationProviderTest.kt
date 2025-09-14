package me.practice.oauth2.configuration

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.service.LoginHistoryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CustomAuthenticationProviderTest {

    private lateinit var sut: CustomAuthenticationProvider
    private lateinit var userDetailsService: CustomUserDetailsService
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var loginHistoryService: LoginHistoryService
    private lateinit var mockAccount: IoIdpAccount
    private lateinit var mockUserDetails: CustomUserDetails
    private lateinit var mockRequest: HttpServletRequest
    private lateinit var mockSession: HttpSession

    @BeforeEach
    fun setUp() {
        userDetailsService = mockk()
        passwordEncoder = mockk()
        loginHistoryService = mockk()
        mockAccount = mockk()
        mockUserDetails = mockk()
        mockRequest = mockk()
        mockSession = mockk()

        sut = CustomAuthenticationProvider(
            userDetailsService,
            passwordEncoder,
            loginHistoryService
        )

        // Mock account 기본 설정
        every { mockAccount.shoplClientId } returns "CLIENT001"
        every { mockAccount.shoplUserId } returns "user123"
        every { mockAccount.id } returns "account-id-123"

        // Mock user details 기본 설정
        every { mockUserDetails.getAccount() } returns mockAccount
        every { mockUserDetails.isEnabled } returns true
        every { mockUserDetails.isAccountNonLocked } returns true
        every { mockUserDetails.password } returns "encodedPassword"
        every { mockUserDetails.authorities } returns emptyList()
        every { mockUserDetails.username } returns "testuser"

        // Mock request and session
        every { mockRequest.session } returns mockSession
        every { mockSession.id } returns "session-123"

        // Mock login history service
        every { loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any(), any()) } returns mockk()
    }

    @Test
    @DisplayName("인증 성공 시 로그인 이력이 기록된다")
    fun authenticateSuccessRecordsLoginHistory() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        
        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { passwordEncoder.matches(password, "encodedPassword") } returns true
        
        // Mock RequestContextHolder
        val mockAttributes = mockk<ServletRequestAttributes>()
        every { mockAttributes.request } returns mockRequest
        mockkStatic(RequestContextHolder::class)
        every { RequestContextHolder.currentRequestAttributes() } returns mockAttributes

        // When
        val result = sut.authenticate(authentication)

        // Then
        assertNotNull(result)
        assertTrue(result.isAuthenticated)
        assertEquals(mockUserDetails, result.principal)

        verify(exactly = 1) {
            loginHistoryService.recordSuccessfulLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                sessionId = "session-123",
                request = mockRequest
            )
        }

        verify(exactly = 0) {
            loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any(), any())
        }

        unmockkStatic(RequestContextHolder::class)
    }

    @Test
    @DisplayName("비밀번호 불일치 시 실패 이력이 기록된다")
    fun authenticateFailureRecordsLoginHistory() {
        // Given
        val username = "testuser"
        val password = "wrongpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        
        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { passwordEncoder.matches(password, "encodedPassword") } returns false
        
        // Mock RequestContextHolder
        val mockAttributes = mockk<ServletRequestAttributes>()
        every { mockAttributes.request } returns mockRequest
        mockkStatic(RequestContextHolder::class)
        every { RequestContextHolder.currentRequestAttributes() } returns mockAttributes

        // When & Then
        assertThrows<BadCredentialsException> {
            sut.authenticate(authentication)
        }

        verify(exactly = 1) {
            loginHistoryService.recordFailedLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                failureReason = FailureReasonType.INVALID_CREDENTIALS,
                sessionId = "session-123",
                request = mockRequest
            )
        }

        verify(exactly = 0) {
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any())
        }

        unmockkStatic(RequestContextHolder::class)
    }

    @Test
    @DisplayName("계정 비활성화 시 로그인 이력을 기록하지 않는다")
    fun disabledAccountDoesNotRecordHistory() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        
        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { mockUserDetails.isEnabled } returns false

        // When & Then
        assertThrows<DisabledException> {
            sut.authenticate(authentication)
        }

        verify(exactly = 0) {
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any())
            loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("계정 잠김 시 로그인 이력을 기록하지 않는다")
    fun lockedAccountDoesNotRecordHistory() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        
        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { mockUserDetails.isAccountNonLocked } returns false

        // When & Then
        assertThrows<LockedException> {
            sut.authenticate(authentication)
        }

        verify(exactly = 0) {
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any())
            loginHistoryService.recordFailedLogin(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("로그인 이력 기록 실패 시 인증은 계속 진행된다")
    fun authenticationContinuesWhenHistoryRecordingFails() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        
        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { passwordEncoder.matches(password, "encodedPassword") } returns true
        every { loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("DB Error")
        
        // Mock RequestContextHolder
        val mockAttributes = mockk<ServletRequestAttributes>()
        every { mockAttributes.request } returns mockRequest
        mockkStatic(RequestContextHolder::class)
        every { RequestContextHolder.currentRequestAttributes() } returns mockAttributes

        // When
        val result = sut.authenticate(authentication)

        // Then
        assertNotNull(result)
        assertTrue(result.isAuthenticated)
        assertEquals(mockUserDetails, result.principal)

        verify(exactly = 1) {
            loginHistoryService.recordSuccessfulLogin(any(), any(), any(), any(), any(), any(), any())
        }

        unmockkStatic(RequestContextHolder::class)
    }

    @Test
    @DisplayName("HTTP 요청이 없는 경우에도 로그인 이력이 기록된다")
    fun recordsHistoryWhenNoHttpRequest() {
        // Given
        val username = "testuser"
        val password = "testpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)
        
        every { userDetailsService.loadUserByUsername(username) } returns mockUserDetails
        every { passwordEncoder.matches(password, "encodedPassword") } returns true
        
        // Mock RequestContextHolder to return null
        mockkStatic(RequestContextHolder::class)
        every { RequestContextHolder.currentRequestAttributes() } throws IllegalStateException("No request")

        // When
        val result = sut.authenticate(authentication)

        // Then
        assertNotNull(result)
        assertTrue(result.isAuthenticated)

        verify(exactly = 1) {
            loginHistoryService.recordSuccessfulLogin(
                shoplClientId = "CLIENT001",
                shoplUserId = "user123",
                platform = IdpClient.Platform.DASHBOARD,
                loginType = LoginType.BASIC,
                sessionId = "unknown-session",
                request = null
            )
        }

        unmockkStatic(RequestContextHolder::class)
    }

    @Test
    @DisplayName("supports 메서드는 UsernamePasswordAuthenticationToken을 지원한다")
    fun supportsUsernamePasswordAuthenticationToken() {
        // When & Then
        assertTrue(sut.supports(UsernamePasswordAuthenticationToken::class.java))
    }
}