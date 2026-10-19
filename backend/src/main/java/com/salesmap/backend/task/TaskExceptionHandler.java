package com.salesmap.backend.task;

import com.salesmap.backend.global.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.dao.DataIntegrityViolationException;

@Order(0)
@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskExceptionHandler {
    @ExceptionHandler({TaskConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(RuntimeException e) {
        return ResponseEntity.status(409).body(ApiResponse.fail("변경 충돌입니다. 새로고침 후 다시 확인하세요."));
    }
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> upstream(RestClientException e) {
        return ResponseEntity.status(502).body(ApiResponse.fail("할 일 분석 API에 연결하지 못했습니다."));
    }
}
