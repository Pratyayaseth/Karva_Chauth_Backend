package org.example.karvachauth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Every conversion point writes one row here — same idea as the Rakhi project,
 * so the dashboard queries carry over.
 *
 * leadType: HINT_SENT / HUSBAND_CAPTURED / WIFE_CAPTURED / STORE_VISIT_BOOKED /
 *           STORE_FINDER_OPENED / BUY_ONLINE_CLICKED / SPARKLE_OPT_IN
 */
@Entity
@Table(name = "leads", indexes = {
        @Index(name = "idx_leads_phone", columnList = "phone"),
        @Index(name = "idx_leads_type", columnList = "leadType"),
        @Index(name = "idx_leads_created", columnList = "createdAt"),
        @Index(name = "idx_leads_composite", columnList = "leadType, path, createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    private Long sessionId;

    @Column(length = 100)
    private String customerName;         // WhatsApp profile name of the customer (from Karix)

    @Column(length = 20)
    private String path;                 // WIFE / HUSBAND / SPARKLE

    @Column(nullable = false, length = 30)
    private String leadType;

    @Column(length = 50)
    private String sourceStep;

    // ---------- Partner details (1J / 2F) ----------
    @Column(length = 100)
    private String partnerName;

    private LocalDate partnerDate;

    @Column(length = 20)
    private String partnerDateType;      // ANNIVERSARY / BIRTHDAY

    @Column(length = 20)
    private String partnerPhone;

    @Column(length = 10)
    private String consent;              // YES / NO — the answer itself; createdAt is WHEN it was given

    // ---------- Product / store context ----------
    @Column(length = 30)
    private String selectedCategory;

    @Column(length = 1000)
    private String productSkus;          // one or more, comma-separated

    @Column(length = 20)
    private String bookingRef;

    @Column(length = 200)
    private String storeName;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }
}