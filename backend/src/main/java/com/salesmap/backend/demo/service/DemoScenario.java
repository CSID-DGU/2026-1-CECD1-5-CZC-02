package com.salesmap.backend.demo.service;

import com.salesmap.backend.source.entity.SourceDirection;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 데모 메일함 시나리오. 6개 고객사가 끝까지 진행하면 딜 파이프라인 5개 단계에 고루 안착하도록 짜여 있다.
 * (리드: 한국정밀·태성건설 / 미팅 예정: 그린에너지 / 일정 조율: 세진바이오 / 확정: 대성물류 / 보류·취소: 한빛소프트)
 * <p>
 * 1단계(첫 "Gmail 새로고침"): 첫 문의·미팅 요청·자료 회신·노이즈.
 * 2단계(두 번째 새로고침): 일정 변경·확정·취소와 새 문의 — 1단계에서 승인한 일정이 실제로 바뀌는 흐름.
 * <p>
 * 본문의 미팅 날짜는 "10월 6일(화)"처럼 절대 날짜로 쓴다. 메일마다 발송일이 달라 "다음 주 화요일" 같은
 * 상대 표현은 서로 다른 날로 해석될 수 있고, AI가 일정 변경 대상을 찾을 때 "10월 6일" 문자열을 단서로 쓰기 때문이다.
 */
public final class DemoScenario {

    public static final int LAST_STAGE = 2;

    private static final String[] WEEKDAY_LABELS = {"월", "화", "수", "목", "금", "토", "일"};

    private DemoScenario() {
    }

    public record DemoMail(
            int stage,
            String threadKey,
            SourceDirection direction,
            String fromName,
            String fromEmail,
            String toName,
            String toEmail,
            String subject,
            String body,
            LocalDateTime sentAt
    ) {
    }

    public record DemoSchedule(String title, LocalDateTime dateTime, String memo) {
    }

    private record Contact(String name, String email) {
    }

    private static final Contact DAESUNG = new Contact("김현우", "hyunwoo.kim@daesung-logis.example");
    private static final Contact HANBIT = new Contact("정우진", "woojin.jung@hanbitsoft.example");
    private static final Contact GREEN = new Contact("박준형", "junhyung.park@greenenergy.example");
    private static final Contact KPRECISION = new Contact("이재훈", "jaehoon.lee@kprecision.example");
    private static final Contact SEJIN = new Contact("윤가은", "gaeun.yoon@sejinbio.example");
    private static final Contact TAESUNG = new Contact("서민호", "minho.seo@taesungenc.example");

