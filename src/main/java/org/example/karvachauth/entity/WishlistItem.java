package org.example.karvachauth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The wife's / sparkle shopper's list (1C "Added to your list").
 * Replaces the CSV column used in the Rakhi project — a proper table can't
 * overflow at 500 chars and the unique key stops the same product being added twice.
 */
@Entity
@Table(name = "wishlist_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_session_sku", columnNames = {"sessionId", "sku"}),
        indexes = @Index(name = "idx_wishlist_session", columnList = "sessionId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(length = 30)
    private String category;

    private LocalDateTime addedAt;

    @PrePersist
    public void onCreate() {
        addedAt = LocalDateTime.now();
    }
}
