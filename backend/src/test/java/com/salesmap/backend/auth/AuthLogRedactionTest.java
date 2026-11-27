package com.salesmap.backend.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.auth.controller.AuthController;
import com.salesmap.backend.auth.dto.AuthResponse;
import com.salesmap.backend.auth.dto.LoginRequest;
import com.salesmap.backend.auth.dto.SignupRequest;
import com.salesmap.backend.auth.service.AuthService;
import com.salesmap.backend.global.exception.GlobalExceptionHandler;
import com.salesmap.backend.global.logging.SensitiveAuthLogFilter;
import com.salesmap.backend.global.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MySQL·실제 계정 없이 실제 MVC 읽기·쓰기·예외 로그를 TRACE로 검사한다. */
class AuthLogRedactionTest {
    private static final String EMAIL = "fake-log-check@example.test";
    private static final String PASSWORD = "FAKE_PASSWORD_MUST_NOT_BE_LOGGED";
    private static final String TOKEN = "FAKE_JWT_MUST_NOT_BE_LOGGED";
    private final ObjectMapper json = new ObjectMapper();
    private AuthService service;
    private MockMvc mvc;
    private Logger web;
    private Level previousLevel;
    private SensitiveAuthLogFilter protection;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        web = context.getLogger("org.springframework.web");
        previousLevel = web.getLevel();
        web.setLevel(Level.TRACE);
        logs = new ListAppender<>();
        logs.start();
        web.addAppender(logs);
        protection = new SensitiveAuthLogFilter();
        protection.setContext(context);
        protection.start();
        context.addTurboFilter(protection);
        service = mock(AuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addDispatcherServletCustomizer(servlet -> servlet.setEnableLoggingRequestDetails(true))
                .build();
        logs.list.clear();
    }

    @AfterEach
    void tearDown() {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getTurboFilterList().remove(protection);
        protection.stop();
        web.detachAppender(logs);
        logs.stop();
        web.setLevel(previousLevel);
    }

    private String logText() {
        return logs.list.stream().map(e -> e.getFormattedMessage()
                + (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Test
    void successfulLoginKeepsJsonCredentialsButNotDiagnosticLogs() throws Exception {
        when(service.login(any())).thenReturn(new AuthResponse("Bearer", TOKEN, 3600L, null));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").value(TOKEN));
        verify(service).login(new LoginRequest(EMAIL, PASSWORD));
        assertThat(logText()).doesNotContain(EMAIL, PASSWORD, TOKEN);
    }

    @Test
    void failedLoginDoesNotLogPassword() throws Exception {
        when(service.login(any())).thenThrow(new BadCredentialsException("인증 실패"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isUnauthorized());
        assertThat(logText()).doesNotContain(EMAIL, PASSWORD);
    }

    @Test
    void signupKeepsPasswordInServiceInputButNotDiagnosticLogs() throws Exception {
        when(service.signup(any())).thenReturn(new AuthResponse("Bearer", TOKEN, 3600L, null));
        var body = new SignupRequest(EMAIL, "가상로그검사", PASSWORD);
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").value(TOKEN));
        verify(service).signup(body);
        assertThat(logText()).doesNotContain(EMAIL, PASSWORD, TOKEN, body.name());
    }

    @Test
    void validationErrorDoesNotLogRejectedValueOrPassword() throws Exception {
        String invalidEmail = "FAKE_REJECTED_EMAIL_MUST_NOT_BE_LOGGED";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(invalidEmail, PASSWORD))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.email").exists());
        verifyNoInteractions(service);
        assertThat(logText()).doesNotContain(invalidEmail, PASSWORD);
    }

    @Test
    void malformedJsonDoesNotLogPasswordOrAuthorizationHeader() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + TOKEN)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        assertThat(logText()).doesNotContain(EMAIL, PASSWORD, TOKEN);
    }

    @Test
    void recordsAndResponseWrappersAreSafeOutsideHttpContext() throws Exception {
        var login = new LoginRequest(EMAIL, PASSWORD);
        var signup = new SignupRequest(EMAIL, "가상로그검사", PASSWORD);
        var auth = new AuthResponse("Bearer", TOKEN, 3600L, null);
        assertThat(login.toString() + signup + ApiResponse.success(auth))
                .doesNotContain(EMAIL, PASSWORD, TOKEN, signup.name()).contains("REDACTED");
        assertThat(json.readValue(json.writeValueAsString(login), LoginRequest.class)).isEqualTo(login);
        assertThat(json.readTree(json.writeValueAsString(auth)).get("accessToken").asText()).isEqualTo(TOKEN);
    }

    @Test
    void unrelatedRequestDiagnosticsRemainEnabled() {
        web.debug("가상 비인증 경로 진단");
        assertThat(logText()).contains("가상 비인증 경로 진단");
    }
}
