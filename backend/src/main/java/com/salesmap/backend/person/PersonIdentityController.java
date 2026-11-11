package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/person-identities/for-note/{noteId}")
public class PersonIdentityController {
    private final PersonIdentityService identities;
    public PersonIdentityController(PersonIdentityService identities){this.identities=identities;}
    @GetMapping
    public ApiResponse<List<IdentityReviewItem>> list(@PathVariable String noteId,@AuthenticationPrincipal CustomUserPrincipal user){
        return ApiResponse.success(identities.list(noteId,owner(user)));
    }
    @PostMapping("/{resolutionId}/selection")
    public ResponseEntity<ApiResponse<IdentityReviewItem>> select(@PathVariable String noteId,@PathVariable String resolutionId,
            @RequestBody JsonNode body,@RequestHeader("If-Match") String etag,@AuthenticationPrincipal CustomUserPrincipal user){
        Long owner=owner(user);
        IdentityReviewItem saved=identities.select(noteId,resolutionId,body,etag,owner,user.getOwnerUserId());
        return ResponseEntity.ok().eTag(saved.etag()).body(ApiResponse.success("인물 선택과 수정 기록을 저장했습니다.",saved));
    }
    private static Long owner(CustomUserPrincipal user){if(user==null)throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");return user.getUserId();}
}
