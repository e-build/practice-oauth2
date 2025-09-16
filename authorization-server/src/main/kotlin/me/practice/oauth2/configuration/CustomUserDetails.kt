package me.practice.oauth2.configuration

import me.practice.oauth2.entity.IoIdpAccount
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * IoIdpAccount 엔티티를 Spring Security UserDetails로 감싸는 클래스
 */
class CustomUserDetails(
	private val account: IoIdpAccount,
) : UserDetails {

	override fun getAuthorities(): Collection<GrantedAuthority> {
		// role 필드를 기반으로 권한 생성
		return listOf(TokenClaimConstants.DEFAULT_ROLE).map {
			SimpleGrantedAuthority("ROLE_${it.trim().uppercase()}")
		}
	}

	override fun getPassword(): String? {
		return account.pwd
	}

	override fun getUsername(): String {
		// email을 username으로 사용
		return account.email ?: account.id
	}

	override fun isAccountNonExpired(): Boolean {
		// 계정 삭제 날짜가 있으면 만료된 것으로 간주
		return account.delDt == null
	}

	override fun isAccountNonLocked(): Boolean {
		// BLOCKED 상태가 아니면 잠기지 않은 것으로 간주
		return account.status != "BLOCKED"
	}

	override fun isCredentialsNonExpired(): Boolean {
		// 비밀번호 업데이트 날짜 기준으로 90일 이내면 유효
		return account.pwdUpdateDt?.let { updateDt ->
			val now = java.time.LocalDateTime.now()
			val daysSinceUpdate = java.time.Duration.between(updateDt, now).toDays()
			daysSinceUpdate <= 90
		} ?: true // 업데이트 날짜가 없으면 유효한 것으로 간주
	}

	override fun isEnabled(): Boolean {
		// 계정 활성화 상태 확인 (status가 ACTIVE이고 삭제되지 않은 경우)
		return account.status == "ACTIVE" && account.delDt == null
	}

	/**
	 * 원본 IoIdpAccount 엔티티에 접근하기 위한 메서드
	 */
	fun getAccount(): IoIdpAccount {
		return account
	}
}