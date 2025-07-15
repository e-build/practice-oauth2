package me.practice.oauth2.client.api.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class ClientSecurityConfiguration(
    private val cookieOAuth2AuthorizationRequestRepository: CookieOAuth2AuthorizationRequestRepository
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/home").authenticated()
                    .anyRequest().permitAll()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .authorizationEndpoint { authorizationEndpoint ->
                        authorizationEndpoint
                            .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository)
                    }
                    .defaultSuccessUrl("/home", true)
            }
            .logout { logout ->
                logout
                    .logoutSuccessUrl("/")
                    .permitAll()
            }

        return http.build()
    }
}