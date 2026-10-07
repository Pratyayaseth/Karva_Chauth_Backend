package org.example.karvachauth.repository;

import org.example.karvachauth.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<AdminUser,Long> {
}
