package org.example.karvachauth.repository;

import org.example.karvachauth.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    /** Load the wishlist's products in one query. */
    List<Product> findBySkuIn(Collection<String> skus);

    /**
     * Step 1B / 1D / "See more" — full category (Wife path).
     * Page:  PageRequest.of(session.getProductPage(), 9)
     */
    List<Product> findByCategoryAndActiveTrueOrderByDisplayOrderAscPriceAsc(String category, Pageable pageable);

    /** Total in the category — decides whether "See more" is shown. */
    long countByCategoryAndActiveTrue(String category);

    /**
     * Step 1B (Sparkle) / 2B (Husband) — filtered to the budget band picked in 1A-Budget / 2A-Budget.
     * minPrice / maxPrice come from the band (e.g. UNDER_50K → 0 .. 49_999).
     */
    List<Product> findByCategoryAndActiveTrueAndPriceBetweenOrderByDisplayOrderAscPriceAsc(
            String category, Integer minPrice, Integer maxPrice, Pageable pageable);

    long countByCategoryAndActiveTrueAndPriceBetween(String category, Integer minPrice, Integer maxPrice);
}