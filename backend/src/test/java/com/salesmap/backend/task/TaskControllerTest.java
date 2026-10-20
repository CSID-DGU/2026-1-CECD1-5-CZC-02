package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.global.exception.GlobalExceptionHandler;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskControllerTest {
    TaskService tasks = mock(TaskService.class);
    TaskDraftService drafts = mock(TaskDraftService.class);
    MockMvc mvc;
    TaskDocument doc = new TaskDocument("t_1", "mn_1", "계약서 송부", null, false, null, null,
            "EXPLICIT", "CONTRACT_PREP", 1.0, "RULE", "계약서를 보내기로 함", null, "DRAFT");

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TaskController(tasks, drafts))
                .setControllerAdvice(new TaskExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer model,
                            NativeWebRequest request, WebDataBinderFactory factory) {
                        return new CustomUserPrincipal(7L, null, null, "USER", UserStatus.ACTIVE);
                    }
                }).build();
    }
    @Test void getExposesEtagAndContractWithoutStorageFields() throws Exception {
        when(tasks.get("t_1", 7L)).thenReturn(doc); when(tasks.etag(doc)).thenReturn("\"v1\"");
        mvc.perform(get("/api/tasks/t_1"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"v1\""))
                .andExpect(header().string("Access-Control-Expose-Headers", "ETag"))
                .andExpect(jsonPath("$.data.taskId").value("t_1"))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(jsonPath("$.data.version").doesNotExist());
    }
    @Test void updateRequiresIfMatchAndUsesAuthenticatedOwner() throws Exception {
        String body = new ObjectMapper().writeValueAsString(doc);
        mvc.perform(put("/api/tasks/t_1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tasks);
        when(tasks.update(eq("t_1"), any(), eq("\"v1\""), eq(7L))).thenReturn(doc);
        when(tasks.etag(doc)).thenReturn("\"v1\"");
        mvc.perform(put("/api/tasks/t_1").header("If-Match", "\"v1\"").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(tasks).update(eq("t_1"), any(), eq("\"v1\""), eq(7L));
    }
    @Test void staleEditsAre409AndUpstreamErrorsAre502() throws Exception {
        when(tasks.update(anyString(), any(), anyString(), anyLong())).thenThrow(new TaskConflictException("충돌"));
        mvc.perform(put("/api/tasks/t_1").header("If-Match", "old").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        when(drafts.draft("mn_1", 7L)).thenThrow(new org.springframework.web.client.RestClientException("접속 실패"));
        mvc.perform(post("/api/tasks/from-note/mn_1")).andExpect(status().isBadGateway());
    }
    @Test void draftAndListUseAuthenticatedOwner() throws Exception {
        when(drafts.draft("mn_1", 7L)).thenReturn(List.of(doc));
        when(tasks.list(7L)).thenReturn(List.of(doc));
        mvc.perform(post("/api/tasks/from-note/mn_1")).andExpect(status().isOk());
        mvc.perform(get("/api/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].taskId").value("t_1"));
        verify(drafts).draft("mn_1", 7L); verify(tasks).list(7L);
    }
}
