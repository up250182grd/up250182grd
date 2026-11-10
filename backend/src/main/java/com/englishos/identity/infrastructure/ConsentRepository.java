package com.englishos.identity.infrastructure;

import java.util.List;

import com.englishos.identity.domain.Consent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findByUserId(Long userId);
}
