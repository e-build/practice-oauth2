package me.practice.oauth2.service

import me.practice.oauth2.configuration.CustomUserDetails
import me.practice.oauth2.exception.AccountExpiredException
import me.practice.oauth2.exception.PasswordExpiredException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.stereotype.Component

@Component
class AccountValidator {

    fun validateAccountStatus(userDetails: CustomUserDetails) {
        // 계정 활성화 상태 확인
        if (!userDetails.isEnabled) {
            throw DisabledException("Account is disabled: ${userDetails.username}")
        }

        // 계정 잠금 상태 확인
        if (!userDetails.isAccountNonLocked) {
            throw LockedException("Account is locked: ${userDetails.username}")
        }

        // 계정 만료 상태 확인
        if (!userDetails.isAccountNonExpired) {
            throw AccountExpiredException(userDetails.username)
        }

        // 비밀번호 만료 상태 확인
        if (!userDetails.isCredentialsNonExpired) {
            throw PasswordExpiredException(userDetails.username)
        }
    }
}