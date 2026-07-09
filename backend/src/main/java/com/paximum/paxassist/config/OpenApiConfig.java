package com.paximum.paxassist.config;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

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

    @Bean
    public GlobalOpenApiCustomizer businessModuleCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (path.startsWith("/api/v1/hotels")) {
                    updateOperation(pathItem, "Hotel", "Otel arama ve listeleme işlemleri.");
                } else if (path.startsWith("/api/v1/flights")) {
                    updateOperation(pathItem, "Flight", "Uçuş arama ve filtreleme işlemleri.");
                } else if (path.startsWith("/api/v1/reservations")) {
                    String description = "Rezervasyon işlemleri.";
                    if (path.endsWith("/preview")) {
                        description = "Rezervasyon önizleme (TourVisio'ya iletilmez, salt okunur).";
                    } else if (path.endsWith("/cancel")) {
                        description = "Rezervasyonu iptal etme.";
                    } else if (path.matches(".*/api/v1/reservations/\\d+")) {
                        description = "Rezervasyon detayı.";
                    } else if (path.equals("/api/v1/reservations")) {
                        description = "Rezervasyon oluşturma ve listeleme.";
                    }
                    updateOperation(pathItem, "Reservation", description);
                }
            });
        };
    }

    private void updateOperation(PathItem pathItem, String tag, String defaultDescription) {
        if (pathItem.getGet() != null) decorate(pathItem.getGet(), tag, defaultDescription);
        if (pathItem.getPost() != null) decorate(pathItem.getPost(), tag, defaultDescription);
        if (pathItem.getPatch() != null) decorate(pathItem.getPatch(), tag, defaultDescription);
        if (pathItem.getPut() != null) decorate(pathItem.getPut(), tag, defaultDescription);
        if (pathItem.getDelete() != null) decorate(pathItem.getDelete(), tag, defaultDescription);
    }

    private void decorate(Operation operation, String tag, String description) {
        operation.addTagsItem(tag);
        if (operation.getSummary() == null || operation.getSummary().isEmpty()) {
            operation.setSummary(description);
        }
    }
}
