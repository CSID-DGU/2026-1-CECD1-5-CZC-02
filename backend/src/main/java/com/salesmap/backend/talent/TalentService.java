package com.salesmap.backend.talent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.dto.RevisionResponse;
import com.salesmap.backend.revision.service.RevisionService;
import com.salesmap.backend.talent.ResumeExtractionClient.Extraction;
import com.salesmap.backend.talent.ResumeExtractionClient.ExtractionException;
import com.salesmap.backend.talent.TalentViews.Detail;
import com.salesmap.backend.talent.TalentViews.DuplicateCandidate;
import com.salesmap.backend.talent.TalentViews.Summary;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 이력서 올리기 → (백그라운드) AI 추출 → 초안 → 사람이 고치고 확정 → 후보 풀.
 * 줄글 이력서는 로컬 LLM이 몇 분 걸리므로 올리기는 접수만 하고 바로 돌려준다. 로컬 LLM이 한 번에 한 건만 돌도록 한 줄로 처리한다.
 * 확정·병합은 사람만 한다. SALESMAP으로 자동 전송하지 않는다 (CSV 내보내기는 사람이 누를 때만).
 */
@Service
public class TalentService {
    static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    static final String SCREEN = "RESUME_REVIEW";
    static final String TARGET_TYPE = "RESUME_PROFILE";
    private static final Logger log = LoggerFactory.getLogger(TalentService.class);

    private final TalentProfileRepository profiles;
    private final UserRepository users;
    private final ResumeExtractionClient extractor;
    private final RevisionService revisions;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ExecutorService executor;
    private final ConcurrentLinkedDeque<Long> queue = new ConcurrentLinkedDeque<>();

