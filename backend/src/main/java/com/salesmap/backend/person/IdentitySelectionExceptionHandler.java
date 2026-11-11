package com.salesmap.backend.person;

import com.salesmap.backend.global.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes=PersonIdentityController.class)
public class IdentitySelectionExceptionHandler {
    @ExceptionHandler({IdentitySelectionConflictException.class,ConcurrencyFailureException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(RuntimeException error){
        return ResponseEntity.status(409).body(ApiResponse.fail("변경 충돌입니다. 최신 식별 결과를 다시 확인해 주세요."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> storageFailure(DataIntegrityViolationException error){
        return ResponseEntity.status(500).body(ApiResponse.fail("선택·수정 기록 저장에 실패해 변경을 취소했습니다. 담당자 확인이 필요합니다."));
    }
}
