package me.practice.oauth2.testbase

import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 통합 테스트용 공통 베이스 클래스
 * 테스트 컨테이너 및 공통 설정을 포함
 */
@DataJpaTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Testcontainers
@Transactional
abstract class IntegrationTestBase {

    companion object {
        const val TEST_CLIENT_ID = "CLIENT001"
        const val TEST_USER_ID = "USER001"
        const val TEST_SESSION_ID = "SESSION001"
        const val TEST_USER_AGENT = "Mozilla/5.0 Test Browser"
        const val TEST_IP_ADDRESS = "127.0.0.1"

        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.0")
            .withDatabaseName("shopl_authorization")
            .withUsername("test")
            .withPassword("test")
    }

    @BeforeEach
    open fun setUp() {
        // 서브 클래스에서 오버라이드할 수 있도록 open으로 선언
    }
}