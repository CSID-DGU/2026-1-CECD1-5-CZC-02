package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.person.IdentityResolutionEntry;
import com.salesmap.backend.person.IdentityResolutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** A의 person_identity_resolutions를 읽기만 한다. 깨진 행은 건너뛰고 resolutionId만 로그에 남긴다. */
@Component
public class PersonIdentityDecisionReader implements IdentityDecisionReader {

    private static final Logger log = LoggerFactory.getLogger(PersonIdentityDecisionReader.class);

    private final IdentityResolutionRepository resolutions;
    private final ObjectMapper objectMapper;

    public PersonIdentityDecisionReader(IdentityResolutionRepository resolutions, ObjectMapper objectMapper) {
        this.resolutions = resolutions;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, String> decidedPersonIds(String sourceId, Long userId) {
        Map<String, String> decided = new HashMap<>();
        for (IdentityResolutionEntry row : resolutions.findByUserIdAndSourceRefOrderByMentionIndexAsc(userId, sourceId)) {
            try {
                JsonNode document = objectMapper.readTree(row.getDocument());
                JsonNode personId = document.path("personId");
                if ("CONFIRMED".equals(document.path("decision").asText()) && personId.isTextual() && !personId.textValue().isBlank()) {
                    decided.put(row.getResolutionId(), personId.textValue());
                }
            } catch (Exception error) {
                log.warn("Skipping unreadable identity resolution. resolutionId={}", row.getResolutionId());
            }
        }
        return decided;
    }
}
