package com.englishos.wallet.infrastructure;

import java.util.List;

import com.englishos.wallet.domain.BillingIncident;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingIncidentRepository extends JpaRepository<BillingIncident, Long> {

    List<BillingIncident> findTop100ByStatusOrderByIdDesc(String status);

    List<BillingIncident> findByUserId(Long userId);
}
