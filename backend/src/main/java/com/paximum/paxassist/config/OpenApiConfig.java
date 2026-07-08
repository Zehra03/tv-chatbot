package com.paximum.paxassist.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Adds a global HTTP Bearer (JWT) security scheme so Swagger UI shows an
 * "Authorize" button. Paste a token from {@code POST /api/v1/auth/login} (or
 * {@code /register}) and every "Try it out" call then sends
 * {@code Authorization: Bearer <token>}. Public auth endpoints ignore it
 * server-side (permitAll); the protected modules (chat, hotels, flights, me)
 * require it — mirroring {@code SecurityConfig}.
 *
 * <p>Annotation-only, deliberately: a custom {@code @Bean OpenAPI} was observed
 * to stop springdoc from registering {@code /v3/api-docs} and {@code /swagger-ui}
 * in this app (Spring AI MCP server also on the context). Driving it purely via
 * {@code @OpenAPIDefinition} + {@code @SecurityScheme} leaves springdoc's own
 * OpenAPI auto-configuration intact.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PaxAssist API",
                version = "v1",
                description = "AI-assisted hotel & flight search backend"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
