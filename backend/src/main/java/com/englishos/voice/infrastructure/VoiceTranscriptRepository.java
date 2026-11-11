package com.englishos.voice.infrastructure;

import java.util.List;

import com.englishos.voice.domain.VoiceTranscript;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceTranscriptRepository extends JpaRepository<VoiceTranscript, Long> {

    List<VoiceTranscript> findBySessionIdOrderByIdAsc(Long sessionId);
}
