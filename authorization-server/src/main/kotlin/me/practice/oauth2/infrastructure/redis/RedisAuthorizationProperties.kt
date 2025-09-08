package me.practice.oauth2.infrastructure.redis

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth.redis")
data class RedisAuthorizationProperties(
    var keyPrefix: String = "app:auth:",
    var idxPrefix: String = "app:idx"
) {
    fun authKey(id: String) = "$keyPrefix$id"

    fun idxState(state: String) = "$idxPrefix:state:$state"
    fun idxCode(code: String) = "$idxPrefix:code:${sha256(code)}"
    fun idxAccess(token: String) = "$idxPrefix:access:${sha256(token)}"
    fun idxRefresh(token: String) = "$idxPrefix:refresh:${sha256(token)}"
    fun idxIdToken(token: String) = "$idxPrefix:id_token:${sha256(token)}"

    private fun sha256(value: String): String = DigestUtils.sha256Hex(value)
}
