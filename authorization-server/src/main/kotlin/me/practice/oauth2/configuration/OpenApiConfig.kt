package me.practice.oauth2.configuration

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType as OasMediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.examples.Example
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        // OAuth2 보안 스키마 정의
        val oauthSecurityScheme = SecurityScheme()
            .type(SecurityScheme.Type.OAUTH2)
            .description("OAuth2 Authorization Code Flow")
            .flows(
                OAuthFlows()
                    .authorizationCode(
                        OAuthFlow()
                            .authorizationUrl("http://localhost:9000/oauth2/authorize")
                            .tokenUrl("http://localhost:9000/oauth2/token")
                            .refreshUrl("http://localhost:9000/oauth2/token")
                            .scopes(
                                Scopes()
                                    .addString("read", "Read access")
                                    .addString("write", "Write access")
                            )
                    )
            )

        // Bearer 토큰 보안 스키마 정의
        val bearerSecurityScheme = SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("JWT Bearer Token")

        return OpenAPI()
            .info(
                Info()
                    .title("OAuth2 Authorization Server API")
                    .description("OAuth2 인증 서버의 로그인 이력 관리 및 통계 API")
                    .version("v1.0.0")
                    .contact(
                        Contact()
                            .name("API Support")
                            .url("http://localhost:9000")
                            .email("support@example.com")
                    )
            )
            .servers(
                listOf(
                    Server().url("http://localhost:9000").description("Local Development Server"),
                )
            )
            .tags(
                listOf()
            )
            .components(
                Components()
                    .addSecuritySchemes("oauth2", oauthSecurityScheme)
                    .addSecuritySchemes("bearerAuth", bearerSecurityScheme)
            )
            .addSecurityItem(
                SecurityRequirement()
                    .addList("oauth2", listOf("read", "write"))
            )
            .addSecurityItem(
                SecurityRequirement()
                    .addList("bearerAuth")
            )
    }

    @Bean
    fun publicApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("default")
            .packagesToScan("me.practice.oauth2.api")
            .build()
    }

    @Bean
    fun oauth2EndpointsCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            // OAuth2 Authorization 엔드포인트
            val authorizeOperation = Operation()
                .summary("OAuth2 인증 엔드포인트")
                .description("OAuth2 Authorization Code Grant 플로우의 인증 엔드포인트입니다. 사용자를 로그인 페이지로 리디렉트하고 인증 코드를 발급합니다.")
                .tags(listOf("OAuth2 Endpoints"))
                .addParametersItem(
                    Parameter()
                        .name("client_id")
                        .`in`("query")
                        .required(true)
                        .description("클라이언트 ID")
                        .example("oauth2-client")
                        .schema(Schema<String>().type("string"))
                )
                .addParametersItem(
                    Parameter()
                        .name("response_type")
                        .`in`("query")
                        .required(true)
                        .description("응답 타입 (authorization_code)")
                        .example("code")
                        .schema(Schema<String>().type("string"))
                )
                .addParametersItem(
                    Parameter()
                        .name("redirect_uri")
                        .`in`("query")
                        .required(true)
                        .description("리디렉트 URI")
                        .example("http://localhost:9000/swagger-ui/oauth2-redirect.html")
                        .schema(Schema<String>().type("string"))
                )
                .addParametersItem(
                    Parameter()
                        .name("scope")
                        .`in`("query")
                        .required(false)
                        .description("권한 범위")
                        .example("read write")
                        .schema(Schema<String>().type("string"))
                )
                .addParametersItem(
                    Parameter()
                        .name("state")
                        .`in`("query")
                        .required(false)
                        .description("상태 값 (CSRF 방지)")
                        .example("xyz")
                        .schema(Schema<String>().type("string"))
                )
                .responses(
                    ApiResponses()
                        .addApiResponse("302", ApiResponse().description("로그인 페이지로 리디렉트 또는 인증 코드와 함께 클라이언트로 리디렉트"))
                        .addApiResponse("400", ApiResponse().description("잘못된 요청 파라미터"))
                )

            // OAuth2 Token 엔드포인트
            val tokenOperation = Operation()
                .summary("OAuth2 토큰 엔드포인트")
                .description("인증 코드를 액세스 토큰으로 교환하거나 Refresh Token으로 새 토큰을 발급받습니다.")
                .tags(listOf("OAuth2 Endpoints"))
                .requestBody(
                    io.swagger.v3.oas.models.parameters.RequestBody()
                        .content(
                            Content()
                                .addMediaType(
                                    "application/x-www-form-urlencoded",
                                    OasMediaType()
                                        .schema(
                                            Schema<Any>()
                                                .type("object")
                                                .addProperty("grant_type", Schema<String>().type("string").description("인증 타입 (authorization_code, refresh_token)").example("authorization_code"))
                                                .addProperty("code", Schema<String>().type("string").description("인증 코드 (grant_type=authorization_code인 경우)"))
                                                .addProperty("redirect_uri", Schema<String>().type("string").description("리디렉트 URI"))
                                                .addProperty("refresh_token", Schema<String>().type("string").description("Refresh Token (grant_type=refresh_token인 경우)"))
                                                .addProperty("client_id", Schema<String>().type("string").description("클라이언트 ID"))
                                                .addProperty("client_secret", Schema<String>().type("string").description("클라이언트 Secret"))
                                        )
                                )
                        )
                )
                .responses(
                    ApiResponses()
                        .addApiResponse("200", 
                            ApiResponse()
                                .description("토큰 발급 성공")
                                .content(
                                    Content().addMediaType(
                                        "application/json",
                                        OasMediaType()
                                            .example(mapOf(
                                                "access_token" to "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                                                "refresh_token" to "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                                                "scope" to "read write",
                                                "token_type" to "Bearer",
                                                "expires_in" to 3600
                                            ))
                                    )
                                )
                        )
                        .addApiResponse("400", ApiResponse().description("잘못된 요청"))
                        .addApiResponse("401", ApiResponse().description("인증 실패"))
                )

            // 실제 경로에 추가
            openApi.paths?.addPathItem("/oauth2/authorize", PathItem().get(authorizeOperation))
            openApi.paths?.addPathItem("/oauth2/token", PathItem().post(tokenOperation))
        }
    }
}