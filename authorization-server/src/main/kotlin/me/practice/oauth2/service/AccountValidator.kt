package me.practice.oauth2.service

import me.practice.oauth2.configuration.CustomUserDetails
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.stereotype.Component

@Component
class AccountValidator {

    fun validateAccountStatus(userDetails: CustomUserDetails) {
        if (!userDetails.isEnabled) {
            throw DisabledException("Account is disabled: ${userDetails.username}")
        }

        if (!userDetails.isAccountNonLocked) {
            throw LockedException("Account is locked: ${userDetails.username}")
        }
    }
}