package com.salesmap.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.ai.dto.AiGroupAnalysisRequest;
import com.salesmap.backend.ai.dto.AiMinutesRequest;
import com.salesmap.backend.ai.dto.AiMinutesResponse;
import com.salesmap.backend.ai.dto.AiReplyDraftRequest;
import com.salesmap.backend.ai.dto.AiReplyDraftResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Component
@ConditionalOnProperty(name = "ai.module.mode", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(MockAiClient.class);

    // contracts/examples/meeting_note.example.json의 복사본 (backend는 실행 중에 contracts/를 읽을 수 없다)
    private static final String MEETING_NOTE_SAMPLE = "contracts-examples/meeting_note.example.json";
    private static final List<String> MEETING_NOTE_FIELDS =
            List.of("discussion", "customerRequests", "concerns", "commitments", "nextContact");
    private static final List<String> PER_PERSON_SLOTS = List.of("status", "request", "followUp");

    private final AiModuleProperties aiModuleProperties;
    private final ObjectMapper objectMapper;

    public MockAiClient(AiModuleProperties aiModuleProperties, ObjectMapper objectMapper) {
        this.aiModuleProperties = aiModuleProperties;
        this.objectMapper = objectMapper;
        log.warn("[AI_MODULE] ai.module.mode=mock — AI 모듈을 호출하지 않고 고정된 가짜 분석 결과를 반환합니다. "
                + "실제 분석을 쓰려면 AI_MODULE_MODE=http 로 실행하세요.");
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        return mockResponse();
    }

    @Override
    public AiAnalysisResponse analyzeGroup(AiGroupAnalysisRequest request) {
        return mockResponse();
    }

    @Override
    public AiReplyDraftResponse generateReplyDraft(AiReplyDraftRequest request) {
        String title = request.emailTitle() == null || request.emailTitle().isBlank()
                ? "문의 주신 내용"
                : request.emailTitle();
        return new AiReplyDraftResponse(
                title.startsWith("Re:") ? title : "Re: " + title,
                """
                안녕하세요.

                보내주신 내용 확인했습니다.
                공유해주신 일정과 요청 사항을 기준으로 내부 일정에 반영하고 준비하겠습니다.

                감사합니다.
                """.trim(),
                "mock"
        );
    }

    /**
     * 샘플 회의록(홍 부장 미팅)을 돌려준다. 원본·시각·장소·참석자는 요청 값으로 바꾸고 상태는 DRAFT.
     * 샘플 근거 문장은 요청 메모에 없으므로 화면의 근거 강조는 mock 모드에서 맞지 않는다.
     */
    @Override
    public AiMinutesResponse generateMinutes(AiMinutesRequest request) {
        ObjectNode note = (ObjectNode) readSample(MEETING_NOTE_SAMPLE);
        String mode = "LIGHT".equals(request.mode()) ? "LIGHT" : "FULL";
        List<String> attendees = request.attendees() == null ? List.of() : request.attendees();

        note.put("noteId", request.noteId());
        note.set("sourceRefs", objectMapper.valueToTree(request.sourceRefs()));
        note.put("occurredAt", request.occurredAt());
        note.put("location", request.location());
        note.put("mode", mode);
        note.set("participants", mockParticipants(attendees, request.identityResolutions()));

        ArrayNode missingFields = note.putArray("missingFields");
        if ("LIGHT".equals(mode)) {
            // D-018: LIGHT는 5문항을 비워 두고 사람별 빈 칸만 missingFields에 적는다.
            ObjectNode fields = note.putObject("fields");
            MEETING_NOTE_FIELDS.forEach(fields::putArray);
            ArrayNode perPerson = note.putArray("perPerson");
            for (int index = 0; index < attendees.size(); index++) {
                ObjectNode person = perPerson.addObject();
                person.put("displayName", attendees.get(index));
                person.set("personId", note.get("participants").get(index).get("personId"));
                for (String slot : PER_PERSON_SLOTS) {
                    person.putNull(slot);
                    missingFields.add("perPerson[" + index + "]." + slot);
                }
            }
        } else {
            MEETING_NOTE_FIELDS.stream()
                    .filter(field -> note.get("fields").get(field).isEmpty())
                    .forEach(missingFields::add);
        }

        note.putArray("uncertainSegments");
        note.put("status", "DRAFT");
        note.put("generatedBy", "LLM");
        note.put("llmModel", "mock");

        return new AiMinutesResponse(
                note,
                "mock",
                "mock",
                "ai.module.mode=mock: AI 모듈을 호출하지 않은 샘플 회의록입니다.",
                List.of()
        );
    }

    private ArrayNode mockParticipants(List<String> attendees, List<JsonNode> identityResolutions) {
        ArrayNode participants = objectMapper.createArrayNode();
        for (String attendee : attendees) {
            JsonNode resolution = identityResolutions == null ? null : identityResolutions.stream()
                    .filter(candidate -> attendee.equals(candidate.path("mention").path("name").asText(null)))
                    .findFirst()
                    .orElse(null);
            ObjectNode participant = participants.addObject();
            participant.put("displayName", attendee);
            participant.put("resolutionId", resolution == null ? null : resolution.path("resolutionId").asText(null));
            participant.put("personId", resolution == null ? null : resolution.path("personId").asText(null));
        }
        return participants;
    }

    private JsonNode readSample(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(inputStream);
        } catch (IOException exception) {
            throw new UncheckedIOException("Mock 샘플을 읽을 수 없습니다: " + path, exception);
        }
    }

    private AiAnalysisResponse mockResponse() {
        return new AiAnalysisResponse(
                "ABC Corp",
                "홍길동",
                "Sales Solution",
                1_000_000L,
                "ABC 고객사 박지훈, 영업팀 이민재",
                "CREATE",
                null,
                null,
                "Mock 분석 결과입니다.",
                "SALES_ACTIVITY",
                0.95,
                "고객사와 제품 도입 관련 일정 요청이 포함되어 있습니다.",
                "다음 주 수요일 미팅",
                null,
                "견적서 발송",
                "예산 검토 및 도입 일정 확인 필요",
                "고객이 제품 도입을 검토 중이며 후속 미팅이 필요합니다.",
                0.95,
                "mock",
                null,
                "ai.module.mode=mock: AI 모듈을 호출하지 않은 고정 응답입니다."
        );
    }

    public String getBaseUrl() {
        return aiModuleProperties.getBaseUrl();
    }
}