    public static List<DemoMail> mails(LocalDate anchor, String sellerName, String sellerEmail, LocalDateTime now) {
        Contact seller = new Contact(sellerName, sellerEmail);
        MeetingDates dates = MeetingDates.from(anchor);
        List<DemoMail> mails = new ArrayList<>();

        // ── 1단계 ──────────────────────────────────────────────
        received(mails, 1, "daesung-1", DAESUNG, seller, at(anchor, -10, 9, 12),
                "스마트 배차 관제 시스템 도입 검토 관련 자료 요청드립니다",
                """
                안녕하세요. 대성물류 물류기획팀 김현우 부장입니다.
                최근 저희가 전국 12개 물류센터의 배차 효율을 개선하기 위해 여러 솔루션을 비교 검토하고 있습니다.
                지난 물류산업 박람회에서 소개받은 스마트 배차 관제 시스템에 관심이 있어 연락드립니다.
                실시간 차량 위치 관제, 배차 자동 최적화, 기존 WMS 연동 가능 여부에 대한 소개 자료와,
                저희와 비슷한 규모의 도입 사례가 있으면 함께 부탁드립니다.
                자료 검토 후 내부 논의를 거쳐 미팅 여부를 결정하겠습니다.""");
        sent(mails, 1, "daesung-1", seller, DAESUNG, at(anchor, -10, 16, 40),
                "Re: 스마트 배차 관제 시스템 도입 검토 관련 자료 요청드립니다",
                """
                김현우 부장님, 안녕하세요. %s입니다.
                요청하신 스마트 배차 관제 시스템 소개서와 비슷한 규모의 물류사 도입 사례를 첨부해 드립니다.
                WMS 연동은 표준 REST API와 파일 연동 방식을 모두 지원합니다.
                검토하시다 궁금한 점이 있으면 편하게 연락 주세요.""".formatted(sellerName));

        received(mails, 1, "hanbit-1", HANBIT, seller, at(anchor, -9, 11, 30),
                "재고관리 자동화 솔루션 견적 문의드립니다",
                """
                안녕하세요. 한빛소프트 IT구매팀 정우진 팀장입니다.
                저희가 전국 50개 지점의 재고를 통합 관리할 솔루션을 검토 중입니다.
                현재는 지점별로 엑셀로 관리하다 보니 재고 불일치와 발주 지연이 자주 발생하고 있습니다.
                재고관리 자동화 솔루션의 지점 통합 관리 기능, 실시간 재고 동기화, 발주 자동화 범위와
                지점 규모 기준 대략적인 견적을 부탁드립니다.""");
        sent(mails, 1, "hanbit-1", seller, HANBIT, at(anchor, -8, 10, 5),
                "Re: 재고관리 자동화 솔루션 견적 문의드립니다",
                """
                정우진 팀장님, 안녕하세요. %s입니다.
                문의하신 재고관리 자동화 솔루션의 기능 소개서와 지점 규모 기준 견적서를 첨부해 드립니다.
                지점 통합 관리와 발주 자동화는 기본 구성에 포함되어 있습니다.
                검토 후 필요하시면 실제 화면으로 데모를 보여드리겠습니다.""".formatted(sellerName));

        noise(mails, 1, "noise-newsletter", "오늘의 IT", "newsletter@todayit.example", seller, at(anchor, -8, 7, 0),
                "[오늘의 IT] " + anchor.getYear() + " 하반기 SaaS 시장 리포트 도착",
                """
                이번 주 주요 소식: 국내 SaaS 시장이 전년 대비 24% 성장했습니다.
                인기 아티클 TOP 5와 함께 확인하세요. 수신 거부는 하단 링크에서 가능합니다.""");

        received(mails, 1, "green-1", GREEN, seller, at(anchor, -8, 9, 40),
                "ESG 탄소배출 관리 플랫폼 도입 문의드립니다",
                """
                안녕하세요. 그린에너지 신사업팀 박준형 매니저입니다.
                내년부터 강화되는 탄소배출 공시 의무에 대응하기 위해
                ESG 탄소배출 관리 플랫폼 도입을 검토하고 있습니다.
                사업장별 배출량 자동 집계와 공시 리포트 자동 생성이 핵심 요구사항입니다.
                관련 기능 소개 자료와 공공기관/제조사 도입 사례를 부탁드립니다.""");
        sent(mails, 1, "green-1", seller, GREEN, at(anchor, -8, 17, 5),
                "Re: ESG 탄소배출 관리 플랫폼 도입 문의드립니다",
                """
                박준형 매니저님, 안녕하세요. %s입니다.
                ESG 탄소배출 관리 플랫폼 소개 자료와 제조사 도입 사례를 첨부해 드립니다.
                사업장별 배출량 집계와 공시 리포트 생성 기능을 중심으로 정리했습니다.
                살펴보시고 편하실 때 의견 주세요.""".formatted(sellerName));

        received(mails, 1, "kprecision-1", KPRECISION, seller, at(anchor, -7, 13, 15),
                "스마트팩토리 MES 도입 관련 문의",
                """
                안녕하세요. 한국정밀 생산기술팀 이재훈 책임입니다.
                저희가 노후화된 생산관리 방식을 개선하기 위해 스마트팩토리 MES 도입을 초기 검토하고 있습니다.
                설비 데이터 실시간 수집, 생산 실적 자동 집계, 불량 원인 추적 기능이 있는지 궁금하고,
                기존 ERP와의 연동이 가능한지도 확인하고 싶습니다.
                아직 내부적으로 예산이나 일정이 확정된 단계는 아니라, 우선 자료부터 검토하려 합니다.""");
        sent(mails, 1, "kprecision-1", seller, KPRECISION, at(anchor, -6, 9, 30),
                "Re: 스마트팩토리 MES 도입 관련 문의",
                """
                이재훈 책임님, 안녕하세요. %s입니다.
                스마트팩토리 MES 기능 소개서와 ERP 연동 구성도를 첨부해 드립니다.
                설비 데이터 수집과 불량 원인 추적 기능은 모두 기본 제공됩니다.
                검토 후 궁금한 점을 알려주시면 자세히 안내드리겠습니다.""".formatted(sellerName));

        received(mails, 1, "daesung-2", DAESUNG, seller, at(anchor, -6, 10, 5),
                "스마트 배차 관제 시스템 도입 상담 미팅 요청드립니다",
                """
                안녕하세요, 대성물류 김현우입니다.
                보내주신 소개 자료와 도입 사례 잘 검토했습니다. 물류기획팀과 IT팀 모두 반응이 좋아,
                구체적인 도입 상담을 진행하고 싶습니다.
                %s 오전 10시에 저희 본사에서 미팅 가능하실까요?
                저희 쪽 참석자는 저와 IT팀 최지훈 대리 두 명입니다.
                미팅 때는 기존 WMS 연동 방식과 도입 일정, 대략적인 비용 범위를 중심으로 논의하고 싶습니다."""
                        .formatted(day(dates.tuesday, anchor)));

        noise(mails, 1, "noise-delivery", "바로택배", "noreply@barodelivery.example", seller, at(anchor, -6, 14, 2),
                "[바로택배] 주문하신 상품이 배송 완료되었습니다",
                """
                운송장번호 1234-5678 상품이 오늘 오후 2시 문 앞에 안전하게 배송 완료되었습니다.
                이용해 주셔서 감사합니다.""");

        received(mails, 1, "sejin-1", SEJIN, seller, at(anchor, -5, 10, 20),
                "QMS 품질관리 시스템 온라인 데모 요청드립니다",
                """
                안녕하세요. 세진바이오 품질보증팀 윤가은 과장입니다.
                GMP 문서와 일탈·CAPA 이력을 아직 수기로 관리하고 있어 QMS 품질관리 시스템 도입을 검토 중입니다.
                %s 오후 3시에 온라인으로 데모 미팅 가능하실까요?
                저희 쪽 참석자는 저와 QA팀 오세훈 대리 두 명입니다.
                전자서명 지원 여부와 기존 LIMS 연동 범위를 중점적으로 보고 싶습니다."""
                        .formatted(day(dates.monday, anchor)));

        received(mails, 1, "daesung-3", DAESUNG, seller, at(anchor, -5, 14, 20),
                "미팅 전 확인 부탁드릴 사항 정리해 드립니다",
                """
                안녕하세요, 김현우입니다.
                잡아주신 미팅 전에 저희 쪽에서 미리 확인하고 싶은 항목을 정리해 공유드립니다.
                (1) 실시간 관제 대시보드가 관리자용/기사용으로 분리 제공되는지
                (2) 자체 개발한 기존 WMS와의 API 연동 난이도
                (3) 도입 후 안정화까지 예상 기간
                (4) 기사 앱의 오프라인 환경 대응 여부
                미팅 때 이 부분 위주로 설명해주시면 논의가 수월할 것 같습니다.""");

        noise(mails, 1, "noise-payment", "페이온", "noreply@payon.example", seller, at(anchor, -4, 12, 31),
                "[페이온] 결제 완료 안내",
                """
                카페 아메리카노 4,500원 결제가 완료되었습니다.
                잔액 및 상세 내역은 앱에서 확인하실 수 있습니다.""");

        received(mails, 1, "hanbit-2", HANBIT, seller, at(anchor, -4, 15, 10),
                "재고관리 솔루션 데모 미팅 요청 (예산 협의 포함)",
                """
                안녕하세요, 정우진입니다.
                보내주신 견적과 기능 범위 검토했고, 내부 논의 결과 도입을 긍정적으로 보고 있습니다.
                실제 화면을 보고 결정하고 싶어 데모 미팅을 요청드립니다.
                %s 오후 3시 가능하실까요? 저희 쪽은 저와 재무팀 이서연 과장이 참석합니다.
                참고로 이번 건 예상 예산은 3,000만원 정도로 잡고 있으니,
                해당 범위에서 가능한 구성으로 제안 부탁드립니다."""
                        .formatted(day(dates.thursday, anchor)));

        noise(mails, 1, "noise-career", "커리어링크", "notifications@careerlink.example", seller, at(anchor, -3, 8, 0),
                "[커리어링크] 회원님의 프로필이 이번 주 12회 조회되었습니다",
                """
                지난 주보다 조회수가 늘었습니다. 누가 프로필을 확인했는지 지금 살펴보세요.""");

        received(mails, 1, "kprecision-2", KPRECISION, seller, at(anchor, -3, 16, 45),
                "MES 관련 추가로 궁금한 점 문의드립니다",
                """
                안녕하세요, 이재훈입니다.
                보내주신 자료 잘 받았습니다. 내부 공유 전에 몇 가지 더 확인하고 싶습니다.
                (1) 설비 제조사가 제각각인데 데이터 수집 어댑터를 별도 개발해야 하는지
                (2) 클라우드 방식과 온프레미스 방식 중 선택 가능한지
                (3) 파일럿(1개 라인)부터 단계적으로 도입한 사례가 있는지
                검토 후에 미팅 일정은 다시 잡아 연락드리겠습니다.""");

        received(mails, 1, "green-2", GREEN, seller, at(anchor, -2, 11, 0),
                "ESG 플랫폼 도입 상담 미팅 요청드립니다",
                """
                안녕하세요, 박준형입니다.
                보내주신 자료 잘 봤습니다. 내부 검토 결과 상담을 진행하기로 했습니다.
                %s 오전 11시에 상담 미팅 가능하실까요?
                저희 쪽 참석자는 저와 환경안전팀 한유진 책임 두 명입니다.
                저희 사업장이 총 7곳인데, 사업장별 데이터 연동 방식과 공시 리포트 양식
                커스터마이징 가능 여부를 중점적으로 보고 싶습니다."""
                        .formatted(day(dates.friday, anchor)));

        noise(mails, 1, "noise-webinar", "AI 인사이트", "marketing@aiinsight.example", seller, at(anchor, -1, 10, 0),
                "[무료 웨비나] 생성형 AI 업무 자동화 실전 — 온라인 개최",
                """
                생성형 AI로 업무를 자동화하는 실전 노하우를 소개합니다.
                사전등록하면 발표자료를 드립니다. 수신 거부는 하단 링크에서 가능합니다.""");

        // ── 2단계 ──────────────────────────────────────────────
        received(mails, 2, "hanbit-3", HANBIT, seller, now.minusMinutes(170),
                "데모 미팅 취소 요청드립니다",
                """
                안녕하세요, 정우진입니다.
                죄송한 말씀 먼저 드립니다. 내부 사정으로 이번 분기 신규 솔루션 도입 예산 집행이 보류되면서,
                %s로 예정했던 데모 미팅을 부득이 취소해야 할 것 같습니다.
                다음 분기에 예산이 다시 편성되면 그때 재검토하여 연락드리겠습니다.
                그동안 신경 써주셔서 감사합니다."""
                        .formatted(day(dates.thursday, now.toLocalDate())));

        received(mails, 2, "daesung-4", DAESUNG, seller, now.minusMinutes(140),
                "미팅 시간 조정 가능할까요",
                """
                안녕하세요, 김현우입니다.
                지난번에 잡았던 %s 오전 미팅을 부득이하게 시간 조정해야 할 것 같습니다.
                그날 오전에 갑자기 임원 보고가 잡혀서요. 같은 날 오후 2시로 변경 가능할까요?
                참석자는 동일합니다. 번거롭게 해드려 죄송합니다."""
                        .formatted(day(dates.tuesday, now.toLocalDate())));

        received(mails, 2, "sejin-2", SEJIN, seller, now.minusMinutes(100),
                "데모 미팅 일정 변경 요청드립니다",
                """
                안녕하세요, 윤가은입니다.
                %s 오후에 잡았던 온라인 데모가 내부 감사 일정과 겹쳐 조정이 필요합니다.
                일주일 뒤인 %s 오후 4시로 변경 가능할까요?
                참석자는 동일합니다. 번거롭게 해드려 죄송합니다."""
                        .formatted(day(dates.monday, now.toLocalDate()), day(dates.mondayNextWeek, now.toLocalDate())));

        noise(mails, 2, "noise-video", "튜브채널", "noreply@tubechannel.example", seller, now.minusMinutes(75),
                "[튜브채널] '개발하는 정대리'님이 새 동영상을 업로드했습니다",
                """
                구독하신 채널에 '주니어 개발자가 알아야 할 Git 10가지'가 올라왔습니다. 지금 시청하기.
                알림 설정은 언제든 변경할 수 있습니다.""");

        received(mails, 2, "daesung-5", DAESUNG, seller, now.minusMinutes(45),
                "변경된 일정으로 확정합니다",
                """
                안녕하세요, 김현우입니다.
                변경된 시간 확인했습니다. 도입 상담 미팅은 %s 오후 2시로 확정하겠습니다.
                참석자는 저와 최지훈 대리 그대로이고, 회의실은 본사 3층 대회의실로 준비하겠습니다.
                당일 뵙겠습니다. 감사합니다."""
                        .formatted(day(dates.tuesday, now.toLocalDate())));

        received(mails, 2, "taesung-1", TAESUNG, seller, now.minusMinutes(15),
                "건설현장 안전관리 플랫폼 도입 문의드립니다",
                """
                안녕하세요. 태성건설 안전보건팀 서민호 차장입니다.
                전국 현장의 안전 점검과 위험성 평가를 디지털로 관리할 방법을 찾고 있습니다.
                건설현장 안전관리 플랫폼의 모바일 점검 체크리스트, 작업 전 안전회의 기록,
                협력사 안전서류 관리 기능에 대한 소개 자료와 건설사 도입 사례를 부탁드립니다.
                자료를 먼저 검토한 뒤 미팅 여부를 말씀드리겠습니다.""");

        return mails;
    }

