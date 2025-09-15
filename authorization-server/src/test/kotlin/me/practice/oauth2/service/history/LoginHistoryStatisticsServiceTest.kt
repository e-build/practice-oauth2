package me.practice.oauth2.service.history

import me.practice.oauth2.entity.LoginResult
import me.practice.oauth2.entity.LoginType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LoginHistoryStatisticsServiceTest {

    @Test
    fun `LoginStatistics 데이터 클래스 생성 테스트`() {
        // given & when
        val stats = LoginStatistics(
            successCount = 80L,
            failCount = 20L,
            totalCount = 100L,
            successRate = 80.0
        )

        // then
        assertEquals(80L, stats.successCount)
        assertEquals(20L, stats.failCount)
        assertEquals(100L, stats.totalCount)
        assertEquals(80.0, stats.successRate)
    }

    @Test
    fun `LoginTypeStatistics 데이터 클래스 생성 테스트`() {
        // given & when
        val typeStats = LoginTypeStatistics(
            loginType = LoginType.BASIC,
            successCount = 50L,
            failCount = 10L,
            totalCount = 60L,
            successRate = 83.33
        )

        // then
        assertEquals(LoginType.BASIC, typeStats.loginType)
        assertEquals(50L, typeStats.successCount)
        assertEquals(10L, typeStats.failCount)
        assertEquals(60L, typeStats.totalCount)
        assertEquals(83.33, typeStats.successRate)
    }

    @Test
    fun `StatisticsQuery 데이터 클래스 생성 테스트`() {
        // given & when
        val query = StatisticsQuery("CLIENT001", 30)

        // then
        assertEquals("CLIENT001", query.shoplClientId)
        assertEquals(30L, query.daysBack)
    }

    @Test
    fun `FailedLoginCountQuery 데이터 클래스 생성 테스트`() {
        // given & when
        val query = FailedLoginCountQuery("user123", 24)

        // then
        assertEquals("user123", query.shoplUserId)
        assertEquals(24L, query.hoursBack)
    }

    @Test
    fun `IpBasedLoginQuery 데이터 클래스 생성 테스트`() {
        // given & when
        val query = IpBasedLoginQuery("192.168.1.100", 1)

        // then
        assertEquals("192.168.1.100", query.ipAddress)
        assertEquals(1L, query.hoursBack)
    }
}