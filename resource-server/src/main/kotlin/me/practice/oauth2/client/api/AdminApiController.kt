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

    @GetMapping("/user-info")
    fun getUserInfo(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Map<String, Any?>> {
        return if (jwt != null) {
            val allClaims = jwt.claims
            ResponseEntity.ok(mapOf(
                "username" to jwt.getClaimAsString("username"),
                "email" to jwt.getClaimAsString("email"),
                "name" to jwt.getClaimAsString("name"),
                "role" to jwt.getClaimAsString("role"),
                "account_id" to jwt.getClaimAsString("account_id"),
                "shopl_client_id" to jwt.getClaimAsString("shopl_client_id"),
                "shopl_user_id" to jwt.getClaimAsString("shopl_user_id"),
                "user_id" to jwt.getClaimAsString("user_id"),
                "sub" to jwt.getClaimAsString("sub"),
                "iss" to jwt.getClaimAsString("iss"),
                "aud" to jwt.audience,
                "exp" to jwt.getClaim<Long>("exp"),
                "iat" to jwt.getClaim<Long>("iat"),
                "scope" to jwt.getClaimAsString("scope"),
                "authenticated" to true,
                "all_claims" to allClaims
            ))
        } else {
            ResponseEntity.ok(mapOf("authenticated" to false))
        }
    }

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
                "message" to "Authentication required"
            ))
        }
    }

    @GetMapping("/sso/configurations")
    fun getSsoConfigurations(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Any> {
        return if (jwt != null) {
            val companyId = jwt.getClaimAsString("account_id")
            val mockData = listOf(
                mapOf(
                    "id" to 1,
                    "name" to "Google Workspace SSO",
                    "providerType" to "OIDC",
                    "companyDomain" to "example.com",
                    "status" to "ACTIVE",
                    "createdAt" to "2024-01-15T10:30:00Z",
                    "updatedAt" to "2024-01-20T14:45:00Z",
                    "companyId" to companyId,
                    "notice" to "Use /api/admin/sso/* API for actual SSO settings"
                )
            )
            ResponseEntity.ok(mockData)
        } else {
            ResponseEntity.status(401).body(mapOf("error" to "Authentication required"))
        }
    }
}