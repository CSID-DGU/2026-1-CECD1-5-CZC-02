package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.global.response.ApiResponse;
import com.salesmap.backend.global.security.CustomUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** 인력 메뉴 (이력서 → 인력 후보 풀). 고객 담당자 API(/api/persons)와 따로 둔다. */
@RestController
@RequestMapping("/api/talents")
public class TalentController {
    private final TalentService talents;

    public TalentController(TalentService talents) {
        this.talents = talents;
    }

    /** 접수만 하고 바로 돌려준다. 분석이 끝나면 상태가 PROCESSING → DRAFT(또는 FAILED)로 바뀐다. */
    @PostMapping("/resumes")
    public ApiResponse<TalentViews.Summary> upload(@RequestBody TalentViews.UploadRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("이력서를 접수했습니다. 분석이 끝나면 확인 대기로 바뀝니다.", talents.upload(request, owner(principal)));
    }

    @GetMapping
    public ApiResponse<List<TalentViews.Summary>> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(required = false) String skills,
            @RequestParam(required = false) Integer minCareerMonths, @RequestParam(required = false) String industry,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableBy,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        TalentFilter filter = TalentFilter.of(status, q, skills, minCareerMonths, industry, availableBy);
        return ApiResponse.success(talents.list(owner(principal), filter));
    }

    @GetMapping("/{profileId}")
    public ApiResponse<TalentViews.Detail> get(@PathVariable String profileId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success(talents.get(profileId, owner(principal)));
    }

    /** 고칠 칸만 보낸다. 예: {"grade": "INTERMEDIATE", "skills": ["Java", {"name": "Kotlin", "category": "LANGUAGE"}]} */
    @PatchMapping("/{profileId}")
    public ApiResponse<TalentViews.Detail> update(@PathVariable String profileId, @RequestBody JsonNode patch,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("수정했습니다.", talents.update(profileId, patch, owner(principal)));
    }

    @PostMapping("/{profileId}/confirm")
    public ApiResponse<TalentViews.Detail> confirm(@PathVariable String profileId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.success("후보 풀에 등록했습니다.", talents.confirm(profileId, owner(principal), principal.getUserId()));
    }

    @DeleteMapping("/{profileId}")
    public ApiResponse<Void> delete(@PathVariable String profileId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        talents.delete(profileId, owner(principal));
        return ApiResponse.success("지웠습니다.", null);
    }

    /** 확정한 후보만 CSV로 (SALESMAP 연락처 가져오기용). SALESMAP으로 자동 전송하지 않는다. */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal CustomUserPrincipal principal) {
        byte[] body = talents.exportCsv(owner(principal));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("인력후보.csv", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static Long owner(CustomUserPrincipal principal) {
        if (principal == null) throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        return principal.getUserId();
    }
}
