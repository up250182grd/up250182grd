package com.englishos.tutor.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.tutor.domain.Conversation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.publicId = :publicId")
    Optional<Conversation> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Query("select c from Conversation c where c.userId = :userId order by c.id desc")
    List<Conversation> findRecentByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select c.publicId from Conversation c where c.status = 'OPEN' and c.lastActivityAt < :before")
    List<String> findIdleOpen(@Param("before") Instant before);
}
