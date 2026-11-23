package com.salesmap.backend.person;

import com.salesmap.backend.global.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes=PersonMergeController.class)
public class PersonMergeExceptionHandler {
    @ExceptionHandler(IdentitySelectionConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(IdentitySelectionConflictException error){
        return ResponseEntity.status(409).body(ApiResponse.fail(error.getMessage()));
    }
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> concurrent(ConcurrencyFailureException error){
        return ResponseEntity.status(409).body(ApiResponse.fail("인물 상태가 바뀌었습니다. 다시 불러와 확인해 주세요."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> failed(DataIntegrityViolationException error){
        return ResponseEntity.status(500).body(ApiResponse.fail("병합·감사 기록을 저장하지 못해 변경을 취소했습니다."));
    }
}
