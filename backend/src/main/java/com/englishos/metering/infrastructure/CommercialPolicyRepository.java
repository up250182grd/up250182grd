package com.englishos.metering.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.englishos.metering.domain.CommercialPolicy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPolicyRepository extends JpaRepository<CommercialPolicy, Long> {

    Optional<CommercialPolicy> findFirstByRoleCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            String roleCode, Instant at);

    List<CommercialPolicy> findAllByOrderByRoleCodeAscEffectiveFromDescIdDesc();
}
