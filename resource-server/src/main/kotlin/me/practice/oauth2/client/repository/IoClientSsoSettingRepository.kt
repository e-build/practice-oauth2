package me.practice.oauth2.client.repository

import me.practice.oauth2.client.entity.IoClientSsoSetting
import me.practice.oauth2.client.entity.SsoType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface IoClientSsoSettingRepository : JpaRepository<IoClientSsoSetting, String> {

    /**
     * 클라이언트 ID로 SSO 설정 조회 (삭제되지 않은 것만)
     */
    fun findByClientIdAndDelDtIsNull(clientId: String): IoClientSsoSetting?

    /**
     * 클라이언트 ID로 모든 SSO 설정 조회 (삭제된 것 포함)
     */
    fun findByClientId(clientId: String): List<IoClientSsoSetting>

    /**
     * SSO 타입별 설정 조회
     */
    fun findBySsoTypeAndDelDtIsNull(ssoType: SsoType): List<IoClientSsoSetting>

    /**
     * 특정 클라이언트의 특정 SSO 타입 설정 조회
     */
    fun findByClientIdAndSsoTypeAndDelDtIsNull(clientId: String, ssoType: SsoType): IoClientSsoSetting?

    /**
     * 활성 상태의 모든 SSO 설정 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT s FROM IoClientSsoSetting s WHERE s.delDt IS NULL ORDER BY s.regDt DESC")
    fun findAllActiveSettings(): List<IoClientSsoSetting>

    /**
     * 클라이언트별 활성 SSO 설정 존재 여부 확인
     */
    fun existsByClientIdAndDelDtIsNull(clientId: String): Boolean

    /**
     * SSO 설정 논리 삭제
     */
    @Modifying
    @Query("UPDATE IoClientSsoSetting s SET s.delDt = :deletedAt WHERE s.id = :id")
    fun softDeleteById(id: String, deletedAt: LocalDateTime = LocalDateTime.now())

    /**
     * 클라이언트의 모든 SSO 설정 논리 삭제
     */
    @Modifying
    @Query("UPDATE IoClientSsoSetting s SET s.delDt = :deletedAt WHERE s.clientId = :clientId AND s.delDt IS NULL")
    fun softDeleteByClientId(clientId: String, deletedAt: LocalDateTime = LocalDateTime.now())

    /**
     * 특정 기간 이후에 수정된 SSO 설정 조회
     */
    @Query("SELECT s FROM IoClientSsoSetting s WHERE s.modDt >= :since OR (s.modDt IS NULL AND s.regDt >= :since)")
    fun findModifiedSince(since: LocalDateTime): List<IoClientSsoSetting>

    /**
     * OIDC Client ID로 설정 조회 (중복 방지용)
     */
    fun findByOidcClientIdAndDelDtIsNull(oidcClientId: String): IoClientSsoSetting?

    /**
     * SAML Entity ID로 설정 조회 (중복 방지용)
     */
    fun findBySamlEntityIdAndDelDtIsNull(samlEntityId: String): IoClientSsoSetting?

    /**
     * OAuth2 Client ID로 설정 조회 (중복 방지용)
     */
    fun findByOauth2ClientIdAndDelDtIsNull(oauth2ClientId: String): IoClientSsoSetting?
}