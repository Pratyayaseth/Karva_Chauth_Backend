package org.example.karvachauth.repository;

import org.example.karvachauth.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByPhoneOrderByCreatedAtDesc(String phone);

    /** Stops the same conversion being counted twice in one session (double tap, retried step). */
    boolean existsBySessionIdAndLeadType(Long sessionId, String leadType);

    long countByLeadType(String leadType);

    long countByLeadTypeAndPath(String leadType, String path);

    List<Lead> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    /** Dashboard funnel: [leadType, path, count] rows for a date range. */
    @Query("""
           SELECT l.leadType, l.path, COUNT(l)
           FROM Lead l
           WHERE l.createdAt BETWEEN :from AND :to
           GROUP BY l.leadType, l.path
           """)
    List<Object[]> countByTypeAndPath(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}