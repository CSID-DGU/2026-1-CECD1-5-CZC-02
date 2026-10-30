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
    public TaskAiClient(@Value("${task.ai-base-url:http://localhost:8001}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(10000);
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
