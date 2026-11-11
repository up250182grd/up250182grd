package com.englishos.voice.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.voice.domain.VoiceSession;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoiceSessionRepository extends JpaRepository<VoiceSession, Long> {

    Optional<VoiceSession> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from VoiceSession s where s.id = :id")
    Optional<VoiceSession> findByIdForUpdate(@Param("id") Long id);

    boolean existsByActiveUserId(Long userId);

    List<VoiceSession> findByStatusIn(Collection<VoiceSession.Status> statuses);

    @Query("select s from VoiceSession s where s.userId = :userId order by s.id desc")
    List<VoiceSession> findRecentByUser(@Param("userId") Long userId, Pageable pageable);
}
