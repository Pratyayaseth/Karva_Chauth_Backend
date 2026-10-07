package org.example.karvachauth.repository;

import org.example.karvachauth.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Id is the Karix providerMessageId (String), not a Long.
 * The consumer just calls save() — a duplicate-key error means "already processed, skip".
 */
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    /** Cleanup job — keep ~7 days. */
    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessedMessage p WHERE p.processedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}