package com.englishos.payments.infrastructure;

import java.util.List;

import com.englishos.payments.domain.PaymentRefund;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    List<PaymentRefund> findByIntentIdOrderByIdAsc(Long intentId);
}
