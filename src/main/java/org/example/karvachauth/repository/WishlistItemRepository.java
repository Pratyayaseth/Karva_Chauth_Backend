package org.example.karvachauth.repository;

import org.example.karvachauth.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    /** Her list, in the order she added things — used in the 1G hint and C2. */
    List<WishlistItem> findBySessionIdOrderByAddedAtAsc(Long sessionId);

    /** Checked before inserting, so a double-tap on "Add to my list" doesn't hit the unique key. */
    boolean existsBySessionIdAndSku(Long sessionId, String sku);

    /** The {count} in Step 1C. */
    long countBySessionId(Long sessionId);

    @Transactional
    long deleteBySessionIdAndSku(Long sessionId, String sku);
}