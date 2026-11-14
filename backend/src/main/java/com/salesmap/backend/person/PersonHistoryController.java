package com.salesmap.backend.person;

import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/persons/{personId}/meetings")
public class PersonHistoryController {
    private final PersonHistoryService history;
    public PersonHistoryController(PersonHistoryService history){this.history=history;}

    @GetMapping
    public ApiResponse<List<MeetingNoteDocument>> list(@PathVariable String personId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        if(principal==null) throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        return ApiResponse.success(history.list(personId,page,size,principal.getUserId()));
    }
}
