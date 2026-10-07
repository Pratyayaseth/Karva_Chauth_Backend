package org.example.karvachauth.controller;

import lombok.RequiredArgsConstructor;
import org.example.karvachauth.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Overview page APIs.
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
}