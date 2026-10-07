package org.example.karvachauth.repository;

import org.example.karvachauth.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByBtqCode(String btqCode);

    /** Loaded once into memory for the nearest-store (haversine) calculation. */
    List<Store> findByActiveTrue();

    /** Simple fallback when there are no coordinates: same PIN code. */
    List<Store> findByPincodeAndActiveTrue(String pincode);
}