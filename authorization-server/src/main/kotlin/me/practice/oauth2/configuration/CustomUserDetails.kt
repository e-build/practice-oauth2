package me.practice.oauth2.configuration

import me.practice.oauth2.entity.IoIdpAccount
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.time.LocalDateTime



/**
 * IoIdpAccount 엔티티를 Spring Security UserDetails로 감싸는 클래스
 */
class CustomUserDetails(
    private val account: IoIdpAccount
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        // role 필드를 기반으로 권한 생성
        return listOf(SecurityConstants.DEFAULT_ROLE).map {
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
        return true // 필요시 계정 만료 로직 구현
    }

    override fun isAccountNonLocked(): Boolean {
        // 계정 잠김 상태 확인 (locked_until_dt가 현재 시간보다 이전이면 잠김 해제)
        return account.lockedUntilDt?.let { 
            LocalDateTime.now().isAfter(it) 
        } ?: true
    }

    override fun isCredentialsNonExpired(): Boolean {
        return true // 필요시 비밀번호 만료 로직 구현
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

    /**
     * Shopl Client ID 반환
     */
    fun getShoplClientId(): String {
        return account.shoplClientId
    }

    /**
     * 계정 ID 반환  
     */
    fun getAccountId(): String {
        return account.id
    }
}