    public TalentService(TalentProfileRepository profiles, UserRepository users, ResumeExtractionClient extractor,
                         RevisionService revisions, ObjectMapper mapper) {
        this(profiles, users, extractor, revisions, mapper, Clock.systemDefaultZone(), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "talent-extract");
            thread.setDaemon(true);
            return thread;
        }));
    }

    TalentService(TalentProfileRepository profiles, UserRepository users, ResumeExtractionClient extractor,
                  RevisionService revisions, ObjectMapper mapper, Clock clock, ExecutorService executor) {
        this.profiles = profiles;
        this.users = users;
        this.extractor = extractor;
        this.revisions = revisions;
        this.mapper = mapper;
        this.clock = clock;
        this.executor = executor;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @EventListener(ApplicationReadyEvent.class)
    void failInterrupted() {
        int count = profiles.failInterrupted("서버가 다시 시작되어 분석이 중단되었습니다. 이력서를 다시 올려 주세요.", now());
        if (count > 0) log.info("talent: 중단된 이력서 분석 {}건을 실패로 표시", count);
    }

    // ---------- 올리기 ----------

    public Summary upload(TalentViews.UploadRequest request, Long owner) {
        String fileName = request == null || request.fileName() == null ? "" : request.fileName().strip();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".doc") || lower.endsWith(".hwp"))
            throw new IllegalArgumentException("워드(.docx)만 읽을 수 있습니다. 워드에서 .docx로 다시 저장해 올려 주세요.");
        if (!lower.endsWith(".docx")) throw new IllegalArgumentException("워드(.docx) 이력서를 올려 주세요.");
        if (fileName.length() > 255) throw new IllegalArgumentException("파일 이름이 너무 깁니다.");
        String base64 = request.contentBase64() == null ? "" : request.contentBase64().strip();
        if (base64.isEmpty()) throw new IllegalArgumentException("파일 내용이 비어 있습니다.");
        if (base64.length() > (MAX_FILE_BYTES / 3 + 1) * 4) throw new IllegalArgumentException("파일이 너무 큽니다 (10MB 이하).");
        byte[] content;
        try {
            content = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("파일 내용(base64)이 올바르지 않습니다.");
        }
        if (content.length == 0) throw new IllegalArgumentException("파일 내용이 비어 있습니다.");
        if (content.length > MAX_FILE_BYTES) throw new IllegalArgumentException("파일이 너무 큽니다 (10MB 이하).");

        User user = users.findById(owner).orElseThrow(() -> new NoSuchElementException("사용자가 없습니다."));
        TalentProfile saved = profiles.save(new TalentProfile(user, fileName, now()));
        Long id = saved.getId();
        queue.addLast(id);
        executor.submit(() -> runExtraction(id, fileName, content));
        return summary(saved);
    }

    void runExtraction(Long id, String fileName, byte[] content) {
        try {
            Extraction extraction = extractor.extract(fileName, content, profileId(id), sourceId(id));
            ObjectNode document = (ObjectNode) extraction.resumeProfile().deepCopy();
            document.put("profileId", profileId(id));   // AI 모듈이 다른 ID를 돌려줘도 저장 ID로 맞춘다
            document.put("status", "DRAFT");
            saveExtracted(id, document, extraction);
        } catch (ExtractionException error) {
            saveFailed(id, error.getMessage());
        } catch (RuntimeException error) {
            // 예외 메시지에 원문 조각이 섞일 수 있어 로그·화면에는 종류만 남긴다
            log.warn("talent: 이력서 분석 실패 id={} ({})", id, error.getClass().getSimpleName());
            saveFailed(id, "이력서 분석 중 오류가 났습니다. 다시 올려 주세요.");
        } finally {
            queue.remove(id);
        }
    }

    private void saveExtracted(Long id, ObjectNode document, Extraction extraction) {
        profiles.findById(id).ifPresent(profile -> {
            if (profile.getStatus() != TalentStatus.PROCESSING) return;
            profile.markExtracted(write(document), extraction.sourceText(), extraction.llmProvider(),
                    extraction.llmModel(), extraction.llmFallbackReason(), TalentFields.from(document), now());
            profiles.save(profile);
        });
    }

    private void saveFailed(Long id, String message) {
        profiles.findById(id).ifPresent(profile -> {
            profile.markFailed(message, now());
            profiles.save(profile);
        });
    }

    // ---------- 조회 ----------

    /** 후보 풀 목록. 모든 필터는 선택이며, 기술은 쉼표로 여러 개를 주면 모두 가진 사람만 남긴다. */
    @Transactional(readOnly = true)
    public List<Summary> list(Long owner, TalentFilter filter) {
        List<Summary> rows = new ArrayList<>();
        for (TalentProfile profile : profiles.findByUserIdOrderByIdDesc(owner)) {
            if (filter.matches(profile)) rows.add(summary(profile));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Detail get(String profileId, Long owner) {
        TalentProfile profile = owned(profileId, owner);
        return detail(profile);
    }

    // ---------- 고치기 · 확정 · 지우기 ----------

    @Transactional
    public Detail update(String profileId, JsonNode patch, Long owner) {
        TalentProfile profile = owned(profileId, owner);
        if (profile.getStatus() != TalentStatus.DRAFT && profile.getStatus() != TalentStatus.CONFIRMED)
            throw new IllegalArgumentException("분석이 끝난 이력서만 고칠 수 있습니다.");
        ObjectNode document = (ObjectNode) read(profile.getDocument());
        List<TalentEdits.Change> changes = TalentEdits.apply(document, patch, mapper);
        if (!changes.isEmpty()) {
            for (TalentEdits.Change change : changes) {
                record(profile, change.fieldName(), change.proposedValue(), change.finalValue(), "EDITED",
                        change.proposedBy(), owner);
            }
            profile.replaceDocument(write(document), TalentFields.from(document), now());
        }
        return detail(profile);
    }

    @Transactional
    public Detail confirm(String profileId, Long owner, Long actor) {
        TalentProfile profile = owned(profileId, owner);
        if (profile.getStatus() != TalentStatus.DRAFT) throw new IllegalArgumentException("확인 전(초안) 이력서만 확정할 수 있습니다.");
        ObjectNode document = (ObjectNode) read(profile.getDocument());
        document.put("status", "CONFIRMED");
        // 고친 칸이 없어도 "그대로 받아들임"을 남겨 수정 없이 확정한 비율을 셀 수 있게 한다
        String proposer = "LLM".equals(document.path("generatedBy").asText()) ? "LLM" : "RULE";
        record(profile, "status", mapper.getNodeFactory().textNode("DRAFT"), mapper.getNodeFactory().textNode("CONFIRMED"),
                "ACCEPTED", proposer, owner);
        profile.confirm(write(document), actor, now());
        return detail(profile);
    }

    /** 이력서와 뽑은 내용을 지운다 (개인정보). 분석 중인 건은 끝난 뒤 지운다. */
    @Transactional
    public void delete(String profileId, Long owner) {
        TalentProfile profile = owned(profileId, owner);
        if (profile.getStatus() == TalentStatus.PROCESSING) throw new IllegalArgumentException("분석이 끝난 뒤 지울 수 있습니다.");
        profiles.delete(profile);
    }

    // ---------- CSV ----------

    /** 확정한 후보만. SALESMAP 연락처 가져오기용 칸 + 구분=인력 후보. 자동 전송하지 않는다. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long owner) {
        StringBuilder csv = new StringBuilder("﻿");   // 엑셀에서 한글이 깨지지 않게
        csv.append("이름,구분,생년,등급,주요 기술,총 경력(개월),투입 가능일,산업,프로필 ID,확정일\n");
        for (TalentProfile profile : profiles.findByUserIdOrderByIdDesc(owner)) {
            if (profile.getStatus() != TalentStatus.CONFIRMED) continue;
            Summary row = summary(profile);
            csv.append(String.join(",",
                    cell(row.name()), cell("인력 후보"), cell(row.birthYear()), cell(TalentFilter.gradeLabel(row.grade())),
                    cell(String.join("; ", row.skills())), cell(row.careerMonths()), cell(row.availableFrom()),
                    cell(String.join("; ", row.industries())), cell(row.profileId()),
                    cell(row.confirmedAt() == null ? null : row.confirmedAt().toLocalDate()))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 쉼표·따옴표·줄바꿈을 감싸고, 수식으로 읽힐 수 있는 칸(=+-@로 시작)은 앞에 '를 붙인다. */
    static String cell(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r"))
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        return text;
    }

    // ---------- 같은 사람 후보 ----------

    /** 이름(공백 무시)과 생년이 모두 같으면 같은 사람일 수 있다고 보여 준다. 합치지 않는다. */
    List<DuplicateCandidate> duplicates(TalentProfile profile, Long owner) {
        if (profile.getName() == null || profile.getBirthYear() == null) return List.of();
        String name = compact(profile.getName());
        return profiles.findByUserIdAndBirthYearAndIdNot(owner, profile.getBirthYear(), profile.getId()).stream()
                .filter(other -> other.getStatus() == TalentStatus.DRAFT || other.getStatus() == TalentStatus.CONFIRMED)
                .filter(other -> other.getName() != null && compact(other.getName()).equals(name))
                .map(other -> new DuplicateCandidate(profileId(other.getId()), other.getStatus().name(), other.getName(),
                        other.getBirthYear(), other.getFileName()))
                .toList();
    }

    // ---------- 도우미 ----------

    private void record(TalentProfile profile, String field, JsonNode proposed, JsonNode finalValue, String action,
                        String proposedBy, Long owner) {
        String model = "LLM".equals(proposedBy) ? profile.getLlmModel() : null;
        revisions.create(new RevisionCreateRequest(SCREEN, TARGET_TYPE, profileId(profile.getId()), field, proposed,
                finalValue, action, proposedBy, model), owner);
    }

    private Detail detail(TalentProfile profile) {
        JsonNode document = profile.getDocument() == null ? null : read(profile.getDocument());
        String target = profileId(profile.getId());
        List<RevisionResponse> edits = revisions.list(profile.getUser().getId()).stream()
                .filter(row -> TARGET_TYPE.equals(row.targetType()) && target.equals(row.targetId())).toList();
        return new Detail(summary(profile), document, profile.getSourceText(), profile.getLlmProvider(),
                profile.getLlmModel(), profile.getLlmFallbackReason(), duplicates(profile, profile.getUser().getId()), edits);
    }

    Summary summary(TalentProfile profile) {
        JsonNode document = profile.getDocument() == null ? mapper.createObjectNode() : read(profile.getDocument());
        List<String> skills = new ArrayList<>();
        document.path("skills").forEach(skill -> skills.add(skill.path("name").asText()));
        List<String> industries = new ArrayList<>();
        document.path("industries").forEach(industry -> industries.add(industry.path("name").asText()));
        List<String> missing = new ArrayList<>();
        document.path("missingFields").forEach(field -> missing.add(field.asText()));
        Integer position = null;
        if (profile.getStatus() == TalentStatus.PROCESSING) {
            int index = new ArrayList<>(queue).indexOf(profile.getId());
            position = index < 0 ? null : index + 1;
        }
        return new Summary(profileId(profile.getId()), profile.getStatus().name(), profile.getFileName(),
                profile.getName(), profile.getBirthYear(), TalentFields.text(document.path("grade").path("value")),
                profile.getCareerMonths(), profile.getAvailableFrom(), profile.getAvailableBasis(), skills, industries,
                document.path("projects").size(), missing, profile.getLlmFallbackReason() != null,
                profile.getErrorMessage(), position, profile.getCreatedAt(), profile.getConfirmedAt());
    }

    private TalentProfile owned(String profileId, Long owner) {
        Long id = parseId(profileId);
        return profiles.findByIdAndUserId(id, owner).orElseThrow(() -> new NoSuchElementException("인력 후보를 찾을 수 없습니다."));
    }

    static String profileId(Long id) { return "rp_" + id; }

    static String sourceId(Long id) { return "src_resume_" + id; }

    static Long parseId(String profileId) {
        if (profileId == null || !profileId.matches("rp_[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("인력 후보 ID 형식이 올바르지 않습니다.");
        return Long.parseLong(profileId.substring(3));
    }

    private static String compact(String value) {
        return value.replaceAll("(?U)\\s+", "");
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("저장된 인력 후보 데이터를 읽을 수 없습니다.");
        }
    }

    private String write(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("인력 후보 데이터를 저장할 수 없습니다.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
