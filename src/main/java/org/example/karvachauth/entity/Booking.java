package org.example.karvachauth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A confirmed store visit from the C2 Flow — the MIA-xxxx reference shown to the customer.
 */
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_bookings_phone", columnList = "phone"),
        @Index(name = "idx_bookings_store_date", columnList = "storeCode, visitDate"),
        @Index(name = "idx_bookings_created", columnList = "createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String bookingRef;           // MIA-XXXX

    @Column(nullable = false)
    private Long customerId;

    private Long sessionId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 20)
    private String path;

    @Column(nullable = false, length = 30)
    private String storeCode;

    @Column(length = 200)
    private String storeName;

    private LocalDate visitDate;

    @Column(length = 30)
    private String timeSlot;             // e.g. "11:00 AM – 1:00 PM"

    @Column(length = 1000)
    private String reservedSkus;         // comma-separated snapshot of what was reserved

    @Column(length = 20)
    @Builder.Default
    private String status = "CONFIRMED"; // CONFIRMED / CANCELLED / VISITED

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }
}