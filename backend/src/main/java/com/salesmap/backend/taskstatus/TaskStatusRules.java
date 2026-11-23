package com.salesmap.backend.taskstatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 할 일 내용과 메일을 글자로 비교하는 규칙. LLM을 쓰지 않고(규칙 4), 결과는 제안일 뿐이다. */
final class TaskStatusRules {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\p{N}");
    private static final Pattern QUOTE_START = Pattern.compile(
            "(?m)^(-{2,}\\s*(Original Message|원본 메시지).*|보낸 사람\\s*:.*|From\\s*:.*|On .+ wrote:\\s*)$");
    // 날짜·시기와 어떤 할 일에나 나오는 동작 말은 같은 일이라는 근거가 되지 않는다
    private static final Set<String> IGNORED = Set.of(
            "이번", "다음", "지난", "오늘", "내일", "모레", "금주", "차주", "오전", "오후",
            "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일",
            "송부", "전달", "발송", "보내기", "회신", "확인", "여부", "이행", "제출", "준비",
            "공유", "요청", "진행", "완료", "고객", "내부");

    private TaskStatusRules() {
    }

    /** 할 일 내용에서 비교할 낱말. 숫자가 든 날짜·수량과 고객 측 표시는 뺀다. */
    static List<String> keywords(String content) {
        if (content == null) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        for (String raw : TOKEN_SPLIT.split(content.toLowerCase(Locale.ROOT))) {
            String token = raw.replaceFirst("(까지|부터)$", "");
            if (token.length() >= 2 && !HAS_DIGIT.matcher(token).find() && !IGNORED.contains(token)) {
                found.add(token);
            }
        }
        return List.copyOf(found);
    }

    /** 메일에 나온 낱말. 두 개 이상(낱말이 하나뿐이면 그 하나)이 있어야 같은 일로 본다. 아니면 빈 목록. */
    static List<String> matched(List<String> keywords, String title, String content) {
        if (keywords.isEmpty()) {
            return List.of();
        }
        String text = ((title == null ? "" : title) + "\n" + withoutQuote(content)).toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                hits.add(keyword);
            }
        }
        return hits.size() >= Math.min(2, keywords.size()) ? List.copyOf(hits) : List.of();
    }

    /** 답장에 딸려 온 이전 메일은 이번에 한 일의 근거가 아니다. */
    static String withoutQuote(String content) {
        if (content == null) {
            return "";
        }
        var quote = QUOTE_START.matcher(content);
        String body = quote.find() ? content.substring(0, quote.start()) : content;
        StringBuilder kept = new StringBuilder();
        for (String line : body.split("\\R")) {
            if (!line.stripLeading().startsWith(">")) {
                kept.append(line).append('\n');
            }
        }
        return kept.toString();
    }

    static boolean containsEmail(String joinedEmails, Set<String> emails) {
        if (joinedEmails == null) {
            return false;
        }
        for (String value : joinedEmails.split("[,;\\s]+")) {
            String email = value.replaceAll("[<>]", "").trim().toLowerCase(Locale.ROOT);
            if (emails.contains(email)) {
                return true;
            }
        }
        return false;
    }
}
