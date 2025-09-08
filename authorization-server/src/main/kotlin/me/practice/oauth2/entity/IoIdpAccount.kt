package me.practice.oauth2.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "io_idp_account",
	catalog = "shopl_authorization",
)
data class IoIdpAccount(
	@Id
    @Column(length = 20)
    val id: String,

	@Column(name = "shopl_client_id", length = 20, nullable = false)
    val shoplClientId: String,

	@Column(name = "shopl_user_id", length = 20, nullable = false)
    val shoplUserId: String,

	@Column(name = "shopl_login_id", length = 20, nullable = false)
	val shoplLoginId: String,

	@Column(name = "email")
    val email: String? = null,

	@Column(name = "phone", length = 30)
    val phone: String? = null,

	@Column(name = "name", length = 100)
    val name: String? = null,

	@Column(name = "status", length = 20)
    val status: String = "ACTIVE",

	@Column(name = "is_cert_email", nullable = false)
    val isCertEmail: Boolean = false,

	@Column(name = "is_temp_pwd", nullable = false)
    val isTempPwd: Boolean = false,

	@Column(name = "pwd", nullable = false)
    val pwd: String? = null,

	@Column(name = "before_pwd")
    val beforePwd: String? = null,

	@Column(name = "pwd_update_dt")
    val pwdUpdateDt: LocalDateTime? = null,

	@Column(name = "reg_dt", nullable = false)
    val regDt: LocalDateTime = LocalDateTime.now(),

	@Column(name = "mod_dt")
    val modDt: LocalDateTime? = null,

	@Column(name = "del_dt")
    val delDt: LocalDateTime? = null,
)