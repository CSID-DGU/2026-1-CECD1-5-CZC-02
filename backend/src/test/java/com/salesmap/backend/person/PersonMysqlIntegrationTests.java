package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.analysis.repository.AnalysisRevisionRepository;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.minutes.client.RevisionServiceClient;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteMode;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.revision.service.RevisionService;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** 실제 MySQL 선택 검증. 일반 CI의 *Test 패턴에서 빠지고 명시적 opt-in에서만 실행한다. */
@EnabledIfEnvironmentVariable(named="A_PERSON_MYSQL_TEST",matches="true")
@SpringBootTest(classes=PersonMysqlIntegrationTests.MysqlConfig.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties={"spring.jpa.show-sql=false","spring.jpa.open-in-view=false","spring.sql.init.mode=never",
                "spring.jpa.hibernate.ddl-auto=create-only","spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
                "spring.datasource.hikari.maximum-pool-size=6","logging.level.root=WARN"})
@ContextConfiguration(initializers=PersonMysqlIntegrationTests.FreshDatabaseGuard.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PersonMysqlIntegrationTests {
    private static final Pattern URL=Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/(a_identity_verify_([0-9a-f]{16}))(?:\\?serverTimezone=Asia/Seoul&characterEncoding=UTF-8)?");

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class})
    @EntityScan("com.salesmap.backend")
    @EnableJpaRepositories(basePackageClasses={PersonRepository.class,UserRepository.class,SourceRepository.class,
            MeetingNoteRepository.class,AnalysisRevisionRepository.class})
    @EnableJpaAuditing
    @Import({PersonService.class,PersonIdentityService.class,RevisionServiceClient.class,RevisionService.class})
    static class MysqlConfig {}

    static String env(String key){
        String value=System.getenv(key);
        if(value==null || value.isBlank())throw new IllegalStateException("검증용 환경변수가 필요합니다: "+key);
        return value;
    }
    static String schema(){
        var match=URL.matcher(env("A_PERSON_MYSQL_URL"));
        if(!match.matches() || !env("A_PERSON_MYSQL_USER").equals("a_verify_"+match.group(2)))
            throw new IllegalStateException("로컬 전용 임시 DB·계정만 허용합니다.");
        return match.group(1);
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
        schema();
        properties.add("spring.datasource.url",() -> env("A_PERSON_MYSQL_URL"));
        properties.add("spring.datasource.username",() -> env("A_PERSON_MYSQL_USER"));
        properties.add("spring.datasource.password",() -> env("A_PERSON_MYSQL_PASSWORD"));
    }
    /** JPA의 스키마 생성 전에 빈 전용 DB인지 검사한다. 기존 데이터가 있으면 시작하지 않는다. */
    public static class FreshDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext>{
        public void initialize(ConfigurableApplicationContext context){
            String expected=schema();
            try{
                Class.forName("com.mysql.cj.jdbc.Driver");
                try(var connection=DriverManager.getConnection(env("A_PERSON_MYSQL_URL"),env("A_PERSON_MYSQL_USER"),env("A_PERSON_MYSQL_PASSWORD"));
                    var statement=connection.createStatement()){
                    try(var result=statement.executeQuery("select database()")){
                        if(!result.next() || !expected.equals(result.getString(1)))throw new IllegalStateException("임시 DB 이름이 다릅니다.");
                    }
                    try(var result=statement.executeQuery("select count(*) from information_schema.tables where table_schema=database()")){
                        if(!result.next() || result.getLong(1)!=0)throw new IllegalStateException("기존 테이블이 있는 DB에는 검증을 실행하지 않습니다.");
                    }
                }
            }catch(Exception error){throw new IllegalStateException("빈 전용 MySQL DB 사전 검사가 실패했습니다.",error);}
        }
    }

    @Autowired PersonService people;
    @Autowired PersonIdentityService identities;
    @Autowired RevisionService revisions;
    @Autowired UserRepository users;
    @Autowired SourceRepository sources;
    @Autowired MeetingNoteRepository notes;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;
    Fixture fixture;
    record Fixture(Long owner,Long other,String source,String note){}
    record Seed(String resolutionId,String etag,String personId){}

    @BeforeEach void fixture(){
        assertThat(jdbc.queryForObject("select database()",String.class)).isEqualTo(schema());
        fixture=new TransactionTemplate(transactions).execute(status -> {
            String key=UUID.randomUUID().toString().replace("-","");
            User owner=users.saveAndFlush(new User("synthetic_"+key+"@people.example","가상사용자_001","unused-test-hash","USER",UserStatus.ACTIVE));
            User other=users.saveAndFlush(new User("synthetic_other_"+key+"@people.example","가상사용자_002","unused-test-hash","USER",UserStatus.ACTIVE));
            Source source=sources.saveAndFlush(new Source(owner,null,SourceType.MEETING_NOTE,null,"가상 미팅 검증",
                    "가상인물_0001과 가상회사_001 미팅",SourceStatus.CREATED,LocalDateTime.now()));
            MeetingNote note=notes.saveAndFlush(new MeetingNote(owner,source,null,null,MeetingNoteMode.FULL,
                    "[{\"displayName\":\"가상인물_0001\",\"resolutionId\":null,\"personId\":null}]",
                    "{\"discussion\":[],\"customerRequests\":[],\"concerns\":[],\"commitments\":[],\"nextContact\":[]}",
                    null,"[]","[]","RULE","rule",null,null));
            return new Fixture(owner.getId(),other.getId(),"src_"+source.getId(),"mn_"+note.getId());
        });
    }
    ObjectNode person(String name){
        ObjectNode node=mapper.createObjectNode().put("name",name).put("company","가상회사_001").put("isSynthetic",true);
        node.putArray("nameVariants").add("Synthetic Alias");node.putArray("emails").add("shared@people.example");
        node.putArray("phones").add("000-0000-0001");
        node.putArray("companyHistory").addObject().put("company","가상회사_이전").putNull("title").put("until","2025-12-31");
        return node;
    }
    ObjectNode proposal(String id,String target){
        ObjectNode result=mapper.createObjectNode().put("resolutionId",id).put("decision","CANDIDATES")
                .putNull("personId").put("decidedBy","RULE").put("modelVersion","identity-rule-0.2-d022");
        result.putObject("mention").put("name","가상인물_0001").put("channel","MEETING").put("sourceRef",fixture.source());
        result.putArray("candidates").addObject().put("personId",target).putNull("score").putArray("reasons").add("이름 일치 — 확인 필요");
        return result;
    }
    Seed seed(){
        PersonDocument person=people.create(person("가상인물_0001"),fixture.owner(),fixture.owner());
        String id="ir_mysql_"+UUID.randomUUID().toString().replace("-","");
        identities.storeProposals(fixture.source(),List.of(proposal(id,person.personId())),fixture.owner());
        return new Seed(id,identities.list(fixture.note(),fixture.owner()).get(0).etag(),person.personId());
    }
    ObjectNode choose(String id){return mapper.createObjectNode().put("personId",id);}
    ObjectNode createBody(){ObjectNode body=mapper.createObjectNode();body.set("newPerson",person("가상인물_신규"));return body;}
    IdentityReviewItem select(Seed seed,JsonNode body){
        return identities.select(fixture.note(),seed.resolutionId(),body,seed.etag(),fixture.owner(),fixture.owner());
    }
    long personCount(){return jdbc.queryForObject("select count(*) from person_entries where user_id=?",Long.class,fixture.owner());}
    long revisionCount(){return jdbc.queryForObject("select count(*) from analysis_revisions where owner_user_id=?",Long.class,fixture.owner());}
    JsonNode stored(Seed seed){try{return mapper.readTree(jdbc.queryForObject("select document from person_identity_resolutions where resolution_id=?",String.class,seed.resolutionId()));}
        catch(Exception error){throw new IllegalStateException(error);}}
    void unchanged(Seed seed,long count){
        assertThat(personCount()).isEqualTo(count);assertThat(revisionCount()).isZero();
        assertThat(stored(seed).path("decidedBy").asText()).isEqualTo("RULE");
        assertThat(identities.list(fixture.note(),fixture.owner()).get(0).etag()).isEqualTo(seed.etag());
    }

    @Test void registrationSearchAndOwnerIsolationUseActualMysql() throws Exception {
        PersonDocument first=people.create(person("가상인물_0001"),fixture.owner(),fixture.owner());
        people.create(person("가상인물_0001"),fixture.owner(),fixture.owner());
        PersonDocument foreign=people.create(person("가상인물_비공개"),fixture.other(),fixture.other());
        PersonDocument special=people.create(person("가상인물_%_!"),fixture.owner(),fixture.owner());
        assertThat(first.personId()).matches("p_[1-9][0-9]*");
        assertThat(people.get(first.personId(),fixture.owner())).isEqualTo(first);
        assertThatThrownBy(() -> people.get(foreign.personId(),fixture.owner())).isInstanceOf(NoSuchElementException.class);
        assertThat(people.list("가상인물_0001",0,50,fixture.owner())).hasSize(2);
        assertThat(people.list("SYNTHETIC ALIAS",0,50,fixture.owner())).hasSize(3);
        assertThat(people.list("가상회사_이전",0,50,fixture.owner())).hasSize(3);
        assertThat(people.list("%_!",0,50,fixture.owner())).extracting(PersonDocument::personId).containsExactly(special.personId());
        assertThat(people.list(null,0,2,fixture.owner())).hasSize(2);
        assertThat(people.list(null,1,2,fixture.owner())).hasSize(1);
        assertThat(people.forResolution(fixture.owner())).hasSize(3);
        assertThat(ContractSchemaValidator.load("person.schema.json").validate(mapper.valueToTree(first))).isEmpty();
    }

    @Test void selectionCommitsHumanAndRealDRevisionAndSurvivesFreshReads() throws Exception {
        Seed seed=seed();
        String participants=jdbc.queryForObject("select participants_json from meeting_notes where id=?",String.class,Long.parseLong(fixture.note().substring(3)));
        LocalDateTime before=LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusSeconds(1);
        IdentityReviewItem saved=select(seed,choose(seed.personId()));
        assertThat(saved.etag()).isEqualTo("\"irv-1\"");
        assertThat(stored(seed).path("personId").asText()).isEqualTo(seed.personId());
        assertThat(stored(seed).path("decidedBy").asText()).isEqualTo("HUMAN");
        assertThat(identities.list(fixture.note(),fixture.owner()).get(0)).isEqualTo(saved);
        var records=revisions.list(fixture.owner());assertThat(records).hasSize(1);
        var revision=records.get(0);
        assertThat(revision.action()).isEqualTo("SELECTED_CANDIDATE");assertThat(revision.finalValue().asText()).isEqualTo(seed.personId());
        assertThat(revision.proposedBy()).isEqualTo("RULE");assertThat(revision.actorId()).isEqualTo("user_"+fixture.owner());
        assertThat(revision.decidedAt()).isBetween(before,LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusSeconds(1));
        assertThat(ContractSchemaValidator.load("identity_resolution.schema.json").validate(saved.resolution())).isEmpty();
        assertThat(ContractSchemaValidator.load("revision.schema.json").validate(mapper.valueToTree(revision))).isEmpty();
        assertThat(jdbc.queryForObject("select participants_json from meeting_notes where id=?",String.class,Long.parseLong(fixture.note().substring(3)))).isEqualTo(participants);
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema=database() and table_name='analysis_revisions' and column_name='analysis_id'",String.class)).isEqualTo("YES");
    }

    @Test void registrationReselectionAndStaleRetriesAreConsistentInMysql(){
        Seed seed=seed();
        IdentityReviewItem created=select(seed,createBody());
        assertThat(personCount()).isEqualTo(2);assertThat(revisions.list(fixture.owner()).get(0).action()).isEqualTo("CREATED_NEW");
        assertThat(people.get(created.resolution().path("personId").asText(),fixture.owner()).name()).isEqualTo("가상인물_신규");
        IdentityReviewItem changed=identities.select(fixture.note(),seed.resolutionId(),choose(seed.personId()),created.etag(),fixture.owner(),fixture.owner());
        assertThat(changed.etag()).isEqualTo("\"irv-2\"");assertThat(revisionCount()).isEqualTo(2);
        assertThat(revisions.list(fixture.owner()).get(1).action()).isEqualTo("EDITED");
        identities.select(fixture.note(),seed.resolutionId(),choose(seed.personId()),changed.etag(),fixture.owner(),fixture.owner());
        assertThat(revisionCount()).isEqualTo(2);
        assertThatThrownBy(() -> select(seed,createBody())).isInstanceOf(IdentitySelectionConflictException.class);
        assertThat(personCount()).isEqualTo(2);assertThat(revisionCount()).isEqualTo(2);
    }

    @Test void originalAndHumanDecisionAreNotOverwrittenByAiReplay(){
        Seed seed=seed();select(seed,choose(seed.personId()));
        String original=jdbc.queryForObject("select proposal from person_identity_resolutions where resolution_id=?",String.class,seed.resolutionId());
        var replay=identities.storeProposals(fixture.source(),List.of(proposal("ir_replayed",seed.personId())),fixture.owner());
        assertThat(replay.get(0).path("resolutionId").asText()).isEqualTo(seed.resolutionId());
        assertThat(replay.get(0).path("decidedBy").asText()).isEqualTo("HUMAN");
        assertThat(jdbc.queryForObject("select proposal from person_identity_resolutions where resolution_id=?",String.class,seed.resolutionId())).isEqualTo(original);
        assertThat(jdbc.queryForObject("select count(*) from person_identity_resolutions where user_id=?",Long.class,fixture.owner())).isEqualTo(1);
    }

    @Test void authorizationAndReadonlyStatesBlockRealWrites(){
        Seed seed=seed();PersonDocument foreign=people.create(person("가상다른사용자인물"),fixture.other(),fixture.other());
        assertThatThrownBy(() -> identities.list(fixture.note(),fixture.other())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> select(seed,choose(foreign.personId()))).isInstanceOf(NoSuchElementException.class);
        unchanged(seed,1);
        Long notePk=Long.parseLong(fixture.note().substring(3));
        jdbc.update("update meeting_notes set status='CONFIRMED' where id=?",notePk);
        assertThatThrownBy(() -> select(seed,choose(seed.personId()))).isInstanceOf(IdentitySelectionConflictException.class);
        jdbc.update("update meeting_notes set status='DRAFT', archived_at=current_timestamp where id=?",notePk);
        assertThatThrownBy(() -> select(seed,choose(seed.personId()))).isInstanceOf(IdentitySelectionConflictException.class);
        unchanged(seed,1);
    }

    @Test void dRevisionInsertFailureRollsBackNewPersonAndSelection(){
        Seed seed=seed();String constraint="a_verify_revision_"+fixture.owner();
        jdbc.execute("alter table analysis_revisions add constraint "+constraint+" check(owner_user_id is null or owner_user_id<>"+fixture.owner()+")");
        try{
            assertThatThrownBy(() -> select(seed,createBody())).isInstanceOf(RuntimeException.class);
            unchanged(seed,1);
        }finally{jdbc.execute("alter table analysis_revisions drop check "+constraint);}
    }

    @Test void aSelectionUpdateFailureAlsoRollsBackAlreadyInsertedDRevisionAndNewPerson(){
        Seed seed=seed();String constraint="a_verify_document_"+fixture.owner();
        jdbc.execute("alter table person_identity_resolutions add constraint "+constraint+
                " check(resolution_id<>'"+seed.resolutionId()+"' or json_unquote(json_extract(document,'$.decidedBy'))<>'HUMAN')");
        try{
            assertThatThrownBy(() -> select(seed,createBody())).isInstanceOf(RuntimeException.class);
            unchanged(seed,1);
        }finally{jdbc.execute("alter table person_identity_resolutions drop check "+constraint);}
    }

    @Test void concurrentSelectionsCommitExactlyOneChoiceAndOneRevision() throws Exception {
        Seed seed=seed();String second=people.create(person("가상인물_0002"),fixture.owner(),fixture.owner()).personId();
        var pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try{
            var futures=List.of(seed.personId(),second).stream().map(id -> pool.submit(() -> {
                ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("동시 검증 시작 시간 초과");
                try{select(seed,choose(id));return "saved";}catch(IdentitySelectionConflictException expected){return "conflict";}
            })).toList();
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved","conflict");
            assertThat(revisionCount()).isEqualTo(1);
            assertThat(stored(seed).path("personId").asText()).isIn(Set.of(seed.personId(),second));
            assertThat(identities.list(fixture.note(),fixture.owner()).get(0).etag()).isEqualTo("\"irv-1\"");
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
}
