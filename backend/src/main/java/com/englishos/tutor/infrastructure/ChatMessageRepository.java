package com.englishos.tutor.infrastructure;

import java.util.List;

import com.englishos.tutor.domain.ChatMessage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("select m from ChatMessage m where m.conversationId = :id order by m.id desc")
    List<ChatMessage> findLatest(@Param("id") Long conversationId, Pageable pageable);
}
