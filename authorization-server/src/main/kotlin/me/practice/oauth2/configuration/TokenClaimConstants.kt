package me.practice.oauth2.configuration

/**
 * Security 관련 상수 정의
 */
object TokenClaimConstants {
    const val DEFAULT_ROLE = "STAFF"
    const val LEADER_ROLE = "LEADER"
    const val ADMIN_ROLE = "ADMIN"

    // JWT 클레임 키
    const val CLAIM_ACCOUNT_ID = "account_id"
    const val CLAIM_SHOPL_CLIENT_ID = "shopl_client_id" 
    const val CLAIM_SHOPL_USER_ID = "shopl_user_id"
    const val CLAIM_EMAIL = "email"
    const val CLAIM_NAME = "name"
    const val CLAIM_ROLE = "role"
    const val CLAIM_USERNAME = "username"
}