package org.example.karvachauth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per WhatsApp number, ever. Sessions come and go; the customer stays.
 */
@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String phone;

    @Column(length = 100)
    private String name;                 // WhatsApp profile name (Karix "profileName"), latest wins

    @Column(length = 20)
    private String path;                 // WIFE / HUSBAND / SPARKLE — path chosen in their first session

    @Column(length = 30)
    private String acquisitionSource;    // CRM_BLAST / INSTAGRAM / QR / DIRECT

    /** Her own answer to "keep me posted on new arrivals and offers?" — YES / NO, null = never asked. */
    @Column(length = 10)
    private String marketingConsent;

    private LocalDateTime marketingConsentAt;   // when that answer was given

    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;

    @PrePersist
    public void onCreate() {
        firstSeen = LocalDateTime.now();
        lastSeen = firstSeen;
    }

    @PreUpdate
    public void onUpdate() {
        lastSeen = LocalDateTime.now();
    }
}