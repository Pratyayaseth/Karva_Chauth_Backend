package org.example.karvachauth.repository;

import org.example.karvachauth.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findFirstByKarixMessageId(String karixMessageId);

    @Override
    List<Message> findAll();

    /** Full chat transcript for one journey. */
    List<Message> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<Message> findByPhoneOrderByCreatedAtDesc(String phone);

    /**
     * Step 0: her latest template message not yet linked to a session — linked to the session
     * her Step 0 tap starts. Called with messageType = "template".
     */
    Optional<Message> findFirstByPhoneAndMessageTypeAndSessionIdIsNullOrderByIdDesc(String phone, String messageType);

    /** Dashboard: e.g. countByDirectionAndStatus("OUTBOUND", "FAILED"). */
    long countByDirectionAndStatus(String direction, String status);

    long countByDirectionAndCreatedAtBetween(String direction, LocalDateTime from, LocalDateTime to);
}