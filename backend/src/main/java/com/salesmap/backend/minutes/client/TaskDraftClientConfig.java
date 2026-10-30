package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.task.MeetingTaskAdapter;
import com.salesmap.backend.task.TaskAiClient;
import com.salesmap.backend.task.TaskService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * C의 MeetingTaskAdapter를 B의 TaskDraftClient로 등록한다 (C README: "B가 Fake를 제거하고 등록").
 * 어댑터는 B 서비스에 의존하지 않으므로 순환 의존이 없다.
 */
@Configuration
public class TaskDraftClientConfig {

    @Bean
    public TaskDraftClient taskDraftClient(TaskAiClient taskAiClient, TaskService taskService, ObjectMapper objectMapper) {
        return new MeetingTaskAdapter(taskAiClient, taskService, objectMapper);
    }
}
