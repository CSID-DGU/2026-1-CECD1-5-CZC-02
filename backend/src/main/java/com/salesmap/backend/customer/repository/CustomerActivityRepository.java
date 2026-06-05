package com.salesmap.backend.customer.repository;

import com.salesmap.backend.customer.entity.CustomerActivity;
import com.salesmap.backend.customer.entity.CustomerActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerActivityRepository extends JpaRepository<CustomerActivity, Long> {

    @Query("""
            select ca
            from CustomerActivity ca
            left join ca.analysis a
            where ca.customerContact.id = :customerContactId
              and ca.user.id = :userId
              and (a is null or a.businessType is null or a.businessType <> 'NON_BUSINESS')
            order by ca.occurredAt desc, ca.id desc
            """)
    List<CustomerActivity> findVisibleTimeline(
            @Param("customerContactId") Long customerContactId,
            @Param("userId") Long userId
    );

    List<CustomerActivity> findByScheduleId(Long scheduleId);

    boolean existsByAnalysisIdAndActivityType(Long analysisId, CustomerActivityType activityType);

    boolean existsBySalesmapRecordIdAndActivityType(Long salesmapRecordId, CustomerActivityType activityType);

    @Query("""
            select count(ca)
            from CustomerActivity ca
            left join ca.analysis a
            where ca.customerContact.id = :customerContactId
              and ca.user.id = :userId
              and (a is null or a.businessType is null or a.businessType <> 'NON_BUSINESS')
            """)
    long countVisibleTimeline(
            @Param("customerContactId") Long customerContactId,
            @Param("userId") Long userId
    );
}
