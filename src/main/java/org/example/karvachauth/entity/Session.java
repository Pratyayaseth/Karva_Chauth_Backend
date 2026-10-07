package org.example.karvachauth.entity;



import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The conversation state machine for one journey. Exactly one active session per phone.
 * When a journey reaches E1 (CLOSED) or expires, isActive = false and a new
 * session is created the next time the customer engages.
 */
@Entity
@Table(name = "sessions", indexes = {
        @Index(name = "idx_sessions_phone_active", columnList = "phone, isActive"),
        @Index(name = "idx_sessions_idle", columnList = "isActive, idleNudgeSent, lastInboundAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // ---------- Flow state ----------
    @Column(length = 20)
    private String path;                 // WIFE / HUSBAND / SPARKLE — null until Step 0 is answered

    @Column(length = 50)
    private String currentStep;          // KarvaChauthConstants.STEP_*

    @Column(length = 50)
    private String returnStep;           // where C1 "Keep browsing" / C2 completion goes back to

    // ---------- Browsing context ----------
    @Column(length = 30)
    private String selectedCategory;     // CAT_* — remembered for "See more" / "Browse again"

    @Column(length = 20)
    private String selectedBudget;       // BUDGET_*

    @Builder.Default
    private Integer productPage = 0;     // carousel page for "See more"

    @Column(length = 50)
    private String selectedProductSku;   // husband's single pick (2C) / tapped card before C2

    // ---------- Store visit (C2) ----------
    @Column(length = 10)
    private String pincode;

    @Column(length = 30)
    private String storeCode;

    @Column(length = 200)
    private String storeName;

    // ---------- Partner capture (1J for wife, 2F for husband) ----------
    @Column(length = 100)
    private String partnerName;          // husband's name (1J) or wife's name (2F)

    private LocalDate partnerDate;       // anniversary (1J) or her birthday (2F) — parsed & validated

    @Column(length = 20)
    private String partnerPhone;

    @Column(length = 10)
    private String consent;              // CONSENT_PENDING / YES / NO — answer to 1K / 2G

    // ---------- Idle nudge ----------
    /**
     * Set ONLY when an inbound message arrives — never in @PreUpdate.
     * If this were touched on every save, sending the idle nudge would itself
     * reset the idle timer.
     */
    private LocalDateTime lastInboundAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean idleNudgeSent = false;

    // ---------- Housekeeping ----------
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    @Version
    private Long version;                // optimistic lock — catches concurrent updates (consumer vs idle scheduler)

    @PrePersist
    public void onCreate() {
        startedAt = LocalDateTime.now();
        updatedAt = startedAt;
        if (lastInboundAt == null) lastInboundAt = startedAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}