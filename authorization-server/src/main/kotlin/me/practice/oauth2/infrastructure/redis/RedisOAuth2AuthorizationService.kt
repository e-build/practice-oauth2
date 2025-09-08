package me.practice.oauth2.infrastructure.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class RedisOAuth2AuthorizationService(
	private val redis: StringRedisTemplate,
	private val codec: AuthorizationJsonCodec,
	private val props: RedisAuthorizationProperties,
) : OAuth2AuthorizationService {

	companion object {
		private val TOKEN_TYPE_STATE = OAuth2TokenType("state")
		private val TOKEN_TYPE_CODE = OAuth2TokenType("code")
		private val TOKEN_TYPE_DEVICE_CODE = OAuth2TokenType("device_code") // 미사용
		private val TOKEN_TYPE_USER_CODE = OAuth2TokenType("user_code")     // 미사용
		private val TOKEN_TYPE_ID_TOKEN = OAuth2TokenType("id_token")
	}

	override fun save(authorization: OAuth2Authorization) {
		// (1) 이전 인덱스 정리
		findById(authorization.id)?.let { removeIndexes(it) }

		// (2) 본문 저장 + TTL
		val json = codec.serialize(authorization)
		val ttl = computeAuthTtl(authorization)
		val authKey = props.authKey(authorization.id)
		if (ttl.seconds > 0) {
			redis.opsForValue().set(authKey, json, ttl)
		} else {
			// 이미 만료 직전이면 아주 짧게
			redis.opsForValue().set(authKey, json, Duration.ofSeconds(1))
		}

		// (3) 인덱스 저장 + TTL
		createIndexes(authorization)
	}

	override fun remove(authorization: OAuth2Authorization) {
		// 본문 삭제
		redis.delete(props.authKey(authorization.id))
		// 인덱스 삭제
		removeIndexes(authorization)
	}

	override fun findById(id: String): OAuth2Authorization? {
		val json = redis.opsForValue().get(props.authKey(id)) ?: return null
		return runCatching { codec.deserialize(json) }.onFailure { it.printStackTrace() }.getOrNull()
	}

	override fun findByToken(token: String, tokenType: OAuth2TokenType?): OAuth2Authorization? {
		val id: String? = when (tokenType) {
			null -> {
				// 전체 탐색 순서: state → code → access → refresh → id_token
				resolveIdByState(token)
					?: resolveIdByCode(token)
					?: resolveIdByAccess(token)
					?: resolveIdByRefresh(token)
					?: resolveIdByIdToken(token)
			}

			TOKEN_TYPE_STATE -> resolveIdByState(token)
			TOKEN_TYPE_CODE -> resolveIdByCode(token)
			OAuth2TokenType.ACCESS_TOKEN -> resolveIdByAccess(token)
			OAuth2TokenType.REFRESH_TOKEN -> resolveIdByRefresh(token)
			TOKEN_TYPE_ID_TOKEN -> resolveIdByIdToken(token)
			TOKEN_TYPE_DEVICE_CODE, TOKEN_TYPE_USER_CODE -> null // 현재 미사용
			else -> null
		}

		if (id.isNullOrBlank()) return null

		val json = redis.opsForValue().get(props.authKey(id))
			?: run {
				// 본문이 없는데 인덱스만 남아있으면 정리
				cleanupDanglingIndexes(id)
				return null
			}

		return runCatching { codec.deserialize(json) }.onFailure { it.printStackTrace() }.getOrNull()
	}

	// ======= 인덱스 =======
	private fun createIndexes(auth: OAuth2Authorization) {
		val now = Instant.now()
		//  state - 짧게 10분 정도 (보수적)
		(auth.getAttribute<String>(OAuth2ParameterNames.STATE))?.let { state ->
			setIndex(
				key = props.idxState(state),
				authorizationId = auth.id,
				ttl = ttlFrom(now, now.plusSeconds(600))
			)
		}

		// authorization_code
		auth.getToken(OAuth2AuthorizationCode::class.java)?.let { t ->
			setIndex(
				key = props.idxCode(t.token.tokenValue),
				authorizationId = auth.id,
				ttl = ttlFrom(now, t.token.expiresAt)
			)
		}

		// access_token
		auth.accessToken?.let { t ->
			setIndex(
				key = props.idxAccess(t.token.tokenValue),
				authorizationId = auth.id,
				ttl = ttlFrom(now, t.token.expiresAt)
			)
		}

		// refresh_token
		auth.refreshToken?.let { t ->
			setIndex(
				key = props.idxRefresh(t.token.tokenValue),
				authorizationId = auth.id,
				ttl = ttlFrom(now, t.token.expiresAt)
			)
		}

		// id_token (선택)
		auth.getToken(OidcIdToken::class.java)?.let { t ->
			setIndex(
				key = props.idxIdToken(t.token.tokenValue),
				authorizationId = auth.id,
				ttl = ttlFrom(now, t.token.expiresAt)
			)
		}
	}

	private fun removeIndexes(auth: OAuth2Authorization) {
		val keys = mutableListOf<String>()
		auth.getAttribute<String>(OAuth2ParameterNames.STATE)?.let { keys += props.idxState(it) }
		auth.getToken(OAuth2AuthorizationCode::class.java)?.let { keys += props.idxCode(it.token.tokenValue) }
		auth.accessToken?.let { keys += props.idxAccess(it.token.tokenValue) }
		auth.refreshToken?.let { keys += props.idxRefresh(it.token.tokenValue) }
		auth.getToken(OidcIdToken::class.java)?.let { keys += props.idxIdToken(it.token.tokenValue) }
		if (keys.isNotEmpty()) redis.delete(keys)
	}

	private fun cleanupDanglingIndexes(authorizationId: String) {
		// authorizationId만으로 역추적은 어렵기 때문에, 실운영에선 키스페이스 알림/만료 훅을 쓰는게 베스트.
		// 여기선 no-op로 둔다.
	}

	private fun resolveIdByState(state: String): String? =
		redis.opsForValue().get(props.idxState(state))

	private fun resolveIdByCode(code: String): String? =
		redis.opsForValue().get(props.idxCode(code))

	private fun resolveIdByAccess(token: String): String? =
		redis.opsForValue().get(props.idxAccess(token))

	private fun resolveIdByRefresh(token: String): String? =
		redis.opsForValue().get(props.idxRefresh(token))

	private fun resolveIdByIdToken(token: String): String? =
		redis.opsForValue().get(props.idxIdToken(token))

	private fun setIndex(key: String, authorizationId: String, ttl: Duration) {
		if (ttl.isZero || ttl.isNegative) {
			// 이미 만료된 토큰 → 기존 인덱스 있으면 삭제
			redis.delete(key)
		} else {
			redis.opsForValue().set(key, authorizationId, ttl)
		}
	}

	// TTL 계산
	private fun computeAuthTtl(auth: OAuth2Authorization): Duration {
		val now = Instant.now()
		val candidates = listOfNotNull(
			auth.getToken(OAuth2AuthorizationCode::class.java)?.token?.expiresAt,
			auth.accessToken?.token?.expiresAt,
			auth.refreshToken?.token?.expiresAt,
			auth.getToken(OidcIdToken::class.java)?.token?.expiresAt
		)
		if (candidates.isEmpty()) return Duration.ofHours(1) // 방어적(최소 TTL)

		val latest = candidates.maxOrNull()!!
		val sec = max(1, (latest.epochSecond - now.epochSecond).toInt())
		return Duration.ofSeconds(sec.toLong())
	}

	private fun ttlFrom(now: Instant, expiresAt: Instant?): Duration {
		if (expiresAt == null) return Duration.ofSeconds(1)
		val sec = expiresAt.epochSecond - now.epochSecond
		return Duration.ofSeconds(max(1, sec))
	}
}