    /** 메일과 무관하게 캘린더에 미리 들어 있는 일정. 제목·날짜가 시나리오 미팅과 겹치지 않게 골랐다. */
    public static List<DemoSchedule> schedules(LocalDate anchor) {
        MeetingDates dates = MeetingDates.from(anchor);
        LocalDate firstBusinessDay = nextBusinessDay(anchor);
        LocalDate secondBusinessDay = nextBusinessDay(firstBusinessDay);

        return List.of(
                new DemoSchedule("동우전자 물류창고 현장 방문",
                        previousBusinessDay(anchor).atTime(15, 0),
                        "창고 자동화 요구사항 청취. 방문 결과를 기록해 주세요."),
                new DemoSchedule("주간 영업 파이프라인 회의",
                        firstBusinessDay.atTime(9, 30),
                        "이번 주 딜 진행 상황과 신규 리드 공유"),
                new DemoSchedule("파트너사 기술 세미나 참석",
                        secondBusinessDay.atTime(14, 0),
                        "신규 연동 API 발표 청취"),
                new DemoSchedule("월말 영업 실적 보고",
                        dates.friday.plusWeeks(1).atTime(17, 0),
                        "고객사별 딜 단계와 예상 매출 정리")
        );
    }

    private record MeetingDates(
            LocalDate monday,
            LocalDate tuesday,
            LocalDate thursday,
            LocalDate friday,
            LocalDate mondayNextWeek
    ) {
        // 기준일 5일 뒤부터 7일 창 안에서 요일별로 하나씩 고른다 → 날짜가 서로 겹치지 않고, 미리 넣는 일정(기준일+4일 이내)과도 떨어진다.
        static MeetingDates from(LocalDate anchor) {
            LocalDate windowStart = anchor.plusDays(5);
            LocalDate monday = onOrAfter(windowStart, DayOfWeek.MONDAY);
            return new MeetingDates(
                    monday,
                    onOrAfter(windowStart, DayOfWeek.TUESDAY),
                    onOrAfter(windowStart, DayOfWeek.THURSDAY),
                    onOrAfter(windowStart, DayOfWeek.FRIDAY),
                    monday.plusWeeks(1)
            );
        }
    }

