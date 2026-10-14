package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 가짜 구현이 돌려줄 contracts/examples 샘플. backend는 실행 중에 contracts/를 읽을 수 없어서
 * src/main/resources/contracts-examples/에 복사본을 두고, 원본과 같은지는 ContractSamplesSyncTest가 검사한다.
 */
final class ContractSamples {

    static final String IDENTITY_RESOLUTION = "contracts-examples/identity_resolution.example.json";
    static final String TASK = "contracts-examples/task.example.json";

    private ContractSamples() {
    }

    /** 매번 새로 읽어서 돌려준다 (호출한 쪽이 고쳐도 다음 호출에 영향 없음). */
    static List<JsonNode> readArray(ObjectMapper objectMapper, String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            List<JsonNode> items = new ArrayList<>();
            objectMapper.readTree(inputStream).forEach(items::add);
            return items;
        } catch (IOException exception) {
            throw new UncheckedIOException("샘플을 읽을 수 없습니다: " + path, exception);
        }
    }
}
