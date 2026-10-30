package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A의 식별 API가 생기기 전까지 쓰는 가짜. contracts/examples/identity_resolution 샘플 중
 * mention.name이 상대 이름과 같거나 mention.email이 상대 메일 주소와 같은 것만 돌려준다
 * (예: "홍 부장" → ir_002 후보 상태, "박가상" → ir_003 신규, gildong.hong@gasang-elec.example → ir_001 확정).
 * 샘플에 없는 상대는 식별 결과 없이 둔다 — 지어내지 않는다.
 */
@Component
public class FakeIdentityResolutionClient implements IdentityResolutionClient {

    private static final Logger log = LoggerFactory.getLogger(FakeIdentityResolutionClient.class);

    private final ObjectMapper objectMapper;

    public FakeIdentityResolutionClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.warn("[MINUTES] A 식별 API가 아직 없어 contracts 샘플을 돌려주는 가짜 구현을 씁니다.");
    }

    @Override
    public List<JsonNode> resolve(String sourceId, List<AttendeeMention> mentions, Long userId) {
        return ContractSamples.readArray(objectMapper, ContractSamples.IDENTITY_RESOLUTION).stream()
                .filter(resolution -> mentions.stream().anyMatch(mention -> matches(mention, resolution.path("mention"))))
                .map(resolution -> {
                    ((ObjectNode) resolution.get("mention")).put("sourceRef", sourceId);
                    return resolution;
                })
                .toList();
    }

    private boolean matches(AttendeeMention mention, JsonNode sample) {
        String sampleName = sample.path("name").asText(null);
        String sampleEmail = sample.path("email").asText(null);
        return (mention.name() != null && mention.name().equals(sampleName))
                || (mention.email() != null && mention.email().equalsIgnoreCase(sampleEmail));
    }
}
