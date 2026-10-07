package org.example.karvachauth.repository;

import org.example.karvachauth.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    /** Dashboard login. */
    Optional<AdminUser> findByEmail(String email);

    boolean existsByEmail(String email);
}