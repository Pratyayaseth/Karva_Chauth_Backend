package org.example.karvachauth.repository;

import org.example.karvachauth.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * All dashboard native queries. Anchored to Lead only so Spring Data recognises
 * this as a JPA repository; every method here is a native query.
 *
 * Common parameters:
 *   path -> 'WIFE' / 'HUSBAND' / 'SPARKLE' (TEMPLATE_* constants), or NULL for all paths
 *   from -> inclusive start of the window
 *   to   -> exclusive end of the window (start of the day after the last day)
 *
 * Two kinds of window:
 *   - EVENT queries (metric cards, outcomes, activity) count things that HAPPENED in the window.
 *   - FUNNEL queries (bot flow drop-off) follow the sessions that STARTED in the window
 *     (sessions.started_at), so every stage is a subset of "Opener" and never goes above 100%.
 *
 * How the bot records data (BotEngineServiceImple):
 *   - A Step 0 tap creates a new sessions row — that row IS the opener tap.
 *   - Every message is saved with the step the session was on at that moment. Outbound messages
 *     are saved BEFORE the step moves, so "reached step X" = any message (in or out) saved with
 *     step X. Each later tap, follow-up or idle nudge carries the new step.
 */
public interface DashboardRepository extends JpaRepository<Lead, Long> {

