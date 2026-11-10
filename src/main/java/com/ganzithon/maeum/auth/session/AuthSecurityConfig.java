package com.ganzithon.maeum.auth.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganzithon.maeum.global.api.ApiError;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.*;

@Configuration
@Profile("auth")
public class AuthSecurityConfig {
    @Bean
    SecurityFilterChain authFilterChain(HttpSecurity http, AuthSettings settings, ObjectMapper mapper) throws Exception {
        var matcher = PathPatternRequestMatcher.withDefaults();
        var csrfExceptions = java.util.stream.Stream.concat(AuthOriginFilter.COOKIE_PATHS.stream(), java.util.stream.Stream.of(
                        "/api/v1/auth/email-verifications", "/api/v1/auth/email-verifications/confirm"))
                .map(path -> matcher.matcher(HttpMethod.POST, path)).toArray(org.springframework.security.web.util.matcher.RequestMatcher[]::new);
        var bearer = new DefaultBearerTokenResolver();
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable()).cors(cors -> cors.configurationSource(cors(settings)))
                .addFilterBefore(new AuthOriginFilter(settings, mapper), CsrfFilter.class)
                .csrf(csrf -> csrf.ignoringRequestMatchers(csrfExceptions)
                        .ignoringRequestMatchers(request -> {
                            String authorization = request.getHeader("Authorization");
                            return authorization != null && authorization.startsWith("Bearer ");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, AuthOriginFilter.COOKIE_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/email-verifications", "/api/v1/auth/email-verifications/confirm").permitAll()
                        .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(request -> AuthOriginFilter.cookieRequest(request) ? null : bearer.resolve(request))
                        .jwt(jwt -> { })
                        .authenticationEntryPoint((request, response, exception) -> {
                            String code = "AUTH_ACCESS_TOKEN_INVALID";
                            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                                if (cause instanceof JwtValidationException validation && validation.getErrors().stream()
                                        .anyMatch(error -> "AUTH_ACCESS_TOKEN_EXPIRED".equals(error.getErrorCode()))) code = "AUTH_ACCESS_TOKEN_EXPIRED";
                            }
                            response.setStatus(401); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
                            mapper.writeValue(response.getWriter(), new ApiError(code, "다시 로그인해주세요.", List.of()));
                        }))
                .build();
    }
    private CorsConfigurationSource cors(AuthSettings settings) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(settings.allowedOrigins());
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-CSRF-TOKEN"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
