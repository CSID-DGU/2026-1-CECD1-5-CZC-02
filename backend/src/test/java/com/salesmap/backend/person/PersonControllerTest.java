package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.global.exception.GlobalExceptionHandler;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import java.util.List;
import java.util.NoSuchElementException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PersonControllerTest {
    private final PersonService people = mock(PersonService.class);
    private CustomUserPrincipal principal = new CustomUserPrincipal(7L, 70L, null, null, "USER", UserStatus.ACTIVE);
    private MockMvc mvc;
    private PersonDocument document;

    @BeforeEach void setup() throws Exception {
        document = PersonRequest.parse(new ObjectMapper().readTree(PersonServiceTest.body())).withId(17L);
        mvc = MockMvcBuilders.standaloneSetup(new PersonController(people))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer model,
                            NativeWebRequest request, WebDataBinderFactory factory) { return principal; }
                }).build();
    }

    @Test void createUsesAuthenticatedDataOwnerAndRealActorAndReturnsOnlyPersonContract() throws Exception {
        when(people.create(any(),eq(7L),eq(70L))).thenReturn(document);
        mvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON).content(PersonServiceTest.body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.personId").value("p_17"))
                .andExpect(jsonPath("$.data.isSynthetic").value(true))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist());
        verify(people).create(any(),eq(7L),eq(70L));
    }

    @Test void listSearchAndDetailUseAuthenticatedOwner() throws Exception {
        when(people.list("가상",1,20,7L)).thenReturn(List.of(document));
        mvc.perform(get("/api/persons").param("q","가상").param("page","1").param("size","20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].personId").value("p_17"));
        when(people.get("p_17",7L)).thenReturn(document);
        mvc.perform(get("/api/persons/p_17")).andExpect(status().isOk());
        verify(people).list("가상",1,20,7L);
        verify(people).get("p_17",7L);
    }

    @Test void missingLoginNeverCallsStorage() throws Exception {
        principal=null;
        mvc.perform(get("/api/persons")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/persons/p_17")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON).content(PersonServiceTest.body()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(people);
    }

    @Test void invalidInputsAre400AndForeignOrMissingPeopleAre404() throws Exception {
        when(people.create(any(),anyLong(),anyLong())).thenThrow(new IllegalArgumentException("잘못된 등록 형식"));
        mvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(people.get("p_17",7L)).thenThrow(new NoSuchElementException("인물이 없거나 접근할 수 없습니다."));
        mvc.perform(get("/api/persons/p_17")).andExpect(status().isNotFound());
    }

    @Test void identitySelectionMutationAndDeletionEndpointsDoNotExist() throws Exception {
        mvc.perform(delete("/api/persons/p_17")).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/persons/p_17").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(people);
    }
}
