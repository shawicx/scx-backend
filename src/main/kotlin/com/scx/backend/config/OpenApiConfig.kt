package com.scx.backend.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI 文档配置
 * 对标 scx-service: swagger-document.ts + @nestjs/swagger
 *
 * 自动生成接口文档（默认 /api/v3/api-docs + /api/swagger-ui.html）。
 * 全局 Bearer 认证 scheme（对标源 addBearerAuth）。
 */
@Configuration
class OpenApiConfig(
    private val swaggerProperties: SwaggerProperties,
) {
    @Bean
    fun customOpenApi(): OpenAPI {
        val securitySchemeName = "bearer"
        return OpenAPI()
            .info(
                Info()
                    .title(swaggerProperties.title)
                    .description(swaggerProperties.description)
                    .version(swaggerProperties.version),
            )
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(
                Components().addSecuritySchemes(
                    securitySchemeName,
                    SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("custom")
                        .description("自定义 HMAC 令牌：base64(payload).hexHmac，通过 Authorization: Bearer <token> 传递"),
                ),
            )
    }
}
