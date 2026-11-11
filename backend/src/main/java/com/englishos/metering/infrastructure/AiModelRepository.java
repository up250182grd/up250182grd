package com.englishos.metering.infrastructure;

import java.util.Optional;

import com.englishos.metering.domain.AiModel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    Optional<AiModel> findByProviderAndModelCode(String provider, String modelCode);
}
