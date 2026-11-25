package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기술명·산업명 사전 (규칙). ai-module/app/resumes/dictionary.py가 내보낸 talent/skill-dictionary.json을 읽어
 * 이력서 추출과 같은 사전으로 고객 요청 글에서 기술을 찾는다. 사전은 파이썬 쪽 한 곳에서만 고친다.
 */
@Component
public class SkillDictionary {
    static final String RESOURCE = "/talent/skill-dictionary.json";

    public record SkillHit(String name, String matched) {}
    public record IndustryHit(String name, String word) {}

    private record ScanEntry(String name, Pattern pattern) {}
    private record Industry(String name, List<String> words) {}

    private final List<ScanEntry> scanEntries = new ArrayList<>();
    private final List<Industry> industries = new ArrayList<>();

    @Autowired
    public SkillDictionary(ObjectMapper mapper) {
        this(read(mapper));
    }

    SkillDictionary(JsonNode data) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (JsonNode skill : data.path("skills")) {
            String name = skill.path("name").asText();
            aliases.putIfAbsent(compactKey(name), name);
            skill.path("aliases").forEach(alias -> aliases.putIfAbsent(compactKey(alias.asText()), name));
        }
        Set<String> exclude = new HashSet<>();
        data.path("scanExclude").forEach(word -> exclude.add(word.asText()));
        // 긴 이름부터 찾는다 (Spring Boot 안의 Spring을 따로 세지 않게). 정렬은 안정적이라 같은 길이는 사전 순서대로.
        List<String> keys = new ArrayList<>(aliases.keySet());
        keys.sort(Comparator.comparingInt((String key) -> key.codePointCount(0, key.length())).reversed());
        for (String key : keys) {
            if (exclude.contains(key) || key.codePointCount(0, key.length()) < 2) continue;
            scanEntries.add(new ScanEntry(aliases.get(key), scanPattern(key)));
        }
        for (JsonNode industry : data.path("industries")) {
            List<String> words = new ArrayList<>();
            industry.path("words").forEach(word -> words.add(word.asText()));
            industries.add(new Industry(industry.path("name").asText(), words));
        }
    }

    static SkillDictionary fromClasspath(ObjectMapper mapper) {
        return new SkillDictionary(read(mapper));
    }

    private static JsonNode read(ObjectMapper mapper) {
        try (InputStream in = SkillDictionary.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("기술명 사전 파일이 없습니다: " + RESOURCE);
            return mapper.readTree(in);
        } catch (IOException exception) {
            throw new IllegalStateException("기술명 사전을 읽을 수 없습니다.", exception);
        }
    }

    static String compactKey(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 파이썬 scan_known_skills와 같은 규칙: 한글 이름 뒤에는 조사만 허용(자바 ≠ 자바스크립트), 영문 이름은 앞뒤가 영문·숫자가 아니어야 한다. */
    private static Pattern scanPattern(String key) {
        StringBuilder body = new StringBuilder();
        key.codePoints().forEach(codePoint -> {
            if (!body.isEmpty()) body.append("\\s*");
            body.append(Pattern.quote(new String(Character.toChars(codePoint))));
        });
        char last = key.charAt(key.length() - 1);
        String tail = last >= '가' && last <= '힣'
                ? "(?:(?=[은는이가을를과와로에의도만])|(?![가-힣]))(?![A-Za-z0-9])"
                : "(?![A-Za-z0-9])";
        return Pattern.compile("(?<![A-Za-z0-9가-힣])" + body + tail, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** 글 전체에서 사전에 있는 기술을 찾는다. 같은 기술은 처음 나온 표기 하나만, 글에 나온 순서대로. */
    public List<SkillHit> scan(String text) {
        if (text == null || text.isBlank()) return List.of();
        Map<String, int[]> firstAt = new LinkedHashMap<>();
        Map<String, String> matched = new HashMap<>();
        List<int[]> taken = new ArrayList<>();
        for (ScanEntry entry : scanEntries) {
            Matcher matcher = entry.pattern().matcher(text);
            while (matcher.find()) {
                int start = matcher.start(), end = matcher.end();
                if (taken.stream().anyMatch(span -> span[0] < end && start < span[1])) continue;
                taken.add(new int[]{start, end});
                int[] before = firstAt.get(entry.name());
                if (before == null || start < before[0]) {
                    firstAt.put(entry.name(), new int[]{start});
                    matched.put(entry.name(), matcher.group());
                }
            }
        }
        return firstAt.entrySet().stream()
                .sorted(Comparator.comparingInt(item -> item.getValue()[0]))
                .map(item -> new SkillHit(item.getKey(), matched.get(item.getKey())))
                .toList();
    }

    public List<IndustryHit> industries(String text) {
        List<IndustryHit> found = new ArrayList<>();
        if (text == null || text.isBlank()) return found;
        String lowered = text.toLowerCase(Locale.ROOT);
        for (Industry industry : industries) {
            industry.words().stream().filter(lowered::contains).findFirst()
                    .ifPresent(word -> found.add(new IndustryHit(industry.name(), word)));
        }
        return found;
    }
}