    // ==================================================================
    // METRIC CARDS — outbound messages (event window)
    // Step 0 campaign templates have no path, so they appear under "all" only.
    // ==================================================================

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM messages m " +
                    "WHERE m.direction = 'OUTBOUND' " +
                    "  AND m.status IN (:statuses) " +
                    "  AND (:path IS NULL OR m.path = :path) " +
                    "  AND m.created_at >= :from AND m.created_at < :to")
    long countOutboundMessagesByStatus(@Param("statuses") List<String> statuses,
                                       @Param("path") String path,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    // ==================================================================
    // OPENER TAPS — one sessions row per Step 0 tap
    // ==================================================================

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM sessions s " +
                    "WHERE s.path IS NOT NULL " +
                    "  AND (:path IS NULL OR s.path = :path) " +
                    "  AND s.started_at >= :from AND s.started_at < :to")
    long countOpenerTaps(@Param("path") String path,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    interface PathCountProjection {
        String getPath();
        Long getTotal();
    }

    @Query(nativeQuery = true, value =
            "SELECT s.path AS path, COUNT(*) AS total FROM sessions s " +
                    "WHERE s.path IS NOT NULL " +
                    "  AND s.started_at >= :from AND s.started_at < :to " +
                    "GROUP BY s.path")
    List<PathCountProjection> countOpenerTapsByPath(@Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);

    // ==================================================================
    // BOT FLOW DROP-OFF — sessions started in the window
    // ==================================================================

    /** Sessions that tapped this exact button (e.g. 'W_CHOOSE_MY_GIFT', 'W_SEND_ISHARA'). */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT m.session_id) FROM messages m " +
                    "JOIN sessions s ON s.id = m.session_id " +
                    "WHERE m.direction = 'INBOUND' " +
                    "  AND m.button_payload = :payload " +
                    "  AND (:path IS NULL OR s.path = :path) " +
                    "  AND s.started_at >= :from AND s.started_at < :to")
    long countSessionsWithButtonTap(@Param("payload") String payload,
                                    @Param("path") String path,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * Sessions that tapped any button starting with this prefix (e.g. 'CAT_' for a category,
     * 'H_BUDGET_' for a budget row). LEFT(...) is used instead of LIKE because '_' is a
     * wildcard in LIKE.
     */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT m.session_id) FROM messages m " +
                    "JOIN sessions s ON s.id = m.session_id " +
                    "WHERE m.direction = 'INBOUND' " +
                    "  AND LEFT(m.button_payload, CHAR_LENGTH(:payloadPrefix)) = :payloadPrefix " +
                    "  AND (:path IS NULL OR s.path = :path) " +
                    "  AND s.started_at >= :from AND s.started_at < :to")
    long countSessionsWithButtonTapPrefix(@Param("payloadPrefix") String payloadPrefix,
                                          @Param("path") String path,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * Sessions that reached one of these steps — any message (inbound or outbound) saved
     * while the session was on that step. A session that stops dead on a step shows up
     * once the idle nudge goes out (within karvachauth.idle.minutes).
     */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT m.session_id) FROM messages m " +
                    "JOIN sessions s ON s.id = m.session_id " +
                    "WHERE m.step IN (:steps) " +
                    "  AND (:path IS NULL OR s.path = :path) " +
                    "  AND s.started_at >= :from AND s.started_at < :to")
    long countSessionsReachedStep(@Param("steps") List<String> steps,
                                  @Param("path") String path,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT pp.session_id) FROM product_picks pp " +
                    "JOIN sessions s ON s.id = pp.session_id " +
                    "WHERE pp.action_type IN (:actionTypes) " +
                    "  AND (:path IS NULL OR s.path = :path) " +
                    "  AND s.started_at >= :from AND s.started_at < :to")
    long countSessionsWithPickAction(@Param("actionTypes") List<String> actionTypes,
                                     @Param("path") String path,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT l.session_id) FROM leads l " +
                    "JOIN sessions s ON s.id = l.session_id " +
                    "WHERE l.lead_type IN (:leadTypes) " +
                    "  AND (:path IS NULL OR s.path = :path) " +
                    "  AND s.started_at >= :from AND s.started_at < :to")
    long countSessionsWithLead(@Param("leadTypes") List<String> leadTypes,
                               @Param("path") String path,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /** Partner captured WITH a mobile — the bot only keeps the mobile when consent is YES. */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT l.session_id) FROM leads l " +
                    "JOIN sessions s ON s.id = l.session_id " +
                    "WHERE l.lead_type IN (:referralTypes) " +
                    "  AND l.partner_phone IS NOT NULL AND l.partner_phone <> '' " +
                    "  AND (:path IS NULL OR s.path = :path) " +
                    "  AND s.started_at >= :from AND s.started_at < :to")
    long countSessionsWithReferralLead(@Param("referralTypes") List<String> referralTypes,
                                       @Param("path") String path,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    // ==================================================================
    // OUTCOMES + PATH CARDS — leads and picks (event window)
    // ==================================================================

    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT l.session_id) FROM leads l " +
                    "WHERE l.lead_type = :leadType " +
                    "  AND (:path IS NULL OR l.path = :path) " +
                    "  AND l.created_at >= :from AND l.created_at < :to")
    long countLeadsByType(@Param("leadType") String leadType,
                          @Param("path") String path,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    /** A referral counts only when the partner's mobile was captured (= consent YES). */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT l.session_id) FROM leads l " +
                    "WHERE l.lead_type IN (:referralTypes) " +
                    "  AND l.partner_phone IS NOT NULL AND l.partner_phone <> '' " +
                    "  AND (:path IS NULL OR l.path = :path) " +
                    "  AND l.created_at >= :from AND l.created_at < :to")
    long countReferralLeads(@Param("referralTypes") List<String> referralTypes,
                            @Param("path") String path,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /** Distinct pieces added to a list (the same piece in two sessions counts twice). */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT CONCAT(pp.session_id, '-', pp.sku)) FROM product_picks pp " +
                    "WHERE pp.action_type = :actionType " +
                    "  AND (:path IS NULL OR pp.path = :path) " +
                    "  AND pp.created_at >= :from AND pp.created_at < :to")
    long countPiecesByPickAction(@Param("actionType") String actionType,
                                 @Param("path") String path,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    // ==================================================================
    // OUTCOMES — daily counts for the sparklines (event window)
    // ==================================================================

    interface DailyCountProjection {
        String getDay();     // yyyy-MM-dd
        Long getTotal();
    }

    @Query(nativeQuery = true, value =
            "SELECT DATE_FORMAT(l.created_at, '%Y-%m-%d') AS day, COUNT(DISTINCT l.session_id) AS total " +
                    "FROM leads l " +
                    "WHERE l.lead_type = :leadType " +
                    "  AND (:path IS NULL OR l.path = :path) " +
                    "  AND l.created_at >= :from AND l.created_at < :to " +
                    "GROUP BY DATE_FORMAT(l.created_at, '%Y-%m-%d') " +
                    "ORDER BY day")
    List<DailyCountProjection> findDailyLeadCounts(@Param("leadType") String leadType,
                                                   @Param("path") String path,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    @Query(nativeQuery = true, value =
            "SELECT DATE_FORMAT(l.created_at, '%Y-%m-%d') AS day, COUNT(DISTINCT l.session_id) AS total " +
                    "FROM leads l " +
                    "WHERE l.lead_type IN (:referralTypes) " +
                    "  AND l.partner_phone IS NOT NULL AND l.partner_phone <> '' " +
                    "  AND (:path IS NULL OR l.path = :path) " +
                    "  AND l.created_at >= :from AND l.created_at < :to " +
                    "GROUP BY DATE_FORMAT(l.created_at, '%Y-%m-%d') " +
                    "ORDER BY day")
    List<DailyCountProjection> findDailyReferralCounts(@Param("referralTypes") List<String> referralTypes,
                                                       @Param("path") String path,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);

    // ==================================================================
    // LIVE ACTIVITY — lead events + product pick events, newest first (event window)
    // ==================================================================

    interface ActivityRowProjection {
        String getSource();          // LEAD / PICK
        String getEventType();       // lead_type or action_type
        String getPath();
        String getCustomerName();
        String getPhone();
        String getPartnerPhone();
        String getConsent();
        String getStoreName();
        String getProductName();
        String getSku();
        LocalDateTime getEventTime();
    }

    @Query(nativeQuery = true, value =
            "(SELECT 'LEAD' AS source, l.lead_type AS eventType, l.path AS path, " +
                    "        COALESCE(l.customer_name, c.name) AS customerName, l.phone AS phone, " +
                    "        l.partner_phone AS partnerPhone, l.consent AS consent, l.store_name AS storeName, " +
                    "        CAST(NULL AS CHAR) AS productName, CAST(NULL AS CHAR) AS sku, " +
                    "        l.created_at AS eventTime " +
                    " FROM leads l " +
                    " LEFT JOIN customers c ON c.phone = l.phone " +
                    " WHERE (:path IS NULL OR l.path = :path) " +
                    "   AND l.created_at >= :from AND l.created_at < :to) " +
                    "UNION ALL " +
                    "(SELECT 'PICK' AS source, pp.action_type AS eventType, pp.path AS path, " +
                    "        c.name AS customerName, pp.phone AS phone, " +
                    "        CAST(NULL AS CHAR) AS partnerPhone, CAST(NULL AS CHAR) AS consent, CAST(NULL AS CHAR) AS storeName, " +
                    "        p.name AS productName, pp.sku AS sku, " +
                    "        pp.created_at AS eventTime " +
                    " FROM product_picks pp " +
                    " LEFT JOIN customers c ON c.phone = pp.phone " +
                    " LEFT JOIN products p ON p.sku = pp.sku " +
                    " WHERE pp.action_type IN (:pickActions) " +
                    "   AND (:path IS NULL OR pp.path = :path) " +
                    "   AND pp.created_at >= :from AND pp.created_at < :to) " +
                    "ORDER BY eventTime DESC " +
                    "LIMIT :limit")
    List<ActivityRowProjection> findRecentActivity(@Param("pickActions") List<String> pickActions,
                                                   @Param("path") String path,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to,
                                                   @Param("limit") int limit);
}