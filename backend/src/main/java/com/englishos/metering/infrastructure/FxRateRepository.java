package com.englishos.metering.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.englishos.metering.domain.FxRate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    Optional<FxRate> findFirstByBaseAndQuoteAndRateDateBetweenOrderByRateDateDescIdDesc(String base, String quote,
            LocalDate from, LocalDate to);

    List<FxRate> findTop30ByOrderByRateDateDescIdDesc();
}
