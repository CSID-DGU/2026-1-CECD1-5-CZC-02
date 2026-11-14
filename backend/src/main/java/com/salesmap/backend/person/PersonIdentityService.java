package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.minutes.client.RevisionClient;
import com.salesmap.backend.minutes.client.RevisionRecord;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.repository.SourceRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PersonIdentityService {
    private final IdentityResolutionRepository rows;
    private final PersonService people;
    private final SourceRepository sources;
    private final MeetingNoteRepository notes;
    private final RevisionClient revisions;
    private final ObjectMapper mapper;

    public PersonIdentityService(IdentityResolutionRepository rows,PersonService people,SourceRepository sources,
            MeetingNoteRepository notes,RevisionClient revisions,ObjectMapper mapper){
        this.rows=rows;this.people=people;this.sources=sources;this.notes=notes;this.revisions=revisions;this.mapper=mapper;
    }

    /** A API에서 검사한 제안만 저장한다. 재실행으로 원본 제안이나 사람 결정을 덮어쓰지 않는다. */
    @Transactional
    public List<JsonNode> storeProposals(String sourceRef,List<JsonNode> proposals,Long owner){
        requireUser(owner);
        Source source=sources.findById(parseId(sourceRef,"src_"))
                .filter(s -> s.getUser().getId().equals(owner) && s.getStatus()!=SourceStatus.DELETED)
                .orElseThrow(() -> new NoSuchElementException("원본이 없거나 접근할 수 없습니다."));
        List<IdentityResolutionEntry> existing=rows.findByUserIdAndSourceRefOrderByMentionIndexAsc(owner,sourceRef);
        if(!existing.isEmpty()){
            if(existing.size()!=proposals.size()) throw conflict("원본의 식별 단서가 달라졌습니다. 담당자 확인이 필요합니다.");
            for(int i=0;i<existing.size();i++)
                if(!read(existing.get(i).getProposal()).get("mention").equals(proposals.get(i).get("mention")))
                    throw conflict("원본의 식별 단서가 달라졌습니다. 담당자 확인이 필요합니다.");
            return existing.stream().map(row -> read(row.getDocument())).toList();
        }
        LocalDateTime now=now();
        for(int i=0;i<proposals.size();i++){
            JsonNode proposal=proposals.get(i);
            IdentityResponseValidator.validateProposalPolicy(proposal);
            if(!sourceRef.equals(proposal.path("mention").path("sourceRef").asText()))
                throw new IllegalArgumentException("검증한 원본 RULE/MODEL 제안만 저장할 수 있습니다.");
            rows.save(new IdentityResolutionEntry(source.getUser(),sourceRef,i,proposal.path("resolutionId").asText(),proposal.toString(),now));
        }
        return proposals.stream().map(JsonNode::deepCopy).map(value -> (JsonNode)value).toList();
    }

    @Transactional(readOnly=true)
    public List<IdentityReviewItem> list(String noteId,Long owner){
        MeetingNote note=ownedNote(noteId,owner);
        return rows.findByUserIdAndSourceRefOrderByMentionIndexAsc(owner,"src_"+note.getSource().getId())
                .stream().map(this::item).toList();
    }

    /** 선택·신규 인물·D 수정 기록은 한 트랜잭션. 기록 실패 시 결정을 성공으로 처리하지 않는다. */
    @Transactional
    public IdentityReviewItem select(String noteId,String resolutionId,JsonNode body,String expectedEtag,Long owner,Long actor){
        requireUser(actor);
        MeetingNote note=ownedNote(noteId,owner);
        if(note.isArchived() || note.getStatus()!=MeetingNoteStatus.DRAFT)
            throw conflict("확정·보관한 회의록의 인물 선택은 변경할 수 없습니다.");
        if(resolutionId==null || !resolutionId.matches("ir_[0-9a-z_]+")) throw new IllegalArgumentException("유효한 식별 ID가 필요합니다.");
        IdentityResolutionEntry row=rows.lockOwned(resolutionId,owner)
                .filter(r -> r.getSourceRef().equals("src_"+note.getSource().getId()))
                .orElseThrow(() -> new NoSuchElementException("식별 결과가 없거나 접근할 수 없습니다."));
        if(!etag(row).equals(expectedEtag)) throw conflict("다른 화면에서 바뀌었습니다. 최신 결과를 다시 확인해 주세요.");
        JsonNode current=read(row.getDocument());
        boolean humanBefore="HUMAN".equals(current.path("decidedBy").asText());
        if(body==null || !body.isObject() || body.size()!=1 || !(body.has("personId") || body.has("newPerson")))
            throw new IllegalArgumentException("personId 또는 newPerson 중 하나가 필요합니다.");
        JsonNode proposal=read(row.getProposal());
        boolean create=body.has("newPerson");
        PersonDocument selected;
        if(create) {
            if(!body.get("newPerson").isObject()) throw new IllegalArgumentException("newPerson은 인물 객체여야 합니다.");
            selected=people.create(body.get("newPerson"),owner,actor);
        }
        else {
            if(!body.get("personId").isTextual()) throw new IllegalArgumentException("인물 ID는 문자열이어야 합니다.");
            selected=people.get(body.get("personId").textValue(),owner);
        }
        if(!create && humanBefore && selected.personId().equals(current.path("personId").asText())) return item(row);
        boolean candidate=false;
        for(JsonNode c:proposal.path("candidates")) if(selected.personId().equals(c.path("personId").asText())) candidate=true;
        String action=create ? "CREATED_NEW" : humanBefore ? "EDITED" : selected.personId().equals(proposal.path("personId").asText())
                ? "ACCEPTED" : candidate ? "SELECTED_CANDIDATE" : "EDITED";
        ObjectNode decided=(ObjectNode)proposal.deepCopy();
        decided.put("decision","CONFIRMED").put("personId",selected.personId()).put("decidedBy","HUMAN");
        ObjectNode choice=decided.putArray("candidates").addObject().put("personId",selected.personId()).putNull("score");
        choice.putArray("reasons").add(create ? "사용자가 새 인물을 등록해 선택함" : "사용자가 인물 대장을 확인해 선택함");
        LocalDateTime now=now();
        revisions.record(new RevisionRecord("IDENTITY_CANDIDATE","IDENTITY_RESOLUTION",row.getResolutionId(),"personId",
                proposal.get("personId"),mapper.valueToTree(selected.personId()),action,proposal.path("decidedBy").asText(),
                proposal.path("modelVersion").asText(null),"user_"+owner,now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))),owner);
        row.decide(decided.toString(),actor,now);
        return item(rows.saveAndFlush(row));
    }

    private MeetingNote ownedNote(String noteId,Long owner){
        requireUser(owner);
        return notes.findById(parseId(noteId,"mn_"))
                .filter(n -> n.getUser().getId().equals(owner) && n.getSource().getUser().getId().equals(owner))
                .orElseThrow(() -> new NoSuchElementException("회의록이 없거나 접근할 수 없습니다."));
    }
    private static long parseId(String value,String prefix){
        if(value==null || !value.matches(prefix+"[1-9][0-9]*")) throw new IllegalArgumentException("유효한 원본·회의록 ID가 필요합니다.");
        try{return Long.parseLong(value.substring(prefix.length()));}
        catch(NumberFormatException e){throw new IllegalArgumentException("유효한 원본·회의록 ID가 필요합니다.");}
    }
    private static void requireUser(Long user){if(user==null || user<1)throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);}
    private static IdentitySelectionConflictException conflict(String message){return new IdentitySelectionConflictException(message);}
    private JsonNode read(String value){try{return mapper.readTree(value);}catch(Exception e){throw new IllegalStateException("식별 저장 형식 오류",e);}}
    private static String etag(IdentityResolutionEntry row){return "\"irv-"+(row.getVersion()==null ? 0 : row.getVersion())+"\"";}
    private IdentityReviewItem item(IdentityResolutionEntry row){return new IdentityReviewItem(read(row.getDocument()),etag(row));}
}
