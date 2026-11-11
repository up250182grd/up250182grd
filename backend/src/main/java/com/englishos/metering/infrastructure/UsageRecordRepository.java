package com.englishos.metering.infrastructure;

import java.util.List;
import java.util.Optional;

import com.englishos.metering.domain.UsageRecord;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    Optional<UsageRecord> findByIdempotencyKey(String idempotencyKey);

    List<UsageRecord> findBySubjectTypeAndSubjectId(String subjectType, Long subjectId);
}
