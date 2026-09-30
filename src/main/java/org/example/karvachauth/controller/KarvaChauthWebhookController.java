package org.example.karvachauth.controller;

import org.example.karvachauth.service.KarvaChauthWebhookService;
//import org.example.karvachauth.service.KarvaChauthWebhookService.WebhookResult;
import org.example.karvachauth.service.KarvaChauthWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Karix webhook for Karva Chauth campaign.
 * Public URL : https://karvachauth-rlai.indiasouthcentral.cloudapp.azure.com/karvachauth-webhook
 * Internal   : POST http://localhost:8086/webhook/karvachauth
 *
 * Thin controller: read raw body + token -> service -> return quickly.
 */
@RestController
@RequestMapping("/webhook/karvachauth")
public class KarvaChauthWebhookController {

    private static final Logger log = LoggerFactory.getLogger(KarvaChauthWebhookController.class);
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{16,36}$");

    private final KarvaChauthWebhookService webhookService;

    public KarvaChauthWebhookController(KarvaChauthWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /** Raw String body: a Karix payload change never causes a 400, exact payload is kept. */
    @PostMapping(consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> receiveWebhook(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "X-Webhook-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestHeader(value = "X-Request-Id", required = false) String nginxRequestId,
            @RequestHeader(value = "X-Real-IP", required = false) String sourceIp) {

        // Correlation id = nginx $request_id (same id in nginx log + app log + DB)
        String eventId = (nginxRequestId != null && SAFE_ID.matcher(nginxRequestId).matches())
                ? nginxRequestId
                : UUID.randomUUID().toString();

        MDC.put("eventId", eventId);
        try {
            log.info("stage=HTTP_RECEIVED eventId={} sourceIp={} bytes={}",
                    eventId, sourceIp, rawBody == null ? 0 : rawBody.length());

            String token = headerToken != null ? headerToken : queryToken;
            KarvaChauthWebhookService.WebhookResult result = webhookService.handleWebhook(eventId, rawBody, token);

            log.info("stage=HTTP_RESPONDED eventId={} httpStatus={} result={}",
                    eventId, result.getHttpStatus().value(), result.name());

            return ResponseEntity.status(result.getHttpStatus())
                    .header("X-Request-Id", eventId)
                    .body(Map.of("status", result.name(), "eventId", eventId));

        } catch (Exception e) {
            log.error("stage=FAILED failedStage=CONTROLLER eventId={} error={}", eventId, e.getMessage(), e);
            return ResponseEntity.status(503)
                    .header("X-Request-Id", eventId)
                    .body(Map.of("status", "UNAVAILABLE", "eventId", eventId));
        } finally {
            MDC.clear();   // Tomcat threads are reused
        }
    }

    /** Lightweight health check, no DB / Service Bus call. */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "karvachauth-webhook");
    }
}