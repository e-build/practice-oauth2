package me.practice.oauth2.client.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
	name = "io_client_oauth_option",
	indexes = [
		Index(name = "idx_client_id", columnList = "clientId")
	]
)
data class IoClientOauthOption(
	@Id
	@Column(length = 20)
	val id: String,

	@Column(name = "client_id", length = 20, nullable = false)
	val clientId: String,

	@Column(name = "is_enable_basic_login", nullable = false)
	val isEnableBasicLogin: Boolean = true,

	@Column(name = "is_enable_google", nullable = false)
	val isEnableGoogle: Boolean = false,

	@Column(name = "is_enable_naver", nullable = false)
	val isEnableNaver: Boolean = false,

	@Column(name = "is_enable_kakao", nullable = false)
	val isEnableKakao: Boolean = false,

	@Column(name = "is_enable_apple", nullable = false)
	val isEnableApple: Boolean = false,

	@Column(name = "is_enable_sso", nullable = false)
	val isEnableSso: Boolean = false,

	@Column(name = "reg_dt", nullable = false)
	val regDt: LocalDateTime = LocalDateTime.now(),

	@Column(name = "mod_dt")
	val modDt: LocalDateTime? = null,

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "client_id", insertable = false, updatable = false)
	val client: IoClientInfo? = null,
)