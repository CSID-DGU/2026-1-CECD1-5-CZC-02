package com.salesmap.backend.minutes.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.dto.AiMinutesRequest;
import com.salesmap.backend.ai.dto.AiMinutesResponse;
import com.salesmap.backend.ai.exception.AiClientException;
import com.salesmap.backend.minutes.client.AttendeeMention;
import com.salesmap.backend.minutes.client.IdentityResolutionClient;
import com.salesmap.backend.minutes.client.RevisionClient;
import com.salesmap.backend.minutes.client.RevisionRecord;
import com.salesmap.backend.minutes.client.TaskDraftClient;
import com.salesmap.backend.minutes.dto.MeetingNoteCreateRequest;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import com.salesmap.backend.minutes.dto.MeetingNoteDraftResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteFromSourceResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteSummaryResponse;
import com.salesmap.backend.minutes.dto.MeetingNoteUpdateRequest;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteMode;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 회의록 저장·조회·수정·확정. 초안 만들기는 A 식별 → AI 모듈 /minutes → 저장 → C 할 일 순서로 묶는다.
 * 확정 전에는 아무것도 등록하지 않는다 (status=DRAFT).
 */
@Service
public class MeetingNoteService {

    private static final Logger log = LoggerFactory.getLogger(MeetingNoteService.class);
    private static final Pattern NOTE_ID_PATTERN = Pattern.compile("^mn_(\\d+)$");
    // contracts 공통 시각 형식 (KST, 시간대 표기 없음)
    private static final DateTimeFormatter CONTRACT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TITLE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DRAFT_NOTE_ID = "mn_draft";
    private static final String REVIEW_SCREEN = "MEETING_NOTE_REVIEW";
    private static final String TARGET_TYPE = "MEETING_NOTE";
    private static final List<String> FIELD_NAMES =
            List.of("discussion", "customerRequests", "concerns", "commitments", "nextContact");

