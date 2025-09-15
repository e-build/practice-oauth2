package me.practice.oauth2.service

import me.practice.oauth2.configuration.CustomUserDetails
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordValidator(
    private val passwordEncoder: PasswordEncoder
) {

    fun validatePassword(userDetails: CustomUserDetails, rawPassword: String) {
        if (userDetails.password != null && !passwordEncoder.matches(rawPassword, userDetails.password)) {
            throw BadCredentialsException("Invalid password")
        }
    }
}