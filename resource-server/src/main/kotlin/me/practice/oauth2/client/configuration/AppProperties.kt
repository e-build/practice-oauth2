package me.practice.oauth2.client.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val authorizationServer: AuthorizationServer
) {
    data class AuthorizationServer(
        val baseUrl: String
    )
}