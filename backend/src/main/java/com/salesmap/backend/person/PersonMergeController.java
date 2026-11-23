package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/persons/{personId}/merges")
public class PersonMergeController {
    private final PersonMergeService merges;
    public PersonMergeController(PersonMergeService merges){this.merges=merges;}
    @GetMapping
    public ApiResponse<PersonMergeView.History> history(@PathVariable String personId,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@AuthenticationPrincipal CustomUserPrincipal principal){
        return ApiResponse.success(merges.history(personId,page,size,owner(principal)));
    }
    @PostMapping
    public ApiResponse<PersonMergeView> merge(@PathVariable String personId,@RequestBody JsonNode body,@AuthenticationPrincipal CustomUserPrincipal principal){
        return ApiResponse.success("인물을 병합했습니다.",merges.merge(personId,body,owner(principal),principal.getOwnerUserId()));
    }
    @PostMapping("/{mergeId}/undo")
    public ApiResponse<PersonMergeView> undo(@PathVariable String personId,@PathVariable String mergeId,@RequestBody JsonNode body,@AuthenticationPrincipal CustomUserPrincipal principal){
        return ApiResponse.success("병합을 되돌렸습니다.",merges.undo(personId,mergeId,body,owner(principal),principal.getOwnerUserId()));
    }
    private static Long owner(CustomUserPrincipal principal){if(principal==null)throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");return principal.getUserId();}
}
