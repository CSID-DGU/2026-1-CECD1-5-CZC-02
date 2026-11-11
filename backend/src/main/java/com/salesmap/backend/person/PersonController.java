package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/persons")
public class PersonController {
    private final PersonService people;
    public PersonController(PersonService people) { this.people = people; }

    @PostMapping
    public ApiResponse<PersonDocument> create(@RequestBody JsonNode body,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("인물을 등록했습니다.", people.create(body, owner(principal), principal.getOwnerUserId()));
    }

    @GetMapping
    public ApiResponse<List<PersonDocument>> list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(people.list(q, page, size, owner(principal)));
    }

    @GetMapping("/{personId}")
    public ApiResponse<PersonDocument> get(@PathVariable String personId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(people.get(personId, owner(principal)));
    }

    private static Long owner(CustomUserPrincipal principal) {
        if (principal == null) throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        return principal.getUserId();
    }
}
