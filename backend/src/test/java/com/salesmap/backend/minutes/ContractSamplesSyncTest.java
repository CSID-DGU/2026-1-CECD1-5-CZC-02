package com.salesmap.backend.minutes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * contracts/examples 복사본이 원본과 같은지 검사한다. contracts 샘플이 바뀌면 이 테스트가 실패하니 복사본도 같이 바꾼다.
 * <ul>
 *   <li>backend/src/main/resources/contracts-examples/ — 가짜 구현·mock이 쓰는 복사본</li>
 *   <li>frontend/src/components/minutes/samples/ — 확인 화면 A 영역이 쓰는 복사본 (Vite가 frontend 밖을 못 읽음)</li>
 * </ul>
 */
class ContractSamplesSyncTest {

    // gradle 테스트는 backend/에서 돈다
    private static final Path FRONTEND_SAMPLES = Path.of("..", "frontend", "src", "components", "minutes", "samples");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "meeting_note.example.json",
            "identity_resolution.example.json"
    })
    void 백엔드_복사본이_contracts_examples와_같다(String fileName) throws Exception {
        try (InputStream copy = new ClassPathResource("contracts-examples/" + fileName).getInputStream()) {
            assertThat(objectMapper.readTree(copy)).isEqualTo(original(fileName));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "identity_resolution.example.json",
            "person.example.json"
    })
    void 프론트_복사본이_contracts_examples와_같다(String fileName) throws Exception {
        assertThat(objectMapper.readTree(FRONTEND_SAMPLES.resolve(fileName).toFile())).isEqualTo(original(fileName));
    }

    private Object original(String fileName) throws Exception {
        return objectMapper.readTree(ContractSchemaValidator.contractsDir().resolve("examples").resolve(fileName).toFile());
    }
}
