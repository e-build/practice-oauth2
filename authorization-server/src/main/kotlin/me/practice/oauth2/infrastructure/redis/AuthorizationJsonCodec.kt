package me.practice.oauth2.infrastructure.redis

import com.fasterxml.jackson.databind.ObjectMapper
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.infrastructure.redis.converter.OAuth2AuthorizationConverter
import me.practice.oauth2.infrastructure.redis.dto.RedisAuthorizationDTO
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Component

@Component
class AuthorizationJsonCodec(
	@Qualifier("redisObjectMapper") private val objectMapper: ObjectMapper,
	private val oAuth2AuthorizationConverter: OAuth2AuthorizationConverter,
	private val registeredClientRepository: RegisteredClientRepository,
	private val ioIdpAccountRepository: IoIdpAccountRepository,
) {
	fun serialize(auth: OAuth2Authorization): String =
		objectMapper.writeValueAsString(oAuth2AuthorizationConverter.toDTO(auth))

	fun deserialize(json: String): OAuth2Authorization {
		return try {
			if (json.isBlank()) {
				throw IllegalArgumentException("JSON content is blank")
			}

			val dto = objectMapper.readValue(json, RedisAuthorizationDTO::class.java)
			oAuth2AuthorizationConverter.fromDTO(
				dto = dto,
				registeredClientRepository = registeredClientRepository,
				ioIdpAccountRepository = ioIdpAccountRepository
			)
		} catch (e: IllegalArgumentException) {
			throw RuntimeException("Invalid JSON format for OAuth2Authorization: ${e.message}", e)
		} catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
			throw RuntimeException("JSON parsing failed for OAuth2Authorization: ${e.message}", e)
		} catch (e: Exception) {
			val truncatedJson = if (json.length > 200) json.take(200) + "..." else json
			throw RuntimeException(
				"Failed to deserialize OAuth2Authorization from JSON: ${e.message}. JSON: $truncatedJson",
				e
			)
		}
	}
}