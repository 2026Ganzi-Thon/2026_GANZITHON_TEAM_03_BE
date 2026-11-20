package com.ganzithon.maeum.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** 이메일 인증은 쿠키 인증을 사용하지 않습니다. 그 외 업무 경로는 아직 차단합니다. */
@Configuration
@org.springframework.context.annotation.Profile("!auth")
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/email-verifications"),
                        org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/email-verifications/confirm")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/email-verifications", "/api/v1/auth/email-verifications/confirm").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
