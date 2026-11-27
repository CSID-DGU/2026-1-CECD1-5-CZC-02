package com.salesmap.backend.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 인증 본문·검증 실패의 rejectedValue를 Spring 웹 진단 로그에 남기지 않는다. */
public final class SensitiveAuthLogFilter extends TurboFilter {
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format,
                              Object[] params, Throwable throwable) {
        if (!logger.getName().startsWith("org.springframework.web.")) {
            return FilterReply.NEUTRAL;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return FilterReply.NEUTRAL;
        }
        var request = attributes.getRequest();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/auth") || path.startsWith("/api/auth/")) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
