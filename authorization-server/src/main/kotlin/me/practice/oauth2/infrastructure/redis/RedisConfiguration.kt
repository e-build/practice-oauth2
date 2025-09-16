package me.practice.oauth2.infrastructure.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
//import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession

//@EnableRedisHttpSession
@EnableConfigurationProperties(RedisAuthorizationProperties::class)
@Configuration
class RedisConfiguration(
	private val redisProperties: RedisProperties
) {

	@Bean
	fun redisConnectionFactory(): RedisConnectionFactory =
		LettuceConnectionFactory(
			redisProperties.host,
			redisProperties.port
		)

	@Bean
	fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate =
		StringRedisTemplate(connectionFactory).apply {
			keySerializer = StringRedisSerializer()
			valueSerializer = StringRedisSerializer()
			hashKeySerializer = StringRedisSerializer()
			hashValueSerializer = StringRedisSerializer()
		}

	@Bean
	fun redisObjectMapper(): ObjectMapper =
		jacksonObjectMapper()
			.registerModule(JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

}