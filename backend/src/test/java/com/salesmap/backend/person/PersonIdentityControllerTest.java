package com.salesmap.backend.person;

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
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PersonIdentityControllerTest {
    final PersonIdentityService service=mock(PersonIdentityService.class);
    CustomUserPrincipal user=new CustomUserPrincipal(7L,70L,null,null,"USER",UserStatus.ACTIVE);
    MockMvc mvc;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.standaloneSetup(new PersonIdentityController(service))
            .setControllerAdvice(new IdentitySelectionExceptionHandler(),new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){
                public boolean supportsParameter(MethodParameter parameter){return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);}
                public Object resolveArgument(MethodParameter parameter,ModelAndViewContainer model,NativeWebRequest request,WebDataBinderFactory factory){return user;}
            }).build();
    }
    @Test void listUsesNoteAndAuthenticatedOwner() throws Exception {
        when(service.list("mn_21",7L)).thenReturn(List.of());
        mvc.perform(get("/api/person-identities/for-note/mn_21")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        verify(service).list("mn_21",7L);
    }
    @Test void selectionRequiresVersionAndReturnsSavedHumanAndEtag() throws Exception {
        String path="/api/person-identities/for-note/mn_21/ir_owned/selection";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"personId\":\"p_17\"}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(service);
        var resolution=new ObjectMapper().createObjectNode().put("resolutionId","ir_owned").put("personId","p_17").put("decidedBy","HUMAN");
        when(service.select(eq("mn_21"),eq("ir_owned"),any(),eq("\"irv-0\""),eq(7L),eq(70L)))
            .thenReturn(new IdentityReviewItem(resolution,"\"irv-1\""));
        mvc.perform(post(path).header("If-Match","\"irv-0\"").contentType(MediaType.APPLICATION_JSON).content("{\"personId\":\"p_17\"}"))
            .andExpect(status().isOk()).andExpect(header().string("ETag","\"irv-1\""))
            .andExpect(jsonPath("$.data.resolution.decidedBy").value("HUMAN"));
    }
    @Test void conflictIs409AndMissingLoginNeverCallsService() throws Exception {
        when(service.list("mn_21",7L)).thenThrow(new IdentitySelectionConflictException("충돌"));
        mvc.perform(get("/api/person-identities/for-note/mn_21")).andExpect(status().isConflict());
        clearInvocations(service);user=null;
        mvc.perform(get("/api/person-identities/for-note/mn_21")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/person-identities/for-note/mn_21/ir_owned/selection").header("If-Match","v0")
            .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
