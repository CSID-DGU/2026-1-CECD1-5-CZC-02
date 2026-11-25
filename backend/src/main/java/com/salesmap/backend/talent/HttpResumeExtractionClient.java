package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.ai.config.AiModuleProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.time.Duration;
import java.util.Base64;

/**
 * 이력서 추출은 회의록 분석 모드(mock/http)와 상관없이 항상 AI 모듈을 부른다 (가짜 결과로 후보 풀을 채우지 않는다).
 * 줄글 이력서는 로컬 LLM이 몇 분 걸릴 수 있어 읽기 제한 시간을 따로 길게 둔다. 호출은 백그라운드 작업에서만 한다.
 */
@Component
public class HttpResumeExtractionClient implements ResumeExtractionClient {
    private final RestClient client;
    private final ObjectMapper mapper;

    @Autowired
    public HttpResumeExtractionClient(AiModuleProperties properties, ObjectMapper mapper,
                                      @Value("${talent.extract.read-timeout-ms:900000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.client = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
        this.mapper = mapper;
    }

    HttpResumeExtractionClient(RestClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Extraction extract(String fileName, byte[] content, String profileId, String sourceId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("profileId", profileId);
        body.put("fileName", fileName);
        body.put("contentBase64", Base64.getEncoder().encodeToString(content));
        ObjectNode ref = body.putArray("sourceRefs").addObject();
        ref.put("sourceId", sourceId);
        ref.put("sourceType", "RESUME_FILE");
        ref.put("fileName", fileName);

        JsonNode response;
        try {
            response = client.post().uri("/resumes/extract").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            // 422·413의 detail은 AI 모듈이 만든 안내 문장(.docx로 다시 저장 등)이라 그대로 보여 준다. 원문은 담기지 않는다
            if (status == 422 || status == 413) throw new ExtractionException(detail(error));
            throw new ExtractionException("이력서 분석 서버 오류 (" + status + ")");
        } catch (RestClientException error) {
            throw new ExtractionException("이력서 분석 서버(AI 모듈)에 연결하지 못했거나 응답이 너무 늦습니다.");
        }
        if (response == null || !response.path("resumeProfile").isObject())
            throw new ExtractionException("이력서 분석 결과 형식이 올바르지 않습니다.");
        return new Extraction(response.get("resumeProfile"), response.path("sourceText").asText(""),
                TalentFields.text(response.path("llmProvider")), TalentFields.text(response.path("llmModel")),
                TalentFields.text(response.path("llmFallbackReason")));
    }

    private String detail(RestClientResponseException error) {
        try {
            JsonNode detail = mapper.readTree(error.getResponseBodyAsString()).path("detail");
            if (detail.isTextual() && detail.asText().length() <= 200) return detail.asText();
        } catch (Exception ignored) {
            // 아래 기본 문장
        }
        return "이력서를 읽을 수 없습니다. 워드(.docx) 파일인지 확인해 주세요.";
    }
}
