package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.minutes.client.AttendeeMention;
import com.salesmap.backend.minutes.client.IdentityResolutionClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.time.Duration;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** B 호출 인터페이스를 구현한다. HTTP 모드만 실제 A API를 우선 사용하고 B 파일은 수정하지 않는다. */
@Component
@Primary
@ConditionalOnProperty(name = "ai.module.mode", havingValue = "http")
public class PersonIdentityResolutionClient implements IdentityResolutionClient {
    private static final Set<String> CHANNELS = Set.of("EMAIL", "JANDI", "KAKAO", "PHONE", "OPEN_MARKET", "MEETING");
    private final PersonService people;
    private final PersonIdentityService identities;
    private final ObjectMapper mapper;
    private final RestClient client;

    @Autowired
    public PersonIdentityResolutionClient(PersonService people, PersonIdentityService identities, AiModuleProperties properties, ObjectMapper mapper) {
        this(people, identities, mapper, buildClient(properties));
    }

    PersonIdentityResolutionClient(PersonService people, PersonIdentityService identities, ObjectMapper mapper, RestClient client) {
        this.people = people; this.identities = identities; this.mapper = mapper; this.client = client;
    }

    @Override
    public List<JsonNode> resolve(String sourceId, List<AttendeeMention> mentions, Long userId) {
        if (userId == null || userId < 1) throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        if (mentions == null || mentions.stream().anyMatch(m -> m == null || m.channel() == null || !CHANNELS.contains(m.channel())))
            throw new IllegalArgumentException("유효한 인물 단서·채널 목록이 필요합니다.");
        if (mentions.isEmpty()) return List.of();
        if (sourceId == null || !sourceId.matches("src_[1-9][0-9]*"))
            throw new IllegalArgumentException("원본의 src_<DB PK> ID가 필요합니다.");

        ArrayNode observed = mapper.createArrayNode();
        for (AttendeeMention mention : mentions) {
            ObjectNode node = observed.addObject();
            node.put("name", mention.name()); node.put("email", mention.email());
            node.put("channel", mention.channel()); node.put("sourceRef", sourceId);
        }
        // 전체 본인 대장의 완성된 DTO만 받아서 HTTP 중 DB 트랜잭션을 유지하지 않는다.
        List<PersonDocument> roster = people.forResolution(userId);
        ObjectNode body = mapper.createObjectNode();
        body.set("mentions", observed); body.set("persons", mapper.valueToTree(roster));
        JsonNode response;
        try {
            response = client.post().uri("/identity/resolve").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException error) {
            // upstream 오류 본문에 원문 단서가 들어 있어도 B의 예외 로그에 복사하지 않는다.
            throw new RestClientException("인물 식별 API HTTP 오류 (" + error.getStatusCode().value() + ")");
        } catch (RestClientException error) {
            throw new RestClientException("인물 식별 API 연결 또는 응답 읽기에 실패했습니다.");
        }
        List<JsonNode> results = IdentityResponseValidator.validate(response, observed, roster);
        preventAmbiguousMeetingLinks(results);
        return identities.storeProposals(sourceId, results, userId);
    }

    /** B는 현재 공백을 뺀 이름으로 연결한다. 같은 표시 이름의 자동 확정을 그대로 넘기면 오연결될 수 있다. */
    private static void preventAmbiguousMeetingLinks(List<JsonNode> results) {
        Set<String> seen = new HashSet<>(), duplicateNames = new HashSet<>();
        for (JsonNode row : results) {
            String name = row.path("mention").path("name").asText("").replaceAll("(?U)\\s+", "");
            if (!name.isEmpty() && !seen.add(name)) duplicateNames.add(name);
        }
        for (JsonNode row : results) {
            String name = row.path("mention").path("name").asText("").replaceAll("(?U)\\s+", "");
            if (duplicateNames.contains(name) && "CONFIRMED".equals(row.path("decision").asText()))
                throw new RestClientException("같은 이름의 참석자를 현재 회의록 연결 방식으로 구분할 수 없습니다. 사용자 확인이 필요합니다.");
        }
    }

    private static RestClient buildClient(AiModuleProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }
}
