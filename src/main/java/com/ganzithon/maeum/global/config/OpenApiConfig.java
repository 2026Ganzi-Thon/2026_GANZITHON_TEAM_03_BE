package com.ganzithon.maeum.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI(/swagger-ui/index.html)에서 Access Token을 넣고 API를 호출할 수 있게 합니다. */
@Configuration
class OpenApiConfig {
    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI maeumOpenApi() {
        return new OpenAPI()
                .info(new Info().title("폴리의 섬 API").version("v1"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
