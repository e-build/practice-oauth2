package me.practice.oauth2.configuration

import me.practice.oauth2.service.CompositeClientRegistrationRepository
import me.practice.oauth2.service.DynamicClientRegistrationRepository
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository

/**
 * OAuth2 클라이언트 설정
 */
@EnableConfigurationProperties(OAuth2ClientProperties::class)
@Configuration
class OAuth2ClientConfiguration {

    /**
     * application.yml에서 정적 OAuth2 클라이언트 설정을 읽어서 ClientRegistrationRepository 생성
     */
    @Bean("staticClientRegistrationRepository")
    fun staticClientRegistrationRepository(
        properties: OAuth2ClientProperties
    ): ClientRegistrationRepository {
        val registrations = OAuth2ClientPropertiesMapper(properties)
            .asClientRegistrations()
        
        return if (registrations.isNotEmpty()) {
            InMemoryClientRegistrationRepository(registrations.values.toList())
        } else {
            // 빈 InMemoryClientRegistrationRepository 반환 (등록된 클라이언트 없음)
            InMemoryClientRegistrationRepository(emptyList())
        }
    }

    /**
     * 복합 ClientRegistrationRepository를 Primary Bean으로 설정
     * Spring Security OAuth2 Login이 이 Bean을 사용하게 됨
     */
    @Bean
    @Primary
    fun clientRegistrationRepository(
        @Qualifier("staticClientRegistrationRepository") staticClientRegistrationRepository: ClientRegistrationRepository,
        dynamicClientRegistrationRepository: DynamicClientRegistrationRepository
    ): ClientRegistrationRepository {
        return CompositeClientRegistrationRepository(
            staticClientRegistrationRepository,
            dynamicClientRegistrationRepository
        )
    }
}