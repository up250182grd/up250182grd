package com.englishos.recommendation.infrastructure;

import java.util.List;
import java.util.Optional;

import com.englishos.recommendation.domain.WeightsVersion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightsVersionRepository extends JpaRepository<WeightsVersion, Long> {

    Optional<WeightsVersion> findFirstByOrderByIdDesc();

    List<WeightsVersion> findTop20ByOrderByIdDesc();
}
