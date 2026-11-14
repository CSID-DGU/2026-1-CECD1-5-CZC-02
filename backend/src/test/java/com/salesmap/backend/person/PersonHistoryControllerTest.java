package com.salesmap.backend.person;

import com.salesmap.backend.global.exception.GlobalExceptionHandler;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PersonHistoryControllerTest {
    final PersonHistoryService history=mock(PersonHistoryService.class);
    CustomUserPrincipal principal=new CustomUserPrincipal(7L,70L,null,null,"USER",UserStatus.ACTIVE);
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new PersonHistoryController(history))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }
                    public Object resolveArgument(MethodParameter parameter,ModelAndViewContainer model,
                            NativeWebRequest request,WebDataBinderFactory factory){return principal;}
                }).build();
    }
    @Test void passesAuthenticatedDataOwnerAndPagingRatherThanQueryOwner() throws Exception {
        when(history.list("p_17",2,10,7L)).thenReturn(List.of());
        mvc.perform(get("/api/persons/p_17/meetings").param("page","2").param("size","10").param("owner","8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        verify(history).list("p_17",2,10,7L);
    }
    @Test void noLoginDoesNotReadAnyMeetings() throws Exception {
        principal=null;
        mvc.perform(get("/api/persons/p_17/meetings")).andExpect(status().isUnauthorized());
        verifyNoInteractions(history);
    }
}
