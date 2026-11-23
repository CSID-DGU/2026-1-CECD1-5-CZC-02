package com.salesmap.backend.schedule.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 할 일에서 만든 일정의 중복 등록을 막는 내부 표식. DB 메모에만 두고 사용자·Google에는 보이지 않게 한다. */
public final class ScheduleMemoMarker {
    private static final Pattern TASK_MARKER = Pattern.compile("(?m)^(\\[SALESMAP_TASK:[^\\]]+\\])\\s*\\n?");

    private ScheduleMemoMarker() {
    }

    public static String strip(String memo) {
        if (memo == null) return null;
        return TASK_MARKER.matcher(memo).replaceFirst("").trim();
    }

    /** 사람이 고친 메모에 기존 표식을 맨 앞에 다시 붙인다. 뒤에서 길이를 잘라도 표식은 남는다. */
    public static String preserve(String previousMemo, String nextMemo) {
        if (previousMemo == null || nextMemo == null) return nextMemo;
        Matcher matcher = TASK_MARKER.matcher(previousMemo);
        if (!matcher.find()) return nextMemo;
        String body = strip(nextMemo);
        return body.isEmpty() ? matcher.group(1) : matcher.group(1) + "\n" + body;
    }
}
