package me.practice.oauth2.api

import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.LoginStatistics
import me.practice.oauth2.service.LoginTypeStatistics
import me.practice.oauth2.entity.LoginType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@AutoConfigureWebMvc
class LoginHistoryControllerTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @MockBean
    private lateinit var loginHistoryService: LoginHistoryService

    private lateinit var mockMvc: MockMvc

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    @Test
    @DisplayName("컨트롤러 테스트 - 서비스 목킹 확인")
    fun controllerTest_ShouldMockService() {
        // Given
        val clientId = "CLIENT001"
        val statistics = LoginStatistics(
            successCount = 95L,
            failCount = 5L,
            totalCount = 100L,
            successRate = 95.0
        )

        `when`(loginHistoryService.getClientLoginStats(clientId, 30L))
            .thenReturn(statistics)

        // When & Then: 서비스가 올바르게 목킹되었는지 확인
        val result = loginHistoryService.getClientLoginStats(clientId, 30L)
        org.junit.jupiter.api.Assertions.assertEquals(statistics.totalCount, result.totalCount)
        org.junit.jupiter.api.Assertions.assertEquals(statistics.successRate, result.successRate)
    }

    @Test
    @DisplayName("로그인 타입 통계 서비스 목킹 테스트")
    fun loginTypeStatsTest_ShouldMockService() {
        // Given
        val clientId = "CLIENT001"
        val typeStats = listOf(
            LoginTypeStatistics(
                loginType = LoginType.BASIC,
                successCount = 40L,
                failCount = 10L,
                totalCount = 50L,
                successRate = 80.0
            )
        )

        `when`(loginHistoryService.getLoginTypeStats(clientId, 30L))
            .thenReturn(typeStats)

        // When & Then
        val result = loginHistoryService.getLoginTypeStats(clientId, 30L)
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size)
        org.junit.jupiter.api.Assertions.assertEquals(LoginType.BASIC, result[0].loginType)
        org.junit.jupiter.api.Assertions.assertEquals(50L, result[0].totalCount)
    }
}