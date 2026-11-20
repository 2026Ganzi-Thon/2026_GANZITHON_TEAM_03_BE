package com.ganzithon.maeum.auth.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganzithon.maeum.global.api.ApiError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** 쿠키를 사용하는 인증 동작은 허용된 프론트 Origin에서만 요청받습니다. */
public class AuthOriginFilter extends OncePerRequestFilter {
    public static final Set<String> COOKIE_PATHS = Set.of("/api/v1/auth/google/challenges",
            "/api/v1/auth/google", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/signup", "/api/v1/auth/login");
    private final AuthSettings settings;
    private final ObjectMapper mapper;
    public AuthOriginFilter(AuthSettings settings, ObjectMapper mapper) { this.settings = settings; this.mapper = mapper; }
    public static boolean cookieRequest(HttpServletRequest request) {
        var matcher = PathPatternRequestMatcher.withDefaults();
        return COOKIE_PATHS.stream().anyMatch(path -> matcher.matcher(HttpMethod.POST, path).matches(request));
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !cookieRequest(request);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || !settings.allowedOrigins().contains(origin)) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), new ApiError("AUTH_CSRF_FAILED", "허용된 화면에서 다시 요청해주세요.", List.of()));
            return;
        }
        chain.doFilter(request, response);
    }
}
