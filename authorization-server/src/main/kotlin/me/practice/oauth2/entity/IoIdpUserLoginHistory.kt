package me.practice.oauth2.entity

import jakarta.persistence.*
import me.practice.oauth2.domain.IdpClient
import java.time.LocalDateTime

@Entity
@Table(name = "io_idp_user_login_history")
data class IoIdpUserLoginHistory(
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

	@Column(name = "shopl_client_id", length = 20, nullable = false)
    val shoplClientId: String,

	@Column(name = "shopl_user_id", length = 20, nullable = false)
    val shoplUserId: String,

	@Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    val platform: IdpClient.Platform,

	@Enumerated(EnumType.STRING)
    @Column(name = "login_type", nullable = false)
    val loginType: LoginType,

	@Column(name = "provider", length = 64)
    val provider: String? = null,

	@Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    val result: LoginResult,

	@Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 100)
    val failureReason: FailureReasonType? = null,

	@Column(name = "ip_address", length = 45)
    val ipAddress: String? = null,

	@Column(name = "user_agent", columnDefinition = "TEXT")
    val userAgent: String? = null,

	@Column(name = "session_id", length = 128, nullable = false)
    val sessionId: String,

	@Column(name = "reg_dt", nullable = false)
	val regDt: LocalDateTime = LocalDateTime.now(),
)