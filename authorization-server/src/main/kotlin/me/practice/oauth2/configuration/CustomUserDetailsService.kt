package me.practice.oauth2.configuration

import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.entity.IoIdpClient
import me.practice.oauth2.entity.IoIdpClientRepository
import me.practice.oauth2.service.AccountIdentifierType
import me.practice.oauth2.utils.AccountIdentifierParser
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 데이터베이스 기반 사용자 인증을 위한 UserDetailsService 구현체
 * email과 phone 모두를 username으로 지원
 */
@Service
class CustomUserDetailsService(
	private val accountRepository: IoIdpAccountRepository,
	private val ioIdpClientRepository: IoIdpClientRepository,
	private val accountIdentifierParser: AccountIdentifierParser,
) : UserDetailsService {

	/**
	 * username으로 사용자를 조회합니다.
	 *
	 * 지원 형식:
	 * 1. "email@domain.com" - 이메일 주소
	 * 2. "01012345678" - 휴대폰 번호
	 */
	@Transactional(readOnly = true)
	override fun loadUserByUsername(username: String): UserDetails {
		val account = findAccountByIdentifier(username)
			?: throw UsernameNotFoundException("User not found: $username")

		// 삭제된 계정인지 확인
		if (account.delDt != null) {
			throw UsernameNotFoundException("Account has been deleted: $username")
		}

		return CustomUserDetails(account)
	}

	/**
	 * OAuth Client ID를 통해 해당 Shopl Client의 사용자만 조회하도록 제한하는 메서드
	 * 추후 멀티테넌트 보안 강화 시 사용
	 */
	@Transactional(readOnly = true)
	fun loadUserByUsernameWithClientValidation(
		username: String,
		idpClientId: String,
	): UserDetails {
		// OAuth Client가 속한 Shopl Client 확인
		val idpClient: IoIdpClient = ioIdpClientRepository.findByClientId(idpClientId)
			?: throw IllegalArgumentException("IDP Client not found $idpClientId: $username")
		// 해당 Shopl Client의 사용자만 조회
		val account = findAccountByShoplClientAndIdentifier(idpClient.shoplClientId, username)
			?: throw UsernameNotFoundException("User not found in client ${idpClient.shoplClientId}: $username")

		return CustomUserDetails(account)
	}

	/**
	 * shopl client ID와 함께 사용자 식별자로 계정을 찾습니다.
	 */
	private fun findAccountByShoplClientAndIdentifier(
		shoplClientId: String,
		userIdentifier: String,
	): IoIdpAccount? {
		return when (accountIdentifierParser.parse(userIdentifier)) {
			AccountIdentifierType.EMAIL -> {
				accountRepository.findByShoplClientIdAndEmail(shoplClientId, userIdentifier)
			}

			AccountIdentifierType.PHONE -> {
				val normalizedPhone = accountIdentifierParser.normalizePhoneNumber(userIdentifier)
				accountRepository.findByShoplClientIdAndPhone(shoplClientId, normalizedPhone)
					?: accountRepository.findByShoplClientIdAndPhone(shoplClientId, userIdentifier) // 원본도 시도
			}

			AccountIdentifierType.UNKNOWN -> {
				// 이메일, 휴대폰이 아닌 경우 shopl_user_id로 간주
				accountRepository.findByShoplClientIdAndShoplUserId(shoplClientId, userIdentifier)
			}
		}
	}

	/**
	 * 단순 식별자로 계정을 찾습니다 (모든 shopl client에서 검색).
	 */
	private fun findAccountByIdentifier(identifier: String): IoIdpAccount? {
		return when (accountIdentifierParser.parse(identifier)) {
			AccountIdentifierType.EMAIL -> {
				accountRepository.findByEmail(identifier)
			}

			AccountIdentifierType.PHONE -> {
				val normalizedPhone = accountIdentifierParser.normalizePhoneNumber(identifier)
				accountRepository.findByPhone(normalizedPhone) ?: accountRepository.findByPhone(identifier) // 원본도 시도
			}

			AccountIdentifierType.UNKNOWN -> {
				// 이메일, 휴대폰이 아닌 경우는 지원하지 않음
				null
			}
		}
	}
}