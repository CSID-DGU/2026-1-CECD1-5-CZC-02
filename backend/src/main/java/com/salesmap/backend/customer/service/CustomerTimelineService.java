package com.salesmap.backend.customer.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.customer.dto.CustomerActivityResponse;
import com.salesmap.backend.customer.dto.CustomerSummaryResponse;
import com.salesmap.backend.customer.dto.CustomerTimelineResponse;
import com.salesmap.backend.customer.entity.CustomerActivity;
import com.salesmap.backend.customer.entity.CustomerActivityType;
import com.salesmap.backend.customer.entity.CustomerContact;
import com.salesmap.backend.customer.repository.CustomerActivityRepository;
import com.salesmap.backend.customer.repository.CustomerContactRepository;
import com.salesmap.backend.salesmap.entity.SalesmapRecord;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.source.entity.Source;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CustomerTimelineService {

    private final CustomerContactRepository customerContactRepository;
    private final CustomerActivityRepository customerActivityRepository;
    private final AnalysisRepository analysisRepository;

    public CustomerTimelineService(
            CustomerContactRepository customerContactRepository,
            CustomerActivityRepository customerActivityRepository,
            AnalysisRepository analysisRepository
    ) {
        this.customerContactRepository = customerContactRepository;
        this.customerActivityRepository = customerActivityRepository;
        this.analysisRepository = analysisRepository;
    }

    @Transactional
    public void recordAnalysis(Analysis analysis) {
        if (!shouldRecordTimeline(analysis)) {
            return;
        }

        CustomerContact contact = upsertContact(analysis);
        if (contact == null) {
            return;
        }

        if (customerActivityRepository.existsByAnalysisIdAndActivityType(analysis.getId(), CustomerActivityType.AI_ANALYZED)) {
            return;
        }

        customerActivityRepository.save(new CustomerActivity(
                analysis.getSource().getUser(),
                contact,
                contact.getCustomerName(),
                CustomerActivityType.AI_ANALYZED,
                activityTitle(analysis),
                activityDescription(analysis),
                analysis.getSource(),
                analysis,
                null,
                null,
                firstNotNull(analysis.getAnalyzedAt(), analysis.getCreatedAt(), LocalDateTime.now())
        ));
    }

    @Transactional
    public void recordSalesmapRegistered(SalesmapRecord record, Schedule schedule) {
        Analysis analysis = record.getAnalysis();
        if (!shouldRecordTimeline(analysis)) {
            return;
        }

        CustomerContact contact = upsertContact(analysis);
        if (contact == null) {
            return;
        }

        CustomerActivityType scheduleActivityType = toScheduleActivityType(analysis.getActionType());
        if (scheduleActivityType != null
                && !customerActivityRepository.existsBySalesmapRecordIdAndActivityType(record.getId(), scheduleActivityType)) {
            customerActivityRepository.save(new CustomerActivity(
                    analysis.getSource().getUser(),
                    contact,
                    contact.getCustomerName(),
                    scheduleActivityType,
                    scheduleActivityTitle(scheduleActivityType, schedule, analysis),
                    activityDescription(analysis),
                    analysis.getSource(),
                    analysis,
                    schedule,
                    record,
                    firstNotNull(record.getRegisteredAt(), LocalDateTime.now())
            ));
        }

        if (!customerActivityRepository.existsBySalesmapRecordIdAndActivityType(record.getId(), CustomerActivityType.SALESMAP_REGISTERED)) {
            customerActivityRepository.save(new CustomerActivity(
                    analysis.getSource().getUser(),
                    contact,
                    contact.getCustomerName(),
                    CustomerActivityType.SALESMAP_REGISTERED,
                    "Salesmap 반영 완료",
                    salesmapDescription(analysis),
                    analysis.getSource(),
                    analysis,
                    schedule,
                    record,
                    firstNotNull(record.getRegisteredAt(), LocalDateTime.now())
            ));
        }
    }

    @Transactional
    public List<CustomerSummaryResponse> getCustomers(Long authenticatedUserId) {
        backfillExistingAnalyses(authenticatedUserId);

        return customerContactRepository.findByUserIdOrderByLastSeenAtDescIdDesc(authenticatedUserId).stream()
                .map(contact -> CustomerSummaryResponse.from(
                        contact,
                        customerActivityRepository.countVisibleTimeline(contact.getId(), authenticatedUserId)
                ))
                .filter(customer -> customer.activityCount() > 0)
                .toList();
    }

    private void backfillExistingAnalyses(Long authenticatedUserId) {
        analysisRepository.findByUserId(authenticatedUserId)
                .forEach(this::recordAnalysis);
    }

    @Transactional(readOnly = true)
    public CustomerTimelineResponse getTimeline(Long customerContactId, Long authenticatedUserId) {
        CustomerContact contact = customerContactRepository.findById(customerContactId)
                .orElseThrow(() -> new NoSuchElementException("Customer contact not found."));
        if (!contact.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("You do not have permission to access this customer timeline.");
        }

        List<CustomerActivityResponse> activities = customerActivityRepository
                .findVisibleTimeline(customerContactId, authenticatedUserId)
                .stream()
                .map(CustomerActivityResponse::from)
                .toList();

        return new CustomerTimelineResponse(
                CustomerSummaryResponse.from(
                        contact,
                        customerActivityRepository.countVisibleTimeline(contact.getId(), authenticatedUserId)
                ),
                activities
        );
    }

    @Transactional
    public void unlinkScheduleReferences(Long scheduleId) {
        if (scheduleId == null) {
            return;
        }

        customerActivityRepository.findByScheduleId(scheduleId)
                .forEach(CustomerActivity::unlinkSchedule);
    }

    private CustomerContact upsertContact(Analysis analysis) {
        Source source = analysis.getSource();
        String email = normalizeEmail(source.getSenderEmail());
        if (email == null) {
            return null;
        }

        CustomerContact existing = customerContactRepository.findByUserIdAndEmail(source.getUser().getId(), email)
                .orElse(null);
        String customerName = firstNotBlankOrNull(
                analysis.getCustomerName(),
                existing == null ? null : existing.getCustomerName()
        );
        if (customerName == null) {
            return null;
        }

        String contactName = normalizeDemoContactName(
                customerName,
                firstNotBlankOrNull(analysis.getContactName(), source.getSenderName())
        );
        LocalDateTime lastSeenAt = firstNotNull(source.getSentAt(), source.getCollectedAt(), LocalDateTime.now());

        if (existing != null) {
            existing.updateProfile(customerName, contactName, lastSeenAt);
            return existing;
        }

        return customerContactRepository.save(new CustomerContact(
                source.getUser(),
                customerName,
                contactName,
                email,
                extractDomain(email),
                lastSeenAt
        ));
    }

    private boolean shouldRecordTimeline(Analysis analysis) {
        if (analysis == null) {
            return false;
        }
        if ("NON_BUSINESS".equals(analysis.getBusinessType())) {
            return false;
        }
        return analysis.getCustomerName() != null && !analysis.getCustomerName().isBlank();
    }

    private CustomerActivityType toScheduleActivityType(String actionType) {
        if ("CREATE".equals(actionType)) {
            return CustomerActivityType.SCHEDULE_CREATED;
        }
        if ("UPDATE".equals(actionType)) {
            return CustomerActivityType.SCHEDULE_UPDATED;
        }
        if ("CANCEL".equals(actionType)) {
            return CustomerActivityType.SCHEDULE_CANCELED;
        }
        return null;
    }

    private String activityTitle(Analysis analysis) {
        return firstNotBlankOrNull(
                analysis.getSource().getTitle(),
                analysis.getTargetScheduleTitle(),
                analysis.getSummary(),
                "AI 분석 완료"
        );
    }

    private String activityDescription(Analysis analysis) {
        return String.join("\n", List.of(
                "처리 유형: " + nullToDash(analysis.getActionType()),
                "제품: " + nullToDash(analysis.getProductName()),
                "요약: " + nullToDash(analysis.getSummary()),
                "다음 행동: " + nullToDash(analysis.getFollowUpAction())
        ));
    }

    private String scheduleActivityTitle(CustomerActivityType type, Schedule schedule, Analysis analysis) {
        String title = schedule == null ? null : schedule.getTitle();
        String fallback = firstNotBlankOrNull(analysis.getTargetScheduleTitle(), analysis.getSummary(), "일정");
        String displayTitle = firstNotBlankOrNull(title, fallback, "일정");

        return switch (type) {
            case SCHEDULE_CREATED -> "일정 생성: " + displayTitle;
            case SCHEDULE_UPDATED -> "일정 변경: " + displayTitle;
            case SCHEDULE_CANCELED -> "일정 삭제: " + displayTitle;
            default -> displayTitle;
        };
    }

    private String salesmapDescription(Analysis analysis) {
        return String.join("\n", List.of(
                "고객사: " + nullToDash(analysis.getCustomerName()),
                "제품: " + nullToDash(analysis.getProductName()),
                "처리 유형: " + nullToDash(analysis.getActionType())
        ));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String extractDomain(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return null;
        }
        return email.substring(atIndex + 1);
    }

    private String firstNotBlankOrNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeDemoContactName(String customerName, String contactName) {
        if ("GreenSoft".equals(customerName)) {
            return "박서준";
        }
        if ("Delta Systems".equals(customerName)) {
            return "최유진";
        }
        return contactName;
    }

    @SafeVarargs
    private <T> T firstNotNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
