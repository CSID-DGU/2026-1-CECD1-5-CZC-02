package com.salesmap.backend.minutes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * backend/src/main/resources/contracts-examples/는 가짜 구현·mock이 쓰는 contracts/examples 복사본이다.
 * contracts 샘플이 바뀌면 이 테스트가 실패하니 복사본도 같이 바꾼다.
 */
class ContractSamplesSyncTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "meeting_note.example.json",
            "identity_resolution.example.json",
            "task.example.json"
    })
    void 복사본이_contracts_examples와_같다(String fileName) throws Exception {
        try (InputStream copy = new ClassPathResource("contracts-examples/" + fileName).getInputStream()) {
            assertThat(objectMapper.readTree(copy))
                    .isEqualTo(objectMapper.readTree(ContractSchemaValidator.contractsDir().resolve("examples").resolve(fileName).toFile()));
        }
    }
}