    private final MeetingNoteRepository meetingNoteRepository;
    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;
    private final AiClient aiClient;
    private final IdentityResolutionClient identityResolutionClient;
    private final TaskDraftClient taskDraftClient;
    private final RevisionClient revisionClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public MeetingNoteService(
            MeetingNoteRepository meetingNoteRepository,
            SourceRepository sourceRepository,
            UserRepository userRepository,
            AiClient aiClient,
            IdentityResolutionClient identityResolutionClient,
            TaskDraftClient taskDraftClient,
            RevisionClient revisionClient,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.meetingNoteRepository = meetingNoteRepository;
        this.sourceRepository = sourceRepository;
        this.userRepository = userRepository;
        this.aiClient = aiClient;
        this.identityResolutionClient = identityResolutionClient;
        this.taskDraftClient = taskDraftClient;
        this.revisionClient = revisionClient;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 메모 → 회의록 초안. 트랜잭션을 걸지 않는다: AI 모듈 응답이 최대 330초(D-019)라 그동안 DB 트랜잭션을 잡지 않고,
     * 원본은 먼저 저장해 AI가 실패해도 메모가 남게 한다.
     */
    public MeetingNoteDraftResponse createDraft(MeetingNoteCreateRequest request, Long authenticatedUserId) {
        String requestedMode = normalizeMode(request.mode());
        List<String> attendees = normalizeAttendees(request.attendees());
        LocalDateTime occurredAt = request.occurredAt() == null ? null : request.occurredAt().truncatedTo(ChronoUnit.SECONDS);
        String location = blankToNull(request.location());

        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        Source source = sourceRepository.save(new Source(
                user,
                null,
                SourceType.MEETING_NOTE,
                null,
                buildSourceTitle(request.title(), occurredAt, location),
                request.text(),
                SourceStatus.CREATED,
                null
        ));
        List<AttendeeMention> mentions = attendees.stream()
                .map(name -> new AttendeeMention(name, null, "MEETING"))
                .toList();

        return draftNote(new DraftInput(
                user,
                source,
                List.of(new AiMinutesRequest.SourceRef(toSourceId(source.getId()), SourceType.MEETING_NOTE.name())),
                request.text(),
                occurredAt,
                location,
                requestedMode,
                mentions,
                null
        ), authenticatedUserId);
    }

    /**
     * 메일(같은 스레드 전체) → 회의록 초안 (B-5). 새 원본을 만들지 않고 기존 EMAIL 원본을 sourceRefs로 참조한다.
     * 같은 메일(스레드)로 이미 만든 회의록이 있으면 새로 만들지 않고 그 회의록을 알려 준다 (스레드당 회의록 하나).
     * createDraft와 같은 이유로 트랜잭션을 걸지 않는다.
     */
    public MeetingNoteFromSourceResponse createDraftFromSource(Long sourceId, Long authenticatedUserId) {
        Source selected = sourceRepository.findById(sourceId)
                .filter(source -> source.getStatus() != SourceStatus.DELETED)
                .orElseThrow(() -> new NoSuchElementException("메일을 찾을 수 없습니다."));
        if (!selected.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("해당 메일에 접근할 권한이 없습니다.");
        }
        if (selected.getSourceType() != SourceType.EMAIL) {
            throw new IllegalArgumentException("메일에서만 회의록을 만들 수 있습니다. 메모는 미팅 기록 화면에서 입력하세요.");
        }

        Long groupId = selected.getSourceGroup() == null ? null : selected.getSourceGroup().getId();
        String originKey = groupId == null ? "source-" + selected.getId() : "group-" + groupId;
        Optional<MeetingNote> existing = meetingNoteRepository.findFirstByUserIdAndOriginKeyOrderByIdDesc(authenticatedUserId, originKey);
        if (existing.isPresent()) {
            return new MeetingNoteFromSourceResponse(false, toNoteId(existing.get().getId()), null);
        }

        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        List<Source> messages = groupId == null ? List.of(selected) : threadMessages(groupId, selected, authenticatedUserId);
        LocalDateTime lastSentAt = MailThreadText.sentAt(messages.get(messages.size() - 1));

        try {
            MeetingNoteDraftResponse draft = draftNote(new DraftInput(
                    user,
                    selected,
                    messages.stream()
                            .map(message -> new AiMinutesRequest.SourceRef(toSourceId(message.getId()), SourceType.EMAIL.name()))
                            .toList(),
                    MailThreadText.build(messages),
                    lastSentAt == null ? null : lastSentAt.truncatedTo(ChronoUnit.SECONDS),
                    null,
                    null,
                    mailAttendees(messages, user.getEmail()),
                    originKey
            ), authenticatedUserId);
            return new MeetingNoteFromSourceResponse(true, draft.note().meetingNote().noteId(), draft);
        } catch (DataIntegrityViolationException exception) {
            // 같은 메일로 거의 동시에 두 번 만들기를 눌러 먼저 저장된 회의록이 있다 (uk_meeting_notes_user_origin)
            return meetingNoteRepository.findFirstByUserIdAndOriginKeyOrderByIdDesc(authenticatedUserId, originKey)
                    .map(note -> new MeetingNoteFromSourceResponse(false, toNoteId(note.getId()), null))
                    .orElseThrow(() -> exception);
        }
    }

    /** A 식별 → AI 모듈 /minutes → 저장 → C 할 일. 메모·메일 공통. */
    private MeetingNoteDraftResponse draftNote(DraftInput input, Long authenticatedUserId) {
        String sourceId = toSourceId(input.source().getId());
        List<String> attendees = input.mentions().stream().map(AttendeeMention::displayName).toList();

        // 1) A 식별
        List<JsonNode> identityResolutions = resolveIdentities(sourceId, input.mentions(), authenticatedUserId);

        // 2) B 회의록 (AI 모듈). sourceType=EMAIL이면 엔진이 메일마다 인용·서명을 줄 단위로 뺀다
        AiMinutesRequest aiRequest = new AiMinutesRequest(
                DRAFT_NOTE_ID,
                input.sourceRefs(),
                input.text(),
                formatContractDateTime(input.occurredAt()),
                input.location(),
                input.mode(),
                attendees,
                identityResolutions
        );
        MinutesDraft draft = generateMinutes(aiRequest);
        MeetingNoteDocument aiNote = draft.document();

        MeetingNote note = new MeetingNote(
                input.user(),
                input.source(),
                input.occurredAt(),
                input.location(),
                MeetingNoteMode.valueOf(aiNote.mode()),
                writeJson(aiNote.participants()),
                writeJson(aiNote.fields()),
                aiNote.perPerson() == null ? null : writeJson(aiNote.perPerson()),
                writeJson(aiNote.missingFields()),
                writeJson(aiNote.uncertainSegments() == null ? List.of() : aiNote.uncertainSegments()),
                aiNote.generatedBy(),
                truncate(draft.llmProvider(), 50),
                truncate(aiNote.llmModel(), 100),
                truncate(draft.llmFallbackReason(), 1000)
        );
        if (input.originKey() != null) {
            // 스레드면 원본 목록과 엔진에 보낸 원문(메일 구분 줄 포함)을 남긴다. 메일 한 통이면 source 하나로 충분
            boolean thread = input.sourceRefs().size() > 1;
            note.linkOrigin(
                    input.originKey(),
                    thread ? writeJson(input.sourceRefs()) : null,
                    thread ? input.text() : null
            );
        }
        note.recordAiSnapshot(writeJson(MeetingNoteEditableFields.valuesOf(aiNote, objectMapper)));
        MeetingNote savedNote = meetingNoteRepository.save(note);
        MeetingNoteDocument document = toDocument(savedNote);

        // 3) C 할 일 초안
        List<JsonNode> tasks = draftTasks(document, authenticatedUserId);

        return new MeetingNoteDraftResponse(
                toResponse(savedNote, document),
                identityResolutions,
                tasks,
                draft.droppedItems()
        );
    }

    /** 스레드의 메일(보낸·받은 모두, 시간순). 지운 메일과 남의 메일은 뺀다. */
    private List<Source> threadMessages(Long groupId, Source selected, Long userId) {
        List<Source> messages = sourceRepository.findBySourceGroupIdOrderBySentAtAscIdAsc(groupId).stream()
                .filter(message -> message.getStatus() != SourceStatus.DELETED)
                .filter(message -> message.getSourceType() == SourceType.EMAIL)
                .filter(message -> message.getUser().getId().equals(userId))
                .toList();
        return messages.isEmpty() ? List.of(selected) : messages;
    }

    /**
     * 메일의 상대: 발신자·수신자 이름과 주소. 내 주소는 빼고, 같은 주소는 한 번만 (이름을 아는 쪽을 남김).
     * 수신자 이름·주소는 쉼표로 이어 저장돼 있어 개수가 같을 때만 짝짓는다 — 다르면 이름은 모름(null).
     */
    private List<AttendeeMention> mailAttendees(List<Source> messages, String ownEmail) {
        Map<String, AttendeeMention> byAddress = new LinkedHashMap<>();
        for (Source message : messages) {
            addMailAttendee(byAddress, message.getSenderName(), message.getSenderEmail(), ownEmail);
            List<String> names = splitList(message.getReceiverNames());
            List<String> emails = splitList(message.getReceiverEmails());
            for (int index = 0; index < emails.size(); index++) {
                String name = names.size() == emails.size() ? names.get(index) : null;
                addMailAttendee(byAddress, name, emails.get(index), ownEmail);
            }
        }
        return List.copyOf(byAddress.values());
    }

    private void addMailAttendee(Map<String, AttendeeMention> byAddress, String name, String email, String ownEmail) {
        String cleanName = blankToNull(name);
        String cleanEmail = blankToNull(email);
        if (cleanEmail == null && cleanName == null) {
            return;
        }
        if (cleanEmail != null && cleanEmail.equalsIgnoreCase(ownEmail)) {
            return;
        }
        String key = cleanEmail != null ? cleanEmail.toLowerCase() : "name:" + cleanName;
        AttendeeMention current = byAddress.get(key);
        if (current == null || (current.name() == null && cleanName != null)) {
            byAddress.put(key, new AttendeeMention(cleanName, cleanEmail, "EMAIL"));
        }
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    @Transactional(readOnly = true)
    public List<MeetingNoteSummaryResponse> getMeetingNotes(String status, Long authenticatedUserId) {
        List<MeetingNote> notes = status == null || status.isBlank()
                ? meetingNoteRepository.findByUserId(authenticatedUserId)
                : meetingNoteRepository.findByUserIdAndStatus(authenticatedUserId, parseStatus(status));

        return notes.stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingNoteResponse getMeetingNote(String noteId, Long authenticatedUserId) {
        MeetingNote note = findOwnedNote(noteId, authenticatedUserId);
        return toResponse(note, toDocument(note));
    }

    /** 문항 수정. 바뀐 값마다 D의 수정 기록 API를 부른다 (proposedValue = AI가 처음 만든 값). */
    @Transactional
    public MeetingNoteResponse updateMeetingNote(String noteId, MeetingNoteUpdateRequest request, Long authenticatedUserId) {
        MeetingNote note = findOwnedNote(noteId, authenticatedUserId);
        if (note.getStatus() != MeetingNoteStatus.DRAFT) {
            throw new IllegalArgumentException("확정된 회의록은 고칠 수 없습니다.");
        }

        MeetingNoteDocument before = toDocument(note);
        MeetingNoteDocument.Fields fields = before.fields();
        List<MeetingNoteDocument.PerPerson> perPerson = before.perPerson();

        if (request.fields() != null) {
            if (note.getMode() == MeetingNoteMode.LIGHT) {
                throw new IllegalArgumentException("LIGHT 회의록은 perPerson만 고칠 수 있습니다.");
            }
            fields = mergeFields(fields, request.fields());
        }
        if (request.perPerson() != null) {
            if (note.getMode() == MeetingNoteMode.FULL) {
                throw new IllegalArgumentException("FULL 회의록은 fields만 고칠 수 있습니다.");
            }
            perPerson = mergePerPerson(perPerson, request.perPerson());
        }

        note.updateContent(
                writeJson(fields),
                perPerson == null ? null : writeJson(perPerson),
                writeJson(computeMissingFields(note.getMode(), fields, perPerson, before.missingFields()))
        );
        MeetingNoteDocument after = toDocument(note);
        recordEdits(note, before, after, authenticatedUserId);

        return toResponse(note, after);
    }

    /**
     * 확정. 할 일은 사람이 하나씩 확정하므로 남은 DRAFT 할 일이 있으면 C가 거부한다 (D-023 ⑤, 일괄 확정 안 함).
     * 고치지 않은 값은 ACCEPTED로 기록한다(D 수정 기록, 같은 트랜잭션). 일정(D)은 커밋된 뒤에 넘긴다.
     */
    @Transactional
    public MeetingNoteResponse confirmMeetingNote(String noteId, Long authenticatedUserId) {
        MeetingNote note = findOwnedNote(noteId, authenticatedUserId);
        if (note.getStatus() == MeetingNoteStatus.CONFIRMED) {
            throw new IllegalArgumentException("이미 확정된 회의록입니다.");
        }
        MeetingNoteDocument draft = toDocument(note);
        taskDraftClient.confirmTasks(draft, authenticatedUserId);

        Map<String, JsonNode> aiValues = readAiSnapshot(note);
        Map<String, JsonNode> finalValues = MeetingNoteEditableFields.valuesOf(draft, objectMapper);
        boolean withoutEdit = true;
        for (Map.Entry<String, JsonNode> entry : finalValues.entrySet()) {
            JsonNode aiValue = aiValues.get(entry.getKey());
            if (Objects.equals(aiValue, entry.getValue())) {
                revisionClient.record(
                        toRevision(note, entry.getKey(), aiValue, entry.getValue(), "ACCEPTED", authenticatedUserId),
                        authenticatedUserId
                );
            } else {
                withoutEdit = false;
            }
        }

        note.markConfirmed(withoutEdit);
        MeetingNoteDocument document = toDocument(note);
        // 캘린더 같은 외부 효과는 커밋 뒤에 (MeetingNoteHandoffListener)
        eventPublisher.publishEvent(new MeetingNoteConfirmedEvent(document, authenticatedUserId));

        return toResponse(note, document);
    }

    // ---------- 초안 만들기 단계 ----------

    private List<JsonNode> resolveIdentities(String sourceId, List<AttendeeMention> mentions, Long userId) {
        if (mentions.isEmpty()) {
            return List.of();
        }
        try {
            return identityResolutionClient.resolve(sourceId, mentions, userId);
        } catch (RuntimeException exception) {
            // 식별 실패는 회의록을 막지 않는다. 참석자는 이름만 두고 personId는 null.
            log.warn("[MINUTES] identity resolution failed. sourceId={}", sourceId, exception);
            return List.of();
        }
    }

    private MinutesDraft generateMinutes(AiMinutesRequest request) {
        try {
            AiMinutesResponse response = aiClient.generateMinutes(request);
            MeetingNoteDocument document = toValidDocument(response.meetingNote());
            return new MinutesDraft(
                    document,
                    response.llmProvider(),
                    response.llmFallbackReason(),
                    response.droppedItems() == null ? List.of() : response.droppedItems()
            );
        } catch (AiClientException | IllegalStateException exception) {
            // AI 모듈의 LLM 실패 처리와 같다: 내용은 비우고 모든 칸을 missingFields에. 지어내지 않는다.
            log.warn("[MINUTES] AI module /minutes failed. sourceRefs={}", request.sourceRefs(), exception);
            return new MinutesDraft(
                    emptyNote(request),
                    "rule",
                    "AI 모듈 회의록 생성 실패: " + exception.getMessage(),
                    List.of()
            );
        }
    }

    private List<JsonNode> draftTasks(MeetingNoteDocument document, Long userId) {
        try {
            return taskDraftClient.draftTasks(document, userId);
        } catch (RuntimeException exception) {
            // 할 일 추출 실패는 회의록 저장을 되돌리지 않는다. 확인 화면에서 다시 시도할 수 있다.
            log.warn("[MINUTES] task draft failed. noteId={}", document.noteId(), exception);
            return List.of();
        }
    }

    private MeetingNoteDocument toValidDocument(JsonNode meetingNote) {
        MeetingNoteDocument document;
        try {
            document = objectMapper.treeToValue(meetingNote, MeetingNoteDocument.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("AI 모듈 응답이 회의록 형식이 아닙니다.", exception);
        }
        if (document == null
                || document.fields() == null
                || document.participants() == null
                || document.missingFields() == null
                || !("FULL".equals(document.mode()) || "LIGHT".equals(document.mode()))
                || ("LIGHT".equals(document.mode()) && document.perPerson() == null)) {
            throw new IllegalStateException("AI 모듈 응답이 회의록 형식이 아닙니다.");
        }
        return document;
    }

    private MeetingNoteDocument emptyNote(AiMinutesRequest request) {
        String mode = "LIGHT".equals(request.mode()) ? "LIGHT" : "FULL";
        List<MeetingNoteDocument.Participant> participants = request.attendees().stream()
                .map(name -> linkParticipant(name, request.identityResolutions()))
                .toList();
        List<MeetingNoteDocument.PerPerson> perPerson = "LIGHT".equals(mode)
                ? participants.stream()
                        .map(participant -> new MeetingNoteDocument.PerPerson(
                                participant.displayName(), participant.personId(), null, null, null))
                        .toList()
                : null;
        MeetingNoteDocument.Fields fields = new MeetingNoteDocument.Fields(List.of(), List.of(), List.of(), List.of(), List.of());
        MeetingNoteMode noteMode = MeetingNoteMode.valueOf(mode);

        return new MeetingNoteDocument(
                request.noteId(),
                request.sourceRefs().stream()
                        .map(ref -> new MeetingNoteDocument.SourceRef(ref.sourceId(), ref.sourceType()))
                        .toList(),
                request.occurredAt(),
                request.location(),
                mode,
                participants,
                fields,
                perPerson,
                computeMissingFields(noteMode, fields, perPerson, List.copyOf(FIELD_NAMES)),
                List.of(),
                MeetingNoteStatus.DRAFT.name(),
                "RULE",
                null
        );
    }

    /** A 식별 결과 중 mention.name이 같은 것을 연결한다 (AI 모듈 engine과 같은 규칙). */
    private MeetingNoteDocument.Participant linkParticipant(String name, List<JsonNode> identityResolutions) {
        for (JsonNode resolution : identityResolutions) {
            if (name.equals(resolution.path("mention").path("name").asText(null))) {
                return new MeetingNoteDocument.Participant(
                        name,
                        resolution.path("resolutionId").asText(null),
                        resolution.path("personId").asText(null)
                );
            }
        }
        return new MeetingNoteDocument.Participant(name, null, null);
    }

    // ---------- 수정 ----------

    private MeetingNoteDocument.Fields mergeFields(MeetingNoteDocument.Fields current, MeetingNoteUpdateRequest.FieldsUpdate update) {
        return new MeetingNoteDocument.Fields(
                update.discussion() == null ? current.discussion() : normalizeItems(update.discussion(), "discussion"),
                update.customerRequests() == null ? current.customerRequests() : normalizeItems(update.customerRequests(), "customerRequests"),
                update.concerns() == null ? current.concerns() : normalizeItems(update.concerns(), "concerns"),
                update.commitments() == null ? current.commitments() : normalizeItems(update.commitments(), "commitments"),
                update.nextContact() == null ? current.nextContact() : normalizeItems(update.nextContact(), "nextContact")
        );
    }

    private List<MeetingNoteDocument.PerPerson> mergePerPerson(
            List<MeetingNoteDocument.PerPerson> current,
            List<MeetingNoteDocument.PerPerson> update
    ) {
        if (current == null || update.size() != current.size()) {
            throw new IllegalArgumentException("perPerson은 기존과 같은 인원(" + (current == null ? 0 : current.size()) + "명)으로 보내야 합니다.");
        }

        List<MeetingNoteDocument.PerPerson> merged = new ArrayList<>();
        for (int index = 0; index < current.size(); index++) {
            MeetingNoteDocument.PerPerson existing = current.get(index);
            MeetingNoteDocument.PerPerson requested = update.get(index);
            if (requested == null) {
                throw new IllegalArgumentException("perPerson[" + index + "]이 비어 있습니다.");
            }
            String prefix = "perPerson[" + index + "].";
            // 이름·personId는 참석자 영역(A) 값이므로 여기서 바꾸지 않는다.
            merged.add(new MeetingNoteDocument.PerPerson(
                    existing.displayName(),
                    existing.personId(),
                    normalizeNullableItem(requested.status(), prefix + "status"),
                    normalizeNullableItem(requested.request(), prefix + "request"),
                    normalizeNullableItem(requested.followUp(), prefix + "followUp")
            ));
        }
        return merged;
    }

    private List<MeetingNoteDocument.Item> normalizeItems(List<MeetingNoteDocument.Item> items, String fieldName) {
        List<MeetingNoteDocument.Item> normalized = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            MeetingNoteDocument.Item item = normalizeNullableItem(items.get(index), fieldName + "[" + index + "]");
            if (item == null) {
                throw new IllegalArgumentException(fieldName + "[" + index + "]이 비어 있습니다.");
            }
            normalized.add(item);
        }
        return normalized;
    }

    /** contracts 형식상 항목은 text·evidence가 모두 있어야 한다. */
    private MeetingNoteDocument.Item normalizeNullableItem(MeetingNoteDocument.Item item, String path) {
        if (item == null) {
            return null;
        }
        String text = blankToNull(item.text());
        String evidence = blankToNull(item.evidence());
        if (text == null || evidence == null) {
            throw new IllegalArgumentException(path + "의 text와 evidence(원문 근거)는 비울 수 없습니다.");
        }
        return new MeetingNoteDocument.Item(text, evidence);
    }

    /**
     * 빈 문항은 missingFields에 (AI 모듈 engine.missing_fields와 같은 규칙, D-018).
     * LIGHT인데 사람이 없으면 칸 단위로 적을 수 없으므로 기존 값을 그대로 둔다.
     */
    private List<String> computeMissingFields(
            MeetingNoteMode mode,
            MeetingNoteDocument.Fields fields,
            List<MeetingNoteDocument.PerPerson> perPerson,
            List<String> fallback
    ) {
        List<String> missing = new ArrayList<>();
        if (mode == MeetingNoteMode.LIGHT) {
            if (perPerson == null || perPerson.isEmpty()) {
                return fallback;
            }
            for (int index = 0; index < perPerson.size(); index++) {
                MeetingNoteDocument.PerPerson person = perPerson.get(index);
                if (person.status() == null) {
                    missing.add("perPerson[" + index + "].status");
                }
                if (person.request() == null) {
                    missing.add("perPerson[" + index + "].request");
                }
                if (person.followUp() == null) {
                    missing.add("perPerson[" + index + "].followUp");
                }
            }
            return missing;
        }

        if (fields.discussion().isEmpty()) {
            missing.add("discussion");
        }
        if (fields.customerRequests().isEmpty()) {
            missing.add("customerRequests");
        }
        if (fields.concerns().isEmpty()) {
            missing.add("concerns");
        }
        if (fields.commitments().isEmpty()) {
            missing.add("commitments");
        }
        if (fields.nextContact().isEmpty()) {
            missing.add("nextContact");
        }
        return missing;
    }

    private void recordEdits(MeetingNote note, MeetingNoteDocument before, MeetingNoteDocument after, Long userId) {
        Map<String, JsonNode> aiValues = readAiSnapshot(note);
        Map<String, JsonNode> beforeValues = MeetingNoteEditableFields.valuesOf(before, objectMapper);
        MeetingNoteEditableFields.valuesOf(after, objectMapper).forEach((fieldName, finalValue) -> {
            if (Objects.equals(beforeValues.get(fieldName), finalValue)) {
                return;
            }
            revisionClient.record(toRevision(note, fieldName, aiValues.get(fieldName), finalValue, "EDITED", userId), userId);
        });
    }

    private RevisionRecord toRevision(
            MeetingNote note,
            String fieldName,
            JsonNode proposedValue,
            JsonNode finalValue,
            String action,
            Long userId
    ) {
        return new RevisionRecord(
                REVIEW_SCREEN,
                TARGET_TYPE,
                toNoteId(note.getId()),
                fieldName,
                proposedValue == null ? NullNode.getInstance() : proposedValue,
                finalValue,
                action,
                "RULE".equals(note.getGeneratedBy()) ? "RULE" : "LLM",
                note.getLlmModel(),
                "user_" + userId,
                LocalDateTime.now().format(CONTRACT_DATE_TIME)
        );
    }

    private Map<String, JsonNode> readAiSnapshot(MeetingNote note) {
        if (note.getAiSnapshot() == null || note.getAiSnapshot().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(note.getAiSnapshot(), new TypeReference<Map<String, JsonNode>>() {
            });
        } catch (JsonProcessingException exception) {
            log.warn("Failed to read meeting note AI snapshot. noteId={}", toNoteId(note.getId()));
            return Map.of();
        }
    }

    // ---------- 변환 ----------

    private MeetingNoteDocument toDocument(MeetingNote note) {
        Source source = note.getSource();
        // 메일 스레드면 만들 때 남긴 원본 목록, 아니면 원본 하나
        List<MeetingNoteDocument.SourceRef> sourceRefs = note.getSourceRefsJson() == null
                ? List.of(new MeetingNoteDocument.SourceRef(toSourceId(source.getId()), source.getSourceType().name()))
                : readJson(note.getSourceRefsJson(), new TypeReference<List<MeetingNoteDocument.SourceRef>>() {
                });
        return new MeetingNoteDocument(
                toNoteId(note.getId()),
                sourceRefs,
                formatContractDateTime(note.getOccurredAt()),
                note.getLocation(),
                note.getMode().name(),
                readJson(note.getParticipantsJson(), new TypeReference<List<MeetingNoteDocument.Participant>>() {
                }),
                readJson(note.getFieldsJson(), new TypeReference<MeetingNoteDocument.Fields>() {
                }),
                note.getPerPersonJson() == null ? null
                        : readJson(note.getPerPersonJson(), new TypeReference<List<MeetingNoteDocument.PerPerson>>() {
                        }),
                readJson(note.getMissingFieldsJson(), new TypeReference<List<String>>() {
                }),
                readJson(note.getUncertainSegmentsJson(), new TypeReference<List<MeetingNoteDocument.UncertainSegment>>() {
                }),
                note.getStatus().name(),
                note.getGeneratedBy(),
                note.getLlmModel()
        );
    }

    private MeetingNoteResponse toResponse(MeetingNote note, MeetingNoteDocument document) {
        Source source = note.getSource();
        return new MeetingNoteResponse(
                document,
                new MeetingNoteResponse.SourceInfo(
                        toSourceId(source.getId()),
                        source.getSourceType().name(),
                        source.getTitle(),
                        // 스레드면 엔진에 보낸 원문(메일 구분 줄 포함), 아니면 원본 본문. 둘 다 인용·서명 제거 전
                        note.getSourceText() != null ? note.getSourceText() : source.getContent()
                ),
                note.getLlmProvider(),
                note.getLlmFallbackReason(),
                note.getConfirmedAt(),
                note.getConfirmedWithoutEdit(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    private MeetingNoteSummaryResponse toSummary(MeetingNote note) {
        List<MeetingNoteDocument.Participant> participants = readJson(
                note.getParticipantsJson(),
                new TypeReference<List<MeetingNoteDocument.Participant>>() {
                }
        );
        List<String> missingFields = readJson(note.getMissingFieldsJson(), new TypeReference<List<String>>() {
        });

        return new MeetingNoteSummaryResponse(
                toNoteId(note.getId()),
                note.getStatus(),
                note.getMode(),
                note.getOccurredAt(),
                note.getLocation(),
                participants.stream().map(MeetingNoteDocument.Participant::displayName).toList(),
                note.getSource().getTitle(),
                missingFields.size(),
                note.getCreatedAt(),
                note.getConfirmedAt()
        );
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 회의록 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("회의록 JSON을 만들 수 없습니다.", exception);
        }
    }

    // ---------- 작은 규칙 ----------

    static String toNoteId(Long id) {
        return "mn_" + id;
    }

    private static String toSourceId(Long id) {
        return "src_" + id;
    }

    private MeetingNote findOwnedNote(String noteId, Long authenticatedUserId) {
        MeetingNote note = meetingNoteRepository.findById(parseNoteId(noteId))
                .orElseThrow(() -> new NoSuchElementException("회의록을 찾을 수 없습니다."));
        if (!note.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("해당 회의록에 접근할 권한이 없습니다.");
        }
        return note;
    }

    private Long parseNoteId(String noteId) {
        Matcher matcher = NOTE_ID_PATTERN.matcher(noteId == null ? "" : noteId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("회의록 ID는 mn_<숫자> 형식이어야 합니다.");
        }
        return Long.parseLong(matcher.group(1));
    }

    private MeetingNoteStatus parseStatus(String status) {
        try {
            return MeetingNoteStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status는 DRAFT, CONFIRMED 중 하나여야 합니다.");
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        String normalized = mode.trim().toUpperCase();
        if (!"FULL".equals(normalized) && !"LIGHT".equals(normalized)) {
            throw new IllegalArgumentException("mode는 FULL, LIGHT 중 하나이거나 비워야 합니다.");
        }
        return normalized;
    }

    private List<String> normalizeAttendees(List<String> attendees) {
        if (attendees == null) {
            return List.of();
        }
        return attendees.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }

    private String buildSourceTitle(String title, LocalDateTime occurredAt, String location) {
        String requested = blankToNull(title);
        if (requested != null) {
            return truncate(requested, 255);
        }
        StringBuilder builder = new StringBuilder("미팅 메모");
        if (occurredAt != null) {
            builder.append(' ').append(occurredAt.format(TITLE_DATE_TIME));
        }
        if (location != null) {
            builder.append(" · ").append(location);
        }
        return truncate(builder.toString(), 255);
    }

    private String formatContractDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(CONTRACT_DATE_TIME);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record DraftInput(
            User user,
            // 회의록이 붙는 원본 (메모, 또는 메일에서 회의록 만들기를 누른 메일)
            Source source,
            List<AiMinutesRequest.SourceRef> sourceRefs,
            String text,
            LocalDateTime occurredAt,
            String location,
            String mode,
            List<AttendeeMention> mentions,
            // 메일이면 "group-<PK>"·"source-<PK>", 메모는 null
            String originKey
    ) {
    }

    private record MinutesDraft(
            MeetingNoteDocument document,
            String llmProvider,
            String llmFallbackReason,
            List<AiMinutesResponse.DroppedItem> droppedItems
    ) {
    }
}
