package me.practice.oauth2.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val resourceServer: ResourceServer,
    val auth: Auth
) {
    data class ResourceServer(
        val baseUrl: String
    )

    data class Auth(
        val redis: Redis
    ) {
        data class Redis(
            val keyPrefix: String,
            val idxPrefix: String
        )
    }
}