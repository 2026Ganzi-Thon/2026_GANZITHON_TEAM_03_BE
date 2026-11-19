package com.ganzithon.maeum.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger UI(/swagger-ui/index.html)에서 Access Token을 넣고 API를 호출할 수 있게 합니다. 서버 주소는 문서를 연 페이지와 같은
 * 출처(/)로 두어, HTTPS 프록시 뒤에서도 호출 주소가 http://로 바뀌지 않게 합니다.
 */
@Configuration
class OpenApiConfig {
    private static final String BEARER = "bearerAuth";
    private static final String PUBLIC_PREFIX = "/api/v1/auth/";

    @Bean
    OpenAPI maeumOpenApi() {
        return new OpenAPI()
                .info(new Info().title("폴리의 섬 API").version("v1"))
                .servers(List.of(new Server().url("/")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }

    /** 로그인·가입 등 인증 API를 뺀 나머지 API에만 Bearer 인증 표시를 붙입니다. */
    @Bean
    OpenApiCustomizer bearerOnProtectedApis() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, item) -> {
                if (path.startsWith(PUBLIC_PREFIX)) return;
                item.readOperations()
                        .forEach(operation -> operation.addSecurityItem(new SecurityRequirement().addList(BEARER)));
            });
        };
    }
}
