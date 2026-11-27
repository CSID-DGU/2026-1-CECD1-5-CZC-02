package com.salesmap.backend.global.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DataIntegrityLogRedactionTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void captureLogs() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void stopCapturing() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsCodesWithoutMessagesOrStackTraceAndPreservesResponse() {
        SQLException sql = new SQLException("Duplicate entry synthetic@example.test", "23000", 1062);
        var response = handler.handleDataIntegrityViolationException(
                new DataIntegrityViolationException("INSERT password=synthetic-secret", sql));

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("데이터 저장 중 오류가 발생했습니다.", response.getBody().message());
        assertEquals(1, appender.list.size());
        var event = appender.list.get(0);
        assertEquals("Data integrity violation: type=DataIntegrityViolationException, sqlState=23000, vendorCode=1062",
                event.getFormattedMessage());
        assertNull(event.getThrowableProxy());
        for (Object argument : event.getArgumentArray()) {
            assertFalse(argument instanceof Throwable);
            assertFalse(argument.toString().contains("synthetic"));
        }
    }

    @Test
    void handlesExceptionsWithoutSqlCause() {
        handler.handleDataIntegrityViolationException(
                new DataIntegrityViolationException("synthetic-private-value"));
        assertTrue(appender.list.get(0).getFormattedMessage().contains("sqlState=UNKNOWN, vendorCode=0"));
        assertNull(appender.list.get(0).getThrowableProxy());
    }

    @Test
    void rejectsUnexpectedSqlStateContents() {
        handler.handleDataIntegrityViolationException(new DataIntegrityViolationException("synthetic-value",
                new SQLException("synthetic-message", "synthetic@example.test\nforged-log", 999)));
        assertTrue(appender.list.get(0).getFormattedMessage().contains("sqlState=UNKNOWN, vendorCode=999"));
        assertFalse(appender.list.get(0).getFormattedMessage().contains("synthetic"));
    }
}