    private static void received(List<DemoMail> mails, int stage, String threadKey, Contact from, Contact to,
                                 LocalDateTime sentAt, String subject, String body) {
        mails.add(new DemoMail(stage, threadKey, SourceDirection.RECEIVED, from.name(), from.email(),
                to.name(), to.email(), subject, body, sentAt));
    }

    private static void sent(List<DemoMail> mails, int stage, String threadKey, Contact from, Contact to,
                             LocalDateTime sentAt, String subject, String body) {
        mails.add(new DemoMail(stage, threadKey, SourceDirection.SENT, from.name(), from.email(),
                to.name(), to.email(), subject, body, sentAt));
    }

    private static void noise(List<DemoMail> mails, int stage, String threadKey, String fromName, String fromEmail,
                              Contact to, LocalDateTime sentAt, String subject, String body) {
        mails.add(new DemoMail(stage, threadKey, SourceDirection.RECEIVED, fromName, fromEmail,
                to.name(), to.email(), subject, body, sentAt));
    }

    private static LocalDateTime at(LocalDate anchor, int dayOffset, int hour, int minute) {
        return anchor.plusDays(dayOffset).atTime(LocalTime.of(hour, minute));
    }

    // AI 모듈의 일정 매칭이 "10월 6일" 형태(공백 포함)를 찾으므로 그대로 맞춘다. 해가 바뀌면 연도를 붙인다.
    private static String day(LocalDate date, LocalDate writtenOn) {
        String label = date.getMonthValue() + "월 " + date.getDayOfMonth() + "일("
                + WEEKDAY_LABELS[date.getDayOfWeek().getValue() - 1] + ")";
        return date.getYear() == writtenOn.getYear() ? label : date.getYear() + "년 " + label;
    }

    private static LocalDate onOrAfter(LocalDate date, DayOfWeek dayOfWeek) {
        return date.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }

    private static LocalDate nextBusinessDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }

    private static LocalDate previousBusinessDay(LocalDate date) {
        LocalDate previous = date.minusDays(1);
        while (previous.getDayOfWeek() == DayOfWeek.SATURDAY || previous.getDayOfWeek() == DayOfWeek.SUNDAY) {
            previous = previous.minusDays(1);
        }
        return previous;
    }
}
