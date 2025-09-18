package me.practice.oauth2.testbase

import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 테스트용 계정 및 이력 생성 헬퍼 클래스
 */
@Component
class AccountTestHelper {

	@Autowired
	private lateinit var accountRepository: IoIdpAccountRepository

	@Autowired
	private lateinit var passwordEncoder: PasswordEncoder

	/**
	 * 테스트용 계정 생성 및 저장
	 */
	fun createAndSaveTestAccount(
		id: String = "ACC${System.currentTimeMillis() % 1000000}",
		shoplClientId: String = AuthenticationIntegrationTestBase.TEST_CLIENT_ID,
		shoplUserId: String = AuthenticationIntegrationTestBase.TEST_USER_ID,
		shoplLoginId: String = "test@example.com",
		email: String? = "test@example.com",
		rawPassword: String? = null,
		encodedPassword: String? = null,
		status: String = "ACTIVE",
		pwdUpdateDt: LocalDateTime? = null,
		delDt: LocalDateTime? = null,
	): IoIdpAccount {
		val finalPassword = when {
			encodedPassword != null -> encodedPassword
			rawPassword != null -> passwordEncoder.encode(rawPassword)
			else -> null
		}

		val account = IoIdpAccount(
			id = id,
			shoplClientId = shoplClientId,
			shoplUserId = shoplUserId,
			shoplLoginId = shoplLoginId,
			email = email,
			pwd = finalPassword,
			status = status,
			pwdUpdateDt = pwdUpdateDt,
			delDt = delDt
		)
		return accountRepository.save(account)
	}

	/**
	 * 테스트용 로그인 이력 생성
	 */
	fun createTestLoginHistory(
		idpClientId: String = AuthenticationIntegrationTestBase.TEST_IDP_CLIENT_ID,
		shoplClientId: String = AuthenticationIntegrationTestBase.TEST_CLIENT_ID,
		shoplUserId: String = AuthenticationIntegrationTestBase.TEST_USER_ID,
		loginType: LoginType = LoginType.BASIC,
		result: LoginResult = LoginResult.FAIL,
		failureReason: FailureReasonType = FailureReasonType.INVALID_CREDENTIALS,
		sessionId: String = "session-${System.currentTimeMillis()}",
		regDt: LocalDateTime = LocalDateTime.now().minusMinutes(1),
	): IoIdpUserLoginHistory {
		return IoIdpUserLoginHistory(
			idpClientId = idpClientId,
			shoplClientId = shoplClientId,
			shoplUserId = shoplUserId,
			platform = IdpClient.Platform.DASHBOARD,
			loginType = loginType,
			result = result,
			failureReason = if (result == LoginResult.FAIL) failureReason else null,
			sessionId = sessionId,
			regDt = regDt
		)
	}
}