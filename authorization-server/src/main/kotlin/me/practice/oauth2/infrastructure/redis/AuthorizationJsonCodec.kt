package me.practice.oauth2.infrastructure.redis

import com.fasterxml.jackson.databind.ObjectMapper
import me.practice.oauth2.configuration.RedisAuthorizationConverter
import me.practice.oauth2.configuration.RedisAuthorizationDTO
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Component

@Component
class AuthorizationJsonCodec(
	@Qualifier("redisObjectMapper") private val objectMapper: ObjectMapper,
	private val registeredClientRepository: RegisteredClientRepository
) {
    fun serialize(auth: OAuth2Authorization): String =
        objectMapper.writeValueAsString(RedisAuthorizationConverter.toDTO(auth))

    fun deserialize(json: String): OAuth2Authorization {
        val dto = objectMapper.readValue(json, RedisAuthorizationDTO::class.java)
		return RedisAuthorizationConverter.fromDTO(dto, registeredClientRepository)
    }
}