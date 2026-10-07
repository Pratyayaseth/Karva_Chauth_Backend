package org.example.karvachauth.repository;

import org.example.karvachauth.entity.ProductPick;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductPickRepository extends JpaRepository<ProductPick, Long> {

    long countByActionType(String actionType);

    /**
     * Dashboard: most-picked products for an action, as [sku, count] rows.
     * e.g. topSkus("ADDED", PageRequest.of(0, 10))
     */
    @Query("""
           SELECT p.sku, COUNT(p)
           FROM ProductPick p
           WHERE p.actionType = :actionType
           GROUP BY p.sku
           ORDER BY COUNT(p) DESC
           """)
    List<Object[]> topSkus(@Param("actionType") String actionType, Pageable pageable);
}