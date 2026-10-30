package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.service.RevisionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 회의록 수정 기록이 D의 RevisionService로 같은 값 그대로 넘어가는지 (HTTP 없이 서비스 직접 호출). */
class RevisionServiceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RevisionService revisionService = mock(RevisionService.class);
    private final RevisionServiceClient client = new RevisionServiceClient(revisionService);

    @Test
    void 회의록_수정_기록을_D_요청_형식으로_바꿔_소유자와_함께_저장한다() {
        RevisionRecord record = new RevisionRecord(
                "MEETING_NOTE_REVIEW",
                "MEETING_NOTE",
                "mn_7",
                "fields.concerns",
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("text", "예산 확정 시기 불확실")
                        .put("evidence", "예산은 아직 확정 전이라고 하심")),
                "EDITED",
                "LLM",
                "qwen2.5:7b",
                "user_1",
                "2026-10-31T10:00:00"
        );

        client.record(record, 1L);

        ArgumentCaptor<RevisionCreateRequest> captor = ArgumentCaptor.forClass(RevisionCreateRequest.class);
        verify(revisionService).create(captor.capture(), eq(1L));
        RevisionCreateRequest request = captor.getValue();
        assertThat(request.screen()).isEqualTo("MEETING_NOTE_REVIEW");
        assertThat(request.targetType()).isEqualTo("MEETING_NOTE");
        assertThat(request.targetId()).isEqualTo("mn_7");
        assertThat(request.fieldName()).isEqualTo("fields.concerns");
        assertThat(request.proposedValue()).isEqualTo(record.proposedValue());
        assertThat(request.finalValue()).isEqualTo(record.finalValue());
        assertThat(request.action()).isEqualTo("EDITED");
        assertThat(request.proposedBy()).isEqualTo("LLM");
        assertThat(request.modelVersion()).isEqualTo("qwen2.5:7b");
    }
}
