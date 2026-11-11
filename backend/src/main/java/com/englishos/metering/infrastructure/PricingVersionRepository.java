package com.englishos.metering.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.englishos.metering.domain.PricingVersion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingVersionRepository extends JpaRepository<PricingVersion, Long> {

    Optional<PricingVersion> findFirstByModelIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(Long modelId,
            Instant at);

    List<PricingVersion> findByModelIdOrderByEffectiveFromDescIdDesc(Long modelId);
}
