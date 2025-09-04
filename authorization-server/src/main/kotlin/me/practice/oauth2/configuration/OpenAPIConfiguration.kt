package me.practice.oauth2.configuration

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@OpenAPIDefinition(
	info = Info(title = "OAuth Practice", description = "인가서버 연습", version = "v1"),
)
@Configuration
class OpenAPIConfiguration {
}
