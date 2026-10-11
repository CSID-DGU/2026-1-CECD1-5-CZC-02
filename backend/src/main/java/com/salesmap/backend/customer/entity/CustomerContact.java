package com.salesmap.backend.customer.entity;

import com.salesmap.backend.global.entity.BaseEntity;
import com.salesmap.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "customer_contacts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_customer_contacts_user_email", columnNames = {"user_id", "email"})
        }
)
public class CustomerContact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String customerName;

    @Column(length = 100)
    private String contactName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 255)
    private String domain;

    @Column
    private LocalDateTime lastSeenAt;

    protected CustomerContact() {
    }

    public CustomerContact(
            User user,
            String customerName,
            String contactName,
            String email,
            String domain,
            LocalDateTime lastSeenAt
    ) {
        this.user = user;
        this.customerName = customerName;
        this.contactName = contactName;
        this.email = email;
        this.domain = domain;
        this.lastSeenAt = lastSeenAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getContactName() {
        return contactName;
    }

    public String getEmail() {
        return email;
    }

    public String getDomain() {
        return domain;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void updateProfile(String customerName, String contactName, LocalDateTime lastSeenAt) {
        if (customerName != null && !customerName.isBlank()) {
            this.customerName = customerName;
        }
        if (contactName != null && !contactName.isBlank()) {
            this.contactName = contactName;
        }
        if (lastSeenAt != null) {
            this.lastSeenAt = lastSeenAt;
        }
    }
}
