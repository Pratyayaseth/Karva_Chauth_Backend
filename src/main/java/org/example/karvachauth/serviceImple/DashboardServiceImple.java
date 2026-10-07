package org.example.karvachauth.serviceImple;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.karvachauth.repository.DashboardRepository;
import org.example.karvachauth.service.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.example.karvachauth.constants.KarvaChauthConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImple implements DashboardService {

    private final DashboardRepository dashboardRepo;

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_ACTIVITY_LIMIT = 50;

    // Message statuses
    private static final List<String> ALL_SENT_STATUSES =
            List.of(MSG_STATUS_SENT, MSG_STATUS_DELIVERED, MSG_STATUS_READ, MSG_STATUS_FAILED);
    private static final List<String> DELIVERED_STATUSES = List.of(MSG_STATUS_DELIVERED, MSG_STATUS_READ);
    private static final List<String> READ_STATUSES = List.of(MSG_STATUS_READ);
    private static final List<String> FAILED_STATUSES = List.of(MSG_STATUS_FAILED);

    // Button payloads the bot records on inbound taps (same strings as BotEngineServiceImple)
    private static final String TAP_WIFE_CHOOSE_MY_GIFT = "W_CHOOSE_MY_GIFT";
    private static final String TAP_WIFE_SEND_ISHARA = "W_SEND_ISHARA";
    private static final String TAP_HUSBAND_FIND_HER_GIFT = "H_FIND_HER_GIFT";
    private static final String TAP_SPARKLE_SHOW_ME = "S_SHOW_ME";
    private static final String TAP_CATEGORY_PREFIX = "CAT_";
    private static final String TAP_HUSBAND_BUDGET_PREFIX = "H_BUDGET_";
    private static final String TAP_SPARKLE_BUDGET_PREFIX = "S_BUDGET_";

    // Product carousel step per path
    private static final List<String> ALL_CAROUSEL_STEPS =
            List.of(STEP_W_BROWSE_PRODUCTS, STEP_H_BROWSE_PRODUCTS, STEP_S_BROWSE_PRODUCTS);

    // Product pick actions
    private static final List<String> ADDED_ACTION = List.of(PICK_ADDED);
    private static final List<String> SELECTED_ACTION = List.of(PICK_SELECTED);
    private static final List<String> CHOSE_PIECE_ACTIONS = List.of(PICK_ADDED, PICK_SELECTED);

    // Referral leads (partner details captured)
    private static final List<String> ALL_REFERRAL_TYPES = List.of(LEAD_HUSBAND_CAPTURED, LEAD_WIFE_CAPTURED);
    private static final List<String> HUSBAND_REFERRAL = List.of(LEAD_HUSBAND_CAPTURED);
    private static final List<String> WIFE_REFERRAL = List.of(LEAD_WIFE_CAPTURED);

    // ==================================================================
    // METRIC CARDS
    // ==================================================================

    @Override
    public Map<String, Object> getMetricCards(String flow, String range, String startDate, String endDate) {
        DateWindow window = resolveDateTimeRange(range, startDate, endDate);
        String pathType = flowToPathType(flow);

        long sent = dashboardRepo.countOutboundMessagesByStatus(ALL_SENT_STATUSES, pathType, window.from(), window.to());
        long delivered = dashboardRepo.countOutboundMessagesByStatus(DELIVERED_STATUSES, pathType, window.from(), window.to());
        long read = dashboardRepo.countOutboundMessagesByStatus(READ_STATUSES, pathType, window.from(), window.to());
        long failed = dashboardRepo.countOutboundMessagesByStatus(FAILED_STATUSES, pathType, window.from(), window.to());
        long openerTaps = dashboardRepo.countOpenerTaps(pathType, window.from(), window.to());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messagesSent", sent);
        data.put("messagesPerDay", Math.round((double) sent / window.numberOfDays()));
        data.put("messagesDelivered", delivered);
        data.put("messagesFailed", failed);
        data.put("deliveryRate", pct(delivered, sent));
        data.put("readReceipts", read);
        data.put("openRate", pct(read, delivered));
        data.put("openerTaps", openerTaps);
        data.put("clickRate", pct(openerTaps, delivered));
        data.put("flow", normaliseFlow(flow));
        data.put("range", normaliseRange(range));
        return data;
    }

    // ==================================================================
    // OUTCOMES
    // An outcome that doesn't exist on the selected path comes back with total = null,
    // so the card can show "—" (e.g. Buy-online clicks on "I'm celebrating").
    // ==================================================================

    @Override
    public Map<String, Object> getOutcomes(String flow, String range, String startDate, String endDate) {
        DateWindow window = resolveDateTimeRange(range, startDate, endDate);
        String pathType = flowToPathType(flow);
        boolean allPaths = pathType == null;

        boolean hasReferrals = !TEMPLATE_SPARKLE.equals(pathType);
        boolean hasIshara = allPaths || TEMPLATE_WIFE.equals(pathType);
        boolean hasBuyOnline = allPaths || TEMPLATE_HUSBAND.equals(pathType);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("referralLeads", hasReferrals ? buildReferralOutcome(pathType, window) : notApplicable());
        result.put("isharaSent", hasIshara ? buildOutcome(LEAD_HINT_SENT, pathType, window) : notApplicable());
        result.put("storeVisitsBooked", buildOutcome(LEAD_STORE_VISIT_BOOKED, pathType, window));
        result.put("buyOnlineClicks", hasBuyOnline ? buildOutcome(LEAD_BUY_ONLINE_CLICKED, pathType, window) : notApplicable());
        result.put("flow", normaliseFlow(flow));
        result.put("range", normaliseRange(range));
        return result;
    }

    private Map<String, Object> buildReferralOutcome(String pathType, DateWindow window) {
        long husbandsReferred = dashboardRepo.countReferralLeads(HUSBAND_REFERRAL, pathType, window.from(), window.to());
        long wivesReferred = dashboardRepo.countReferralLeads(WIFE_REFERRAL, pathType, window.from(), window.to());

        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("total", husbandsReferred + wivesReferred);
        outcome.put("husbandsReferred", husbandsReferred);
        outcome.put("wivesReferred", wivesReferred);
        outcome.put("trend", buildDailyTrend(
                dashboardRepo.findDailyReferralCounts(ALL_REFERRAL_TYPES, pathType, window.from(), window.to()), window));
        return outcome;
    }

    private Map<String, Object> buildOutcome(String leadType, String pathType, DateWindow window) {
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("total", dashboardRepo.countLeadsByType(leadType, pathType, window.from(), window.to()));
        outcome.put("trend", buildDailyTrend(
                dashboardRepo.findDailyLeadCounts(leadType, pathType, window.from(), window.to()), window));
        return outcome;
    }

    private Map<String, Object> notApplicable() {
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("total", null);
        outcome.put("trend", List.of());
        return outcome;
    }

    /** One point per day in the window — days with no leads get 0 so the sparkline has no gaps. */
    private List<Map<String, Object>> buildDailyTrend(List<DashboardRepository.DailyCountProjection> dailyCounts,
                                                      DateWindow window) {
        Map<String, Long> countByDay = new HashMap<>();
        for (DashboardRepository.DailyCountProjection dailyCount : dailyCounts) {
            countByDay.put(dailyCount.getDay(), valueOrZero(dailyCount.getTotal()));
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate day = window.from().toLocalDate(); day.isBefore(window.to().toLocalDate()); day = day.plusDays(1)) {
            String dayKey = day.format(ISO_DATE);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", dayKey);
            point.put("count", countByDay.getOrDefault(dayKey, 0L));
            trend.add(point);
        }
        return trend;
    }

    // ==================================================================
    // BOT FLOW DROP-OFF
    // Follows the sessions that STARTED in the window, so every stage is a share of "Opener".
    // Each path has its own stages, matching its journey in the flow script.
    // ==================================================================

    @Override
    public Map<String, Object> getFlowDropOff(String flow, String range, String startDate, String endDate) {
        DateWindow window = resolveDateTimeRange(range, startDate, endDate);
        String pathType = flowToPathType(flow);
        long openerTaps = dashboardRepo.countOpenerTaps(pathType, window.from(), window.to());

        List<Map<String, Object>> stages;
        if (pathType == null) {
            stages = buildAllPathsStages(window, openerTaps);
        } else {
            stages = switch (pathType) {
                case TEMPLATE_WIFE -> buildWifeStages(window, openerTaps);
                case TEMPLATE_HUSBAND -> buildHusbandStages(window, openerTaps);
                default -> buildSparkleStages(window, openerTaps);
            };
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stages", stages);
        result.put("flow", normaliseFlow(flow));
        result.put("range", normaliseRange(range));
        return result;
    }

    /** "I'm celebrating" — no budget step on this path. */
    private List<Map<String, Object>> buildWifeStages(DateWindow window, long openerTaps) {
        String wife = TEMPLATE_WIFE;
        LocalDateTime from = window.from();
        LocalDateTime to = window.to();

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(buildStage("OPENER", "Opener", openerTaps, openerTaps));
        stages.add(buildStage("CHOOSE_MY_GIFT", "Choose My Gift",
                dashboardRepo.countSessionsWithButtonTap(TAP_WIFE_CHOOSE_MY_GIFT, wife, from, to), openerTaps));
        stages.add(buildStage("CATEGORY", "Category",
                dashboardRepo.countSessionsWithButtonTapPrefix(TAP_CATEGORY_PREFIX, wife, from, to), openerTaps));
        stages.add(buildStage("CAROUSEL", "Carousel",
                dashboardRepo.countSessionsReachedStep(List.of(STEP_W_BROWSE_PRODUCTS), wife, from, to), openerTaps));
        stages.add(buildStage("ADDED_TO_LIST", "Added to List",
                dashboardRepo.countSessionsWithPickAction(ADDED_ACTION, wife, from, to), openerTaps));
        stages.add(buildStage("ISHARA_CREATED", "Ishara Created",
                dashboardRepo.countSessionsWithButtonTap(TAP_WIFE_SEND_ISHARA, wife, from, to), openerTaps));
        stages.add(buildStage("ISHARA_SENT", "Ishara Sent",
                dashboardRepo.countSessionsWithLead(List.of(LEAD_HINT_SENT), wife, from, to), openerTaps));
        stages.add(buildStage("HIS_DETAILS", "His Details",
                dashboardRepo.countSessionsReachedStep(List.of(STEP_W_CONSENT), wife, from, to), openerTaps));
        stages.add(buildStage("CONSENT", "Consent",
                dashboardRepo.countSessionsWithReferralLead(HUSBAND_REFERRAL, wife, from, to), openerTaps));
        return stages;
    }

    /** "Shopping for her" — category → budget → products → one piece → buy online → her details. */
    private List<Map<String, Object>> buildHusbandStages(DateWindow window, long openerTaps) {
        String husband = TEMPLATE_HUSBAND;
        LocalDateTime from = window.from();
        LocalDateTime to = window.to();

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(buildStage("OPENER", "Opener", openerTaps, openerTaps));
        stages.add(buildStage("FIND_HER_GIFT", "Find Her Gift",
                dashboardRepo.countSessionsWithButtonTap(TAP_HUSBAND_FIND_HER_GIFT, husband, from, to), openerTaps));
        stages.add(buildStage("CATEGORY", "Category",
                dashboardRepo.countSessionsWithButtonTapPrefix(TAP_CATEGORY_PREFIX, husband, from, to), openerTaps));
        stages.add(buildStage("BUDGET", "Budget",
                dashboardRepo.countSessionsWithButtonTapPrefix(TAP_HUSBAND_BUDGET_PREFIX, husband, from, to), openerTaps));
        stages.add(buildStage("CAROUSEL", "Carousel",
                dashboardRepo.countSessionsReachedStep(List.of(STEP_H_BROWSE_PRODUCTS), husband, from, to), openerTaps));
        stages.add(buildStage("CHOSE_PIECE", "Chose a Piece",
                dashboardRepo.countSessionsWithPickAction(SELECTED_ACTION, husband, from, to), openerTaps));
        stages.add(buildStage("BUY_ONLINE", "Buy Online",
                dashboardRepo.countSessionsWithLead(List.of(LEAD_BUY_ONLINE_CLICKED), husband, from, to), openerTaps));
        stages.add(buildStage("HER_DETAILS", "Her Details",
                dashboardRepo.countSessionsReachedStep(List.of(STEP_H_CONSENT), husband, from, to), openerTaps));
        stages.add(buildStage("CONSENT", "Consent",
                dashboardRepo.countSessionsWithReferralLead(WIFE_REFERRAL, husband, from, to), openerTaps));
        return stages;
    }

    /** "Here for the sparkle" — no Ishara and no partner details on this path. */
    private List<Map<String, Object>> buildSparkleStages(DateWindow window, long openerTaps) {
        String sparkle = TEMPLATE_SPARKLE;
        LocalDateTime from = window.from();
        LocalDateTime to = window.to();

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(buildStage("OPENER", "Opener", openerTaps, openerTaps));
        stages.add(buildStage("SHOW_ME", "Show Me",
                dashboardRepo.countSessionsWithButtonTap(TAP_SPARKLE_SHOW_ME, sparkle, from, to), openerTaps));
        stages.add(buildStage("CATEGORY", "Category",
                dashboardRepo.countSessionsWithButtonTapPrefix(TAP_CATEGORY_PREFIX, sparkle, from, to), openerTaps));
        stages.add(buildStage("BUDGET", "Budget",
                dashboardRepo.countSessionsWithButtonTapPrefix(TAP_SPARKLE_BUDGET_PREFIX, sparkle, from, to), openerTaps));
        stages.add(buildStage("CAROUSEL", "Carousel",
                dashboardRepo.countSessionsReachedStep(List.of(STEP_S_BROWSE_PRODUCTS), sparkle, from, to), openerTaps));
        stages.add(buildStage("ADDED_TO_LIST", "Added to List",
                dashboardRepo.countSessionsWithPickAction(ADDED_ACTION, sparkle, from, to), openerTaps));
        stages.add(buildStage("STORE_VISIT_BOOKED", "Store Visit Booked",
                dashboardRepo.countSessionsWithLead(List.of(LEAD_STORE_VISIT_BOOKED), sparkle, from, to), openerTaps));
        stages.add(buildStage("KEEP_ME_POSTED", "Keep Me Posted",
                dashboardRepo.countSessionsWithLead(List.of(LEAD_SPARKLE_OPT_IN), sparkle, from, to), openerTaps));
        return stages;
    }

    /**
     * "All paths" — the shared stages. Budget is asked only on the Husband and Sparkle paths,
     * and the Ishara only on the Wife path, so those two bars are a share of ALL opener taps.
     */
    private List<Map<String, Object>> buildAllPathsStages(DateWindow window, long openerTaps) {
        LocalDateTime from = window.from();
        LocalDateTime to = window.to();

        // A session belongs to one path, so the two budget counts never overlap
        long budget = dashboardRepo.countSessionsWithButtonTapPrefix(TAP_HUSBAND_BUDGET_PREFIX, TEMPLATE_HUSBAND, from, to)
                + dashboardRepo.countSessionsWithButtonTapPrefix(TAP_SPARKLE_BUDGET_PREFIX, TEMPLATE_SPARKLE, from, to);

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(buildStage("OPENER_TAPPED", "Opener Tapped", openerTaps, openerTaps));
        stages.add(buildStage("CATEGORY", "Category",
                dashboardRepo.countSessionsWithButtonTapPrefix(TAP_CATEGORY_PREFIX, null, from, to), openerTaps));
        stages.add(buildStage("BUDGET", "Budget", budget, openerTaps));
        stages.add(buildStage("CAROUSEL", "Carousel",
                dashboardRepo.countSessionsReachedStep(ALL_CAROUSEL_STEPS, null, from, to), openerTaps));
        stages.add(buildStage("CHOSE_PIECE", "Added / Chose Piece",
                dashboardRepo.countSessionsWithPickAction(CHOSE_PIECE_ACTIONS, null, from, to), openerTaps));
        stages.add(buildStage("ISHARA_SENT", "Ishara Sent",
                dashboardRepo.countSessionsWithLead(List.of(LEAD_HINT_SENT), null, from, to), openerTaps));
        stages.add(buildStage("STORE_VISIT_BOOKED", "Store Visit Booked",
                dashboardRepo.countSessionsWithLead(List.of(LEAD_STORE_VISIT_BOOKED), null, from, to), openerTaps));
        stages.add(buildStage("REFERRAL_LEADS", "Referral Leads",
                dashboardRepo.countSessionsWithReferralLead(ALL_REFERRAL_TYPES, null, from, to), openerTaps));
        return stages;
    }

    private Map<String, Object> buildStage(String step, String label, long count, long openerTaps) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("step", step);
        stage.put("label", label);
        stage.put("count", count);
        stage.put("pct", pctTwoDecimals(count, openerTaps));
        return stage;
    }

    // ==================================================================
    // LIVE ACTIVITY
    // ==================================================================

    @Override
    public Map<String, Object> getActivity(String flow, String range, String startDate, String endDate, int limit) {
        DateWindow window = resolveDateTimeRange(range, startDate, endDate);
        String pathType = flowToPathType(flow);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_ACTIVITY_LIMIT);

        List<DashboardRepository.ActivityRowProjection> rows =
                dashboardRepo.findRecentActivity(CHOSE_PIECE_ACTIONS, pathType, window.from(), window.to(), safeLimit);

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> events = new ArrayList<>();
        for (DashboardRepository.ActivityRowProjection row : rows) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("badge", activityBadge(row));
            event.put("summary", summarizeActivityRow(row));
            event.put("path", row.getPath());
            event.put("timestamp", row.getEventTime());
            event.put("minutesAgo", row.getEventTime() == null ? null : ChronoUnit.MINUTES.between(row.getEventTime(), now));
            events.add(event);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("events", events);
        result.put("flow", normaliseFlow(flow));
        return result;
    }

    private String activityBadge(DashboardRepository.ActivityRowProjection row) {
        if ("PICK".equals(row.getSource())) {
            return "MSG";
        }
        return switch (row.getEventType()) {
            case LEAD_HUSBAND_CAPTURED, LEAD_WIFE_CAPTURED -> "REFERRAL";
            case LEAD_HINT_SENT -> "ISHARA";
            case LEAD_STORE_VISIT_BOOKED -> "VISIT";
            case LEAD_BUY_ONLINE_CLICKED -> "BUY ONLINE";
            case LEAD_SPARKLE_OPT_IN -> "OPT-IN";
            case LEAD_SPARKLE_OPT_OUT -> "OPT-OUT";
            default -> "LEAD";
        };
    }

    private String summarizeActivityRow(DashboardRepository.ActivityRowProjection row) {
        String customer = displayName(row.getCustomerName(), row.getPhone());

        if ("PICK".equals(row.getSource())) {
            String piece = hasText(row.getProductName()) ? row.getProductName() : row.getSku();
            return PICK_ADDED.equals(row.getEventType())
                    ? customer + " added " + piece + " to her list"
                    : customer + " picked " + piece;
        }

        String mobileText = hasText(row.getPartnerPhone()) ? " + mobile" : "";
        String consentText = consentText(row.getConsent());
        String storeText = hasText(row.getStoreName()) ? " · " + row.getStoreName() : "";

        return switch (row.getEventType()) {
            case LEAD_HUSBAND_CAPTURED -> customer + " referred her husband · anniversary" + mobileText + consentText;
            case LEAD_WIFE_CAPTURED -> customer + " referred his wife · birthday" + mobileText + consentText;
            case LEAD_HINT_SENT -> customer + " created her Ishara for her husband";
            case LEAD_STORE_VISIT_BOOKED -> customer + " booked a visit" + storeText;
            case LEAD_BUY_ONLINE_CLICKED -> customer + " asked for the Buy Online link";
            case LEAD_SPARKLE_OPT_IN -> customer + " asked to be kept posted";
            case LEAD_SPARKLE_OPT_OUT -> customer + " said no to updates";
            default -> customer + " · " + row.getEventType();
        };
    }

    private String consentText(String consent) {
        if (CONSENT_YES.equalsIgnoreCase(consent)) return ", consent yes";
        if (CONSENT_NO.equalsIgnoreCase(consent)) return ", consent no";
        return "";
    }

    /** Customer's name if we have it, otherwise a masked phone number. */
    private String displayName(String customerName, String phone) {
        if (hasText(customerName)) return customerName;
        if (phone == null || phone.length() < 4) return "A customer";
        return "******" + phone.substring(phone.length() - 4);
    }

    // ==================================================================
    // THE THREE PATHS
    // ==================================================================

    @Override
    public Map<String, Object> getPathsSummary(String range, String startDate, String endDate) {
        DateWindow window = resolveDateTimeRange(range, startDate, endDate);
        LocalDateTime from = window.from();
        LocalDateTime to = window.to();

        Map<String, Long> openerTapsByPath = new HashMap<>();
        for (DashboardRepository.PathCountProjection pathCount : dashboardRepo.countOpenerTapsByPath(from, to)) {
            openerTapsByPath.put(pathCount.getPath(), valueOrZero(pathCount.getTotal()));
        }
        long totalOpenerTaps = openerTapsByPath.values().stream().mapToLong(Long::longValue).sum();

        // I'm celebrating (Wife)
        Map<String, Object> celebrating = buildPathCard("celebrating",
                openerTapsByPath.getOrDefault(TEMPLATE_WIFE, 0L), totalOpenerTaps);
        celebrating.put("isharaSent", dashboardRepo.countLeadsByType(LEAD_HINT_SENT, TEMPLATE_WIFE, from, to));
        celebrating.put("visitsBooked", dashboardRepo.countLeadsByType(LEAD_STORE_VISIT_BOOKED, TEMPLATE_WIFE, from, to));
        celebrating.put("husbandsReferred", dashboardRepo.countReferralLeads(HUSBAND_REFERRAL, TEMPLATE_WIFE, from, to));

        // Shopping for her (Husband)
        Map<String, Object> shopping = buildPathCard("shopping",
                openerTapsByPath.getOrDefault(TEMPLATE_HUSBAND, 0L), totalOpenerTaps);
        shopping.put("buyOnlineClicks", dashboardRepo.countLeadsByType(LEAD_BUY_ONLINE_CLICKED, TEMPLATE_HUSBAND, from, to));
        shopping.put("visitsBooked", dashboardRepo.countLeadsByType(LEAD_STORE_VISIT_BOOKED, TEMPLATE_HUSBAND, from, to));
        shopping.put("wivesReferred", dashboardRepo.countReferralLeads(WIFE_REFERRAL, TEMPLATE_HUSBAND, from, to));

        // Here for the sparkle
        Map<String, Object> sparkle = buildPathCard("sparkle",
                openerTapsByPath.getOrDefault(TEMPLATE_SPARKLE, 0L), totalOpenerTaps);
        sparkle.put("piecesShortlisted", dashboardRepo.countPiecesByPickAction(PICK_ADDED, TEMPLATE_SPARKLE, from, to));
        sparkle.put("visitsBooked", dashboardRepo.countLeadsByType(LEAD_STORE_VISIT_BOOKED, TEMPLATE_SPARKLE, from, to));
        sparkle.put("keepMePosted", dashboardRepo.countLeadsByType(LEAD_SPARKLE_OPT_IN, TEMPLATE_SPARKLE, from, to));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOpenerTaps", totalOpenerTaps);
        result.put("paths", List.of(celebrating, shopping, sparkle));
        result.put("range", normaliseRange(range));
        return result;
    }

    private Map<String, Object> buildPathCard(String flow, long openerTaps, long totalOpenerTaps) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("flow", flow);
        card.put("openerTaps", openerTaps);
        card.put("share", pct(openerTaps, totalOpenerTaps));
        return card;
    }

    // ==================================================================
    // HELPERS
    // ==================================================================

    /** Frontend tab → value stored in the path column. "all" means no filter. */
    private String flowToPathType(String flow) {
        if (flow == null || flow.isBlank()) return null;
        return switch (flow.trim().toLowerCase()) {
            case "all" -> null;
            case "celebrating", "wife" -> TEMPLATE_WIFE;
            case "shopping", "husband" -> TEMPLATE_HUSBAND;
            case "sparkle" -> TEMPLATE_SPARKLE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "flow must be all, celebrating, shopping or sparkle");
        };
    }

    private record DateWindow(LocalDateTime from, LocalDateTime to) {
        long numberOfDays() {
            return Math.max(ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()), 1);
        }
    }

    /**
     * Uses the server's clock, the same clock the entities use for createdAt / startedAt —
     * so "today" lines up with the stored timestamps. Run the app in IST (see notes).
     */
    private DateWindow resolveDateTimeRange(String range, String startDate, String endDate) {
        LocalDate today = LocalDate.now();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();

        return switch (normaliseRange(range)) {
            case "today" -> new DateWindow(today.atStartOfDay(), endOfToday);
            case "7d" -> new DateWindow(today.minusDays(6).atStartOfDay(), endOfToday);
            case "30d" -> new DateWindow(today.minusDays(29).atStartOfDay(), endOfToday);
            case "custom" -> resolveCustomRange(startDate, endDate);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "range must be today, 7d, 30d or custom");
        };
    }

    private DateWindow resolveCustomRange(String startDate, String endDate) {
        if (!hasText(startDate) || !hasText(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "custom range needs both from and to");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate.trim(), ISO_DATE);
            end = LocalDate.parse(endDate.trim(), ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to must be in yyyy-MM-dd format");
        }
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
        }
        return new DateWindow(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    private String normaliseRange(String range) {
        return (range == null || range.isBlank()) ? "today" : range.trim().toLowerCase();
    }

    private String normaliseFlow(String flow) {
        return (flow == null || flow.isBlank()) ? "all" : flow.trim().toLowerCase();
    }

    /** Percentage with one decimal, e.g. 94.3 */
    private double pct(long part, long total) {
        if (total <= 0) return 0.0;
        return Math.round((part * 1000.0) / total) / 10.0;
    }

    /** Percentage with two decimals, e.g. 66.16 — used by the drop-off bars. */
    private double pctTwoDecimals(long part, long total) {
        if (total <= 0) return 0.0;
        return Math.round((part * 10000.0) / total) / 100.0;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}