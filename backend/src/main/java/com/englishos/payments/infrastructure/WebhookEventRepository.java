package com.englishos.payments.infrastructure;

import com.englishos.payments.domain.WebhookEvent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
}
