package com.salesmap.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Arrays;
import java.util.List;

@Component
public class TaskAiClient {
    private final RestClient client;
    // 생성자가 둘이라 Spring이 쓸 쪽을 지정한다 (없으면 "No default constructor found"로 서버가 안 뜸)
    @Autowired
    // 규칙 NONE 항목은 AI 모듈이 로컬 LLM에 물어봐서 10초를 넘길 수 있다 → 회의록과 같은 AI 모듈 제한을 쓴다
    public TaskAiClient(@Value("${task.ai-base-url:http://localhost:8001}") String baseUrl,
                        @Value("${ai.module.connect-timeout-ms:5000}") int connectTimeoutMs,
                        @Value("${ai.module.read-timeout-ms:330000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs); factory.setReadTimeout(readTimeoutMs);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    TaskAiClient(RestClient client) { this.client = client; }
    public List<JsonNode> extract(MeetingNoteDocument note, Long userId) {
        JsonNode[] response = client.post().uri(uri -> uri.path("/tasks/extract")
                .queryParam("assignee", "user_" + userId).build()).body(note).retrieve().body(JsonNode[].class);
        if (response == null) throw new org.springframework.web.client.RestClientException("할 일 API 응답이 없습니다.");
        return Arrays.asList(response);
    }
}
