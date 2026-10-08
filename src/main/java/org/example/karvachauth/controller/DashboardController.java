package org.example.karvachauth.controller;

import lombok.RequiredArgsConstructor;
import org.example.karvachauth.service.DashboardService;
import org.example.karvachauth.service.DashboardService.ConversationFilters;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dashboard APIs — Overview, Conversations, Store Visits and Referral Leads pages.
 *
 * flow  : all / celebrating / shopping / sparkle
 * range : today / 7d / 30d / custom   (custom needs from + to as yyyy-MM-dd, both inclusive)
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/metric-cards")
    public ResponseEntity<Map<String, Object>> metricCards(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(dashboardService.getMetricCards(flow, range, from, to));
    }

    @GetMapping("/outcomes")
    public ResponseEntity<Map<String, Object>> outcomes(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(dashboardService.getOutcomes(flow, range, from, to));
    }

    @GetMapping("/flow-dropoff")
    public ResponseEntity<Map<String, Object>> flowDropOff(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(dashboardService.getFlowDropOff(flow, range, from, to));
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> activity(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(dashboardService.getActivity(flow, range, from, to, limit));
    }

    /** "The three paths" section — always compares all three, so it has no flow filter. */
    @GetMapping("/paths")
    public ResponseEntity<Map<String, Object>> paths(
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(dashboardService.getPathsSummary(range, from, to));
    }

    // ==================================================================
    // CONVERSATIONS PAGE
    // ==================================================================

    /** The 5 cards at the top — follow the path tabs and the date range only. */
    @GetMapping("/conversations/summary")
    public ResponseEntity<Map<String, Object>> conversationsSummary(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(dashboardService.getConversationsSummary(flow, range, from, to));
    }

    /** The table — 50 rows per page, page starts at 0. Every filter is optional. */
    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> conversations(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String step,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "lastActivity") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page) {
        ConversationFilters filters = new ConversationFilters(
                flow, range, from, to, category, step, search, sort, direction);
        return ResponseEntity.ok(dashboardService.getConversations(filters, page));
    }

    /**
     * Rows to export, with the same filters as the table. The frontend builds the CSV / PDF.
     * scope : page (the 50 rows of ?page=) / all (every row for the filters, up to 10,000)
     */
    @GetMapping("/conversations/export")
    public ResponseEntity<Map<String, Object>> conversationsExport(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String step,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "lastActivity") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "0") int page) {
        ConversationFilters filters = new ConversationFilters(
                flow, range, from, to, category, step, search, sort, direction);
        return ResponseEntity.ok(dashboardService.exportConversations(filters, scope, page));
    }

    // ==================================================================
    // STORE VISITS PAGE
    // ==================================================================

    /** The 3 cards — Visits Booked, Booking Form Opened, Most Picked Slot. */
    @GetMapping("/store-visits/summary")
    public ResponseEntity<Map<String, Object>> storeVisitsSummary(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(dashboardService.getStoreVisitsSummary(flow, range, from, to));
    }

    /** Recent Bookings table — newest first, 50 rows per page, page starts at 0. */
    @GetMapping("/store-visits/bookings")
    public ResponseEntity<Map<String, Object>> storeBookings(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(dashboardService.getStoreBookings(flow, range, from, to, page));
    }

    // ==================================================================
    // REFERRAL LEADS PAGE
    // ==================================================================

    /** The 5 cards and the Opt-in Leads section. */
    @GetMapping("/referral-leads/summary")
    public ResponseEntity<Map<String, Object>> referralLeadsSummary(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(dashboardService.getReferralLeadsSummary(flow, range, from, to));
    }

    /**
     * The table — newest first, 50 rows per page, page starts at 0.
     * show : all / husbands / wives / consented
     */
    @GetMapping("/referral-leads")
    public ResponseEntity<Map<String, Object>> referralLeads(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "all") String show,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(dashboardService.getReferralLeads(flow, range, from, to, show, page));
    }

    /**
     * Rows for "Copy as CSV" / export, with the same filters as the table. The frontend builds the file.
     * scope : page (the 50 rows of ?page=) / all (every row for the filters, up to 10,000)
     */
    @GetMapping("/referral-leads/export")
    public ResponseEntity<Map<String, Object>> referralLeadsExport(
            @RequestParam(defaultValue = "all") String flow,
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "all") String show,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(dashboardService.exportReferralLeads(flow, range, from, to, show, scope, page));
    }
}