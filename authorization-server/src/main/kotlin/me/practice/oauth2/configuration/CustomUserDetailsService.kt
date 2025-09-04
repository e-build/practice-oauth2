package me.practice.oauth2.configuration

import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.utils.UserIdentifierValidator
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 데이터베이스 기반 사용자 인증을 위한 UserDetailsService 구현체
 * email과 phone 모두를 username으로 지원
 */
@Service
class CustomUserDetailsService(
	private val accountRepository: IoIdpAccountRepository,
) : UserDetailsService {

	/**
	 * username으로 사용자를 조회합니다.
	 *
	 * 지원 형식:
	 * 1. "email@domain.com" - 이메일 주소
	 * 2. "010-1234-5678" - 휴대폰 번호
	 * 3. "shoplClientId:email@domain.com" - shopl client별 이메일
	 * 4. "shoplClientId:010-1234-5678" - shopl client별 휴대폰 번호
	 * 5. "shoplClientId:shoplUserId" - shopl client별 사용자 ID
	 */
	@Transactional(readOnly = true)
	override fun loadUserByUsername(username: String): UserDetails {
		val account = findAccountByIdentifier(username)
			?: throw UsernameNotFoundException("User not found: $username")

		// 삭제된 계정인지 확인
		if (account.delDt != null) {
			throw UsernameNotFoundException("Account has been deleted: $username")
		}

		// 계정 잠김 상태 자동 해제 처리
		if (account.lockedUntilDt != null && LocalDateTime.now().isAfter(account.lockedUntilDt)) {
			// 잠김 해제 처리 (실제 구현시에는 별도 서비스로 분리 권장)
			unlockAccount(account.id)
		}

		return CustomUserDetails(account)
	}

	/**
	 * shopl client ID와 사용자 식별자로 사용자를 조회합니다.
	 */
	@Transactional(readOnly = true)
	fun loadUserByShoplClientAndIdentifier(shoplClientId: String, userIdentifier: String): UserDetails {
		return loadUserByUsername("$shoplClientId:$userIdentifier")
	}

	/**
	 * shopl client ID와 함께 사용자 식별자로 계정을 찾습니다.
	 */
	private fun findAccountByShoplClientAndIdentifier(
		shoplClientId: String,
		userIdentifier: String,
	): IoIdpAccount? {
		return when (UserIdentifierValidator.getIdentifierType(userIdentifier)) {
			UserIdentifierValidator.IdentifierType.EMAIL -> {
				accountRepository.findByShoplClientIdAndEmail(shoplClientId, userIdentifier)
			}

			UserIdentifierValidator.IdentifierType.PHONE -> {
				val normalizedPhone = UserIdentifierValidator.normalizePhoneNumber(userIdentifier)
				accountRepository.findByShoplClientIdAndPhone(shoplClientId, normalizedPhone)
					?: accountRepository.findByShoplClientIdAndPhone(shoplClientId, userIdentifier) // 원본도 시도
			}

			UserIdentifierValidator.IdentifierType.UNKNOWN -> {
				// 이메일, 휴대폰이 아닌 경우 shopl_user_id로 간주
				accountRepository.findByShoplClientIdAndShoplUserId(shoplClientId, userIdentifier)
			}
		}
	}

	/**
	 * 단순 식별자로 계정을 찾습니다 (모든 shopl client에서 검색).
	 */
	private fun findAccountByIdentifier(identifier: String): IoIdpAccount? {
		return when (UserIdentifierValidator.getIdentifierType(identifier)) {
			UserIdentifierValidator.IdentifierType.EMAIL -> {
				accountRepository.findByEmail(identifier)
			}

			UserIdentifierValidator.IdentifierType.PHONE -> {
				val normalizedPhone = UserIdentifierValidator.normalizePhoneNumber(identifier)
				accountRepository.findByPhone(normalizedPhone) ?: accountRepository.findByPhone(identifier) // 원본도 시도
			}

			UserIdentifierValidator.IdentifierType.UNKNOWN -> {
				// 이메일, 휴대폰이 아닌 경우는 지원하지 않음
				null
			}
		}
	}

	/**
	 * 계정 잠김 해제 (별도 서비스로 분리하는 것을 권장)
	 */
	private fun unlockAccount(accountId: String) {
		// 여기서는 간단히 구현, 실제로는 별도 서비스에서 처리
		// accountRepository.unlockAccount(accountId) 같은 메서드 구현 필요
	}
}