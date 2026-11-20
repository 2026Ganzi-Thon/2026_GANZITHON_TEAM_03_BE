package com.ganzithon.maeum.home;

import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.api.ApiResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("auth")
@RequestMapping("/api/v1")
class HomeController {
    private final HomeService service;

    HomeController(HomeService service) {
        this.service = service;
    }

    @GetMapping("/home")
    ResponseEntity<Object> home(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(service.home(ActivityApi.id(jwt.getSubject()))));
    }
}
