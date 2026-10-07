package org.example.karvachauth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Idempotency guard for the inbound consumer. Inserted in the SAME transaction
 * as the session update — if the insert hits a duplicate key, the event was
 * already handled and is skipped. Service Bus duplicate detection only covers
 * its time window; this covers redelivery after a crash.
 *
 * Purge rows older than ~7 days with a scheduled job.
 */
@Entity
@Table(name = "processed_messages", indexes = {
        @Index(name = "idx_processed_at", columnList = "processedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedMessage {

    @Id
    @Column(length = 100)
    private String providerMessageId;

    private LocalDateTime processedAt;

    @PrePersist
    public void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
