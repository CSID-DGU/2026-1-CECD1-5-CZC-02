package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class PersonMergeService {
    private final PersonRepository rows;
    private final PersonMergeRepository audit;
    private final PersonService people;
    private final ObjectMapper mapper;
    public PersonMergeService(PersonRepository rows,PersonMergeRepository audit,PersonService people,ObjectMapper mapper){
        this.rows=rows;this.audit=audit;this.people=people;this.mapper=mapper;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PersonMergeView merge(String targetId,JsonNode body,Long owner,Long actor){
        authenticated(owner,actor);
        if(body==null || !body.isObject() || body.size()!=2 || !body.path("sourcePersonId").isTextual())
            throw new IllegalArgumentException("sourcePersonId와 confirmed가 필요합니다.");
        confirmed(body);
        long target=PersonService.parseId(targetId),source=PersonService.parseId(body.path("sourcePersonId").asText());
        if(source==target) throw new IllegalArgumentException("서로 다른 두 인물을 선택해 주세요.");
        List<PersonEntry> locked=lockPair(source,target,owner);
        PersonEntry sourceRow=find(locked,source),targetRow=find(locked,target);
        if(sourceRow.getMergedIntoId()!=null || targetRow.getMergedIntoId()!=null)
            throw conflict("이미 병합된 인물입니다. 목록을 새로 불러와 주세요.");
        if(!rows.findByUserIdAndMergedIntoIdOrderByIdAsc(owner,source).isEmpty())
            throw conflict("합쳐진 인물을 다시 병합하려면 기존 병합을 먼저 되돌려 주세요.");
        String sourceBefore=write(people.document(sourceRow));
        String targetBefore=write(people.combined(targetRow,rows.findByUserIdAndMergedIntoIdOrderByIdAsc(owner,target)));
        sourceRow.mergeInto(target);
        // 원래 스칼라 값은 대표 인물에 남기고, 등록된 연락처·별칭만 함께 검색한다.
        refresh(targetRow,owner);
        PersonMergeEntry record=audit.saveAndFlush(new PersonMergeEntry(owner,source,target,sourceBefore,targetBefore,actor,now()));
        return view(record);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PersonMergeView undo(String targetId,String mergeId,JsonNode body,Long owner,Long actor){
        authenticated(owner,actor);
        if(body==null || !body.isObject() || body.size()!=1) throw new IllegalArgumentException("confirmed가 필요합니다.");
        confirmed(body);
        long target=PersonService.parseId(targetId),id=parseMergeId(mergeId);
        PersonMergeRepository.Coordinates location=audit.coordinates(id,owner)
                .filter(row -> row.getTargetId().equals(target))
                .orElseThrow(() -> new NoSuchElementException("병합 기록이 없거나 접근할 수 없습니다."));
        List<PersonEntry> locked=lockPair(location.getSourceId(),target,owner);
        // 처음에는 좌표만 읽는다. 잠금 후 최신 감사 엔티티를 읽어 동시 해제를 직렬화한다.
        PersonMergeEntry record=audit.lockOwned(id,owner).orElseThrow(() -> new NoSuchElementException("병합 기록이 없습니다."));
        PersonEntry source=find(locked,record.getSourceId()),root=find(locked,target);
        if(!record.active() || !Objects.equals(source.getMergedIntoId(),target) || root.getMergedIntoId()!=null)
            throw conflict("이미 되돌렸거나 병합 상태가 바뀌었습니다. 새로 불러와 주세요.");
        source.mergeInto(null);refresh(root,owner);
        record.undo(actor,now());audit.flush();
        return view(record);
    }
    @Transactional(readOnly=true)
    public PersonMergeView.History history(String personId,int page,int size,Long owner){
        authenticated(owner,owner);
        PersonDocument root=people.get(personId,owner);
        if(page<0 || page>1000000 || size<1 || size>100) throw new IllegalArgumentException("유효한 page·size가 필요합니다.");
        return new PersonMergeView.History(people.historyIds(personId,owner).stream().toList(),
                audit.related(owner,PersonService.parseId(root.personId()),PersonService.parseId(personId),PageRequest.of(page,size))
                        .stream().map(this::view).toList());
    }
    private List<PersonEntry> lockPair(long source,long target,Long owner){
        return java.util.stream.LongStream.of(source,target).sorted().mapToObj(id -> rows.lockOwned(id,owner)
                .orElseThrow(() -> new NoSuchElementException("인물이 없거나 접근할 수 없습니다."))).toList();
    }
    private static PersonEntry find(List<PersonEntry> pair,long id){return pair.stream().filter(p -> p.getId()==id).findFirst().orElseThrow();}
    private void refresh(PersonEntry root,Long owner){
        root.replaceSearchText(PersonService.searchText(people.combined(root,rows.findByUserIdAndMergedIntoIdOrderByIdAsc(owner,root.getId()))));
    }
    private PersonMergeView view(PersonMergeEntry entry){
        try{return new PersonMergeView("pm_"+entry.getId(),"p_"+entry.getSourceId(),"p_"+entry.getTargetId(),
                mapper.readValue(entry.getSourceBefore(),PersonDocument.class),mapper.readValue(entry.getTargetBefore(),PersonDocument.class),
                entry.getMergedBy(),entry.getMergedAt(),entry.getUndoneBy(),entry.getUndoneAt(),entry.active());}
        catch(Exception error){throw new IllegalStateException("병합 감사 기록 형식 오류",error);}
    }
    private String write(PersonDocument document){try{return mapper.writeValueAsString(document);}catch(Exception error){throw new IllegalStateException("병합 전 인물 저장 형식 오류",error);}}
    private static void authenticated(Long owner,Long actor){if(owner==null || owner<1 || actor==null || actor<1)throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");}
    private static void confirmed(JsonNode body){if(!body.path("confirmed").isBoolean() || !body.path("confirmed").booleanValue())throw new IllegalArgumentException("같은 사람인지 직접 확인해 주세요.");}
    private static long parseMergeId(String value){
        if(value==null || !value.matches("pm_[1-9][0-9]*"))throw new IllegalArgumentException("유효한 병합 기록 ID가 필요합니다.");
        try{return Long.parseLong(value.substring(3));}catch(NumberFormatException error){throw new IllegalArgumentException("유효한 병합 기록 ID가 필요합니다.");}
    }
    private static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);}
    private static IdentitySelectionConflictException conflict(String message){return new IdentitySelectionConflictException(message);}
}
