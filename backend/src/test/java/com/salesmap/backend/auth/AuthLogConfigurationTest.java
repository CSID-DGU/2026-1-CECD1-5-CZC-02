package com.salesmap.backend.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.salesmap.backend.global.logging.SensitiveAuthLogFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class AuthLogConfigurationTest {
    @Test
    void defaultLogConfigurationInstallsProtectionAndOnlyAuthDiagnosticsAreDenied() throws Exception {
        var context = new LoggerContext();
        var previous = RequestContextHolder.getRequestAttributes();
        try {
            var configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
            assertThat(context.getTurboFilterList()).hasSize(1);
            var filter = context.getTurboFilterList().get(0);
            assertThat(filter).isInstanceOf(SensitiveAuthLogFilter.class);
            assertThat(filter.isStarted()).isTrue();
            assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE")).isNotNull();

            var request = new MockHttpServletRequest("POST", "/context/api/auth/login");
            request.setContextPath("/context");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            var web = context.getLogger("org.springframework.web.servlet.DispatcherServlet");
            assertThat(filter.decide(null, web, ch.qos.logback.classic.Level.TRACE, "fake", null, null))
                    .isEqualTo(ch.qos.logback.core.spi.FilterReply.DENY);
            request.setRequestURI("/context/api/persons");
            assertThat(filter.decide(null, web, ch.qos.logback.classic.Level.TRACE, "fake", null, null))
                    .isEqualTo(ch.qos.logback.core.spi.FilterReply.NEUTRAL);
            request.setRequestURI("/context/api/authorization");
            assertThat(filter.decide(null, web, ch.qos.logback.classic.Level.TRACE, "fake", null, null))
                    .isEqualTo(ch.qos.logback.core.spi.FilterReply.NEUTRAL);
        } finally {
            RequestContextHolder.setRequestAttributes(previous);
            context.stop();
        }
    }
}
