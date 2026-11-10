package com.ganzithon.maeum.auth;

import com.ganzithon.maeum.auth.verification.EmailVerificationController;
import com.ganzithon.maeum.auth.verification.EmailVerificationService;
import com.ganzithon.maeum.auth.verification.VerificationException;
import com.ganzithon.maeum.global.api.ApiExceptionHandler;
import com.ganzithon.maeum.global.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailVerificationController.class)
@ActiveProfiles("web-test")
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class EmailVerificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean EmailVerificationService service;

    @Test
    void sendIsAnonymousReturns202AndDoesNotExposeToken() throws Exception {
        mvc.perform(post("/api/v1/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.message").exists()).andExpect(jsonPath("$.data.token").doesNotExist());
        verify(service).send(eq("user@example.com"), anyString());
    }

    @Test
    void confirmReturnsSignupProofWithoutAccessOrRefreshToken() throws Exception {
        when(service.confirm("a".repeat(43))).thenReturn(new EmailVerificationService.Confirmation("user@example.com", "proof", 600));
        mvc.perform(post("/api/v1/auth/email-verifications/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "a".repeat(43) + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.expiresIn").value(600))
                .andExpect(jsonPath("$.data.signupVerificationToken").value("proof"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void invalidInputUsesCommonErrorAndDoesNotEchoInput() throws Exception {
        mvc.perform(post("/api/v1/auth/email-verifications/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());
        verifyNoInteractions(service);
    }

    @Test
    void expiredTokenUses410AndOtherCookieRoutesStillRequireCsrf() throws Exception {
        when(service.confirm("expired")).thenThrow(new VerificationException(HttpStatus.GONE,
                "AUTH_EMAIL_VERIFICATION_EXPIRED", "인증 시간이 지났습니다."));
        mvc.perform(post("/api/v1/auth/email-verifications/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"expired\"}"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("AUTH_EMAIL_VERIFICATION_EXPIRED"));
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isForbidden());
    }
}
