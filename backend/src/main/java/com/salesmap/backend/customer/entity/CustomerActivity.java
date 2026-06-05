package com.salesmap.backend.customer.entity;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.global.entity.BaseEntity;
import com.salesmap.backend.salesmap.entity.SalesmapRecord;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_activities")
public class CustomerActivity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_contact_id", nullable = false)
    private CustomerContact customerContact;

    @Column(nullable = false, length = 255)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CustomerActivityType activityType;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesmap_record_id")
    private SalesmapRecord salesmapRecord;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    protected CustomerActivity() {
    }

    public CustomerActivity(
            User user,
            CustomerContact customerContact,
            String customerName,
            CustomerActivityType activityType,
            String title,
            String description,
            Source source,
            Analysis analysis,
            Schedule schedule,
            SalesmapRecord salesmapRecord,
            LocalDateTime occurredAt
    ) {
        this.user = user;
        this.customerContact = customerContact;
        this.customerName = customerName;
        this.activityType = activityType;
        this.title = title;
        this.description = description;
        this.source = source;
        this.analysis = analysis;
        this.schedule = schedule;
        this.salesmapRecord = salesmapRecord;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public CustomerContact getCustomerContact() {
        return customerContact;
    }

    public String getCustomerName() {
        return customerName;
    }

    public CustomerActivityType getActivityType() {
        return activityType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Source getSource() {
        return source;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public SalesmapRecord getSalesmapRecord() {
        return salesmapRecord;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void unlinkSchedule() {
        this.schedule = null;
    }
}
