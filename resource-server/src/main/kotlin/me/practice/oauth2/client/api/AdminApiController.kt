package me.practice.oauth2.client.api

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class AdminApiController {

    /**
     * 현재 사용자 정보를 반환하는 API
     */
    @GetMapping("/user-info")
    fun getUserInfo(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Map<String, Any?>> {
        return if (jwt != null) {
            // 모든 클레임 정보 추출
            val allClaims = jwt.claims

            ResponseEntity.ok(mapOf(
                // 기본 사용자 정보
                "username" to jwt.getClaimAsString("username"),
                "email" to jwt.getClaimAsString("email"),
                "name" to jwt.getClaimAsString("name"),
                "role" to jwt.getClaimAsString("role"),

                // Shopl 계정 정보
                "account_id" to jwt.getClaimAsString("account_id"),
                "shopl_client_id" to jwt.getClaimAsString("shopl_client_id"),
                "shopl_user_id" to jwt.getClaimAsString("shopl_user_id"),
                "user_id" to jwt.getClaimAsString("user_id"),

                // JWT 토큰 정보
                "sub" to jwt.getClaimAsString("sub"),
                "iss" to jwt.getClaimAsString("iss"),
                "aud" to jwt.audience,
                "exp" to jwt.getClaim<Long>("exp"),
                "iat" to jwt.getClaim<Long>("iat"),
                "scope" to jwt.getClaimAsString("scope"),

                // 인증 상태
                "authenticated" to true,

                // 모든 클레임 (디버깅용)
                "all_claims" to allClaims
            ))
        } else {
            ResponseEntity.ok(mapOf(
                "authenticated" to false
            ))
        }
    }

    /**
     * 권한 확인 API
     */
    @GetMapping("/check-permission")
    fun checkPermission(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Map<String, Any>> {
        return if (jwt != null) {
            ResponseEntity.ok(mapOf(
                "hasPermission" to true,
                "role" to jwt.getClaimAsString("role")
            ))
        } else {
            ResponseEntity.status(401).body(mapOf(
                "hasPermission" to false,
                "message" to "인증이 필요합니다"
            ))
        }
    }

    /**
     * SSO 설정 목록 조회 API (모의 데이터)
     */
    @GetMapping("/sso/configurations")
    fun getSsoConfigurations(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Any> {
        return if (jwt != null) {
            val companyId = jwt.getClaimAsString("account_id")

            // 모의 데이터 반환
            val mockData = listOf(
                mapOf(
                    "id" to 1,
                    "name" to "Google Workspace SSO",
                    "providerType" to "OIDC",
                    "companyDomain" to "example.com",
                    "status" to "ACTIVE",
                    "createdAt" to "2024-01-15T10:30:00Z",
                    "updatedAt" to "2024-01-20T14:45:00Z",
                    "companyId" to companyId
                ),
                mapOf(
                    "id" to 2,
                    "name" to "Microsoft Azure AD",
                    "providerType" to "SAML",
                    "companyDomain" to "corp.example.com",
                    "status" to "ACTIVE",
                    "createdAt" to "2024-01-10T09:15:00Z",
                    "updatedAt" to "2024-01-18T16:20:00Z",
                    "companyId" to companyId
                ),
                mapOf(
                    "id" to 3,
                    "name" to "Okta SSO",
                    "providerType" to "SAML",
                    "companyDomain" to "startup.com",
                    "status" to "PENDING",
                    "createdAt" to "2024-01-25T11:00:00Z",
                    "updatedAt" to "2024-01-25T11:00:00Z",
                    "companyId" to companyId
                )
            )

            ResponseEntity.ok(mockData)
        } else {
            ResponseEntity.status(401).body(mapOf(
                "error" to "인증이 필요합니다"
            ))
        }
    }
}