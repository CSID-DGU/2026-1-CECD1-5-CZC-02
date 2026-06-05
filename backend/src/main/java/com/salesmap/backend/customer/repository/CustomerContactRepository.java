package com.salesmap.backend.customer.repository;

import com.salesmap.backend.customer.entity.CustomerContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerContactRepository extends JpaRepository<CustomerContact, Long> {

    Optional<CustomerContact> findByUserIdAndEmail(Long userId, String email);

    List<CustomerContact> findByUserIdOrderByLastSeenAtDescIdDesc(Long userId);
}
