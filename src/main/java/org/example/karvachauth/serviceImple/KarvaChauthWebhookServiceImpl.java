package org.example.karvachauth.serviceImple;//package org.example.karvachauth.serviceImpl;

import com.azure.core.amqp.AmqpRetryMode;
import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.example.karvachauth.service.KarvaChauthWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Runs on the request thread (NO runAsync): token check + parse (~1 ms) + Service Bus send -> 200.
 * karvachauth.servicebus.enabled=false -> LOCAL mode (no Service Bus). Never false in production.
 */
@Service
public class KarvaChauthWebhookServiceImpl implements KarvaChauthWebhookService {

    private static final Logger log = LoggerFactory.getLogger(KarvaChauthWebhookServiceImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();   // Spring Boot 4 has no Jackson-2 bean
    private final boolean serviceBusEnabled;
    private final ServiceBusSenderClient inboundSender;   // user messages (sessions, SessionId = mobile)
    private final ServiceBusSenderClient statusSender;    // delivery/read receipts
    private final String webhookToken;
    private final boolean authEnabled;
    private final int maxBodyBytes;

    public KarvaChauthWebhookServiceImpl(
            @Value("${karvachauth.servicebus.enabled:true}") boolean serviceBusEnabled,
            @Value("${karvachauth.servicebus.connection-string:}") String sbConnectionString,
            @Value("${karvachauth.servicebus.inbound-queue:karvachauth-inbound}") String inboundQueue,
            @Value("${karvachauth.servicebus.status-queue:karvachauth-status}") String statusQueue,
            @Value("${karvachauth.webhook.auth-token:}") String webhookToken,
            @Value("${karvachauth.webhook.auth-enabled:true}") boolean authEnabled,
            @Value("${karvachauth.webhook.max-body-bytes:262144}") int maxBodyBytes) {

        this.serviceBusEnabled = serviceBusEnabled;
        this.webhookToken = webhookToken;
        this.authEnabled = authEnabled;
        this.maxBodyBytes = maxBodyBytes;

        if (!serviceBusEnabled) {
            this.inboundSender = null;
            this.statusSender = null;
            log.warn("karvachauth.servicebus.enabled=false -> LOCAL MODE: events will NOT be queued. Do not use in production.");
            return;
        }
        if (sbConnectionString == null || sbConnectionString.isBlank()) {
            throw new IllegalStateException(
                    "karvachauth.servicebus.connection-string is empty. Set it, or set karvachauth.servicebus.enabled=false for local testing.");
        }

        AmqpRetryOptions retry = new AmqpRetryOptions()
                .setMode(AmqpRetryMode.EXPONENTIAL)
                .setMaxRetries(2)
                .setDelay(Duration.ofMillis(200))
                .setMaxDelay(Duration.ofSeconds(1))
                .setTryTimeout(Duration.ofSeconds(3));

        ServiceBusClientBuilder builder = new ServiceBusClientBuilder()
                .connectionString(sbConnectionString)
                .retryOptions(retry);

        this.inboundSender = builder.sender().queueName(inboundQueue).buildClient();
        this.statusSender = builder.sender().queueName(statusQueue).buildClient();
        log.info("Service Bus senders ready: inbound={}, status={}", inboundQueue, statusQueue);
    }

    @Override
    public WebhookResult handleWebhook(String eventId, String rawBody, String token) {
        long start = System.nanoTime();
        int bytes = rawBody == null ? 0 : rawBody.getBytes(StandardCharsets.UTF_8).length;
        log.info("stage=WEBHOOK_RECEIVED eventId={} bytes={}", eventId, bytes);

        // ---------- 1. VALIDATE ----------
        if (!isAuthorized(token)) {
            log.warn("stage=REJECTED eventId={} reason=INVALID_TOKEN elapsedMs={}", eventId, ms(start));
            return WebhookResult.UNAUTHORIZED;
        }
        if (bytes == 0 || bytes > maxBodyBytes) {
            log.warn("stage=REJECTED eventId={} reason=BAD_SIZE bytes={}", eventId, bytes);
            return WebhookResult.IGNORED;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("stage=REJECTED eventId={} reason=NOT_JSON", eventId);
            return WebhookResult.IGNORED;
        }
        if (root == null || !root.isObject()) {
            log.warn("stage=REJECTED eventId={} reason=NOT_JSON_OBJECT", eventId);
            return WebhookResult.IGNORED;
        }

        // ---------- 2. IDENTIFY EVENT (real Karix format) ----------
        String eventType;
        String providerMessageId;
        String mobile;
        String dedupeSource;
        String messageType = null;
        String ctaPayload = null;
        String repliedToMid = null;
        String deliveryStatus = null;
        String templateId = null;

        JsonNode message = root.path("eventContent").path("message");
        JsonNode notification = root.path("notificationAttributes");

        if (message.isObject()) {
            eventType = "INBOUND_MESSAGE";
            providerMessageId = text(message, "id");
            mobile = digits(text(message, "from"));
            messageType = text(message, "messageType");
            repliedToMid = text(message.path("context"), "id");
            if ("button".equals(messageType)) {
                ctaPayload = text(message.path("button"), "payload");
            }
            dedupeSource = "IN|" + (providerMessageId != null ? providerMessageId : rawBody);
        } else if (notification.isObject()) {
            eventType = "DELIVERY_STATUS";
            providerMessageId = text(root.path("events"), "mid");
            mobile = digits(text(root.path("recipient"), "to"));
            deliveryStatus = text(notification, "status");
            templateId = text(root, "templateId");
            dedupeSource = "ST|" + (providerMessageId != null ? providerMessageId + "|" + deliveryStatus : rawBody);
        } else {
            eventType = "UNKNOWN";
            providerMessageId = null;
            mobile = null;
            dedupeSource = "UN|" + rawBody;
        }

        log.info("stage=VALIDATED eventId={} eventType={} messageType={} providerMessageId={} mobile={} elapsedMs={}",
                eventId, eventType, messageType, providerMessageId, mask(mobile), ms(start));

        if (ctaPayload != null) {
            log.info("stage=CTA_CLICKED eventId={} mobile={} ctaPayload={} repliedToMid={}",
                    eventId, mask(mobile), ctaPayload, repliedToMid);
        }
        if (deliveryStatus != null) {
            log.info("stage=DELIVERY_STATUS eventId={} mobile={} mid={} status={} code={} templateId={}",
                    eventId, mask(mobile), providerMessageId, deliveryStatus, text(notification, "code"), templateId);
        }

        // ---------- 3. DUPLICATE KEY (Service Bus MessageId -> duplicate detection) ----------
        String dedupeKey = sha256(dedupeSource);
        log.info("stage=DUPLICATE_CHECK eventId={} dedupeKey={}", eventId, dedupeKey);

        // ---------- 4. PUBLISH TO SERVICE BUS ----------
        if (!serviceBusEnabled) {
            log.info("stage=QUEUE_SKIPPED_LOCAL eventId={} eventType={} totalMs={}", eventId, eventType, ms(start));
            return WebhookResult.ACCEPTED;
        }
        try {
            ServiceBusMessage sbMessage = new ServiceBusMessage(rawBody);
            sbMessage.setMessageId(dedupeKey);
            sbMessage.setCorrelationId(eventId);
            sbMessage.setContentType("application/json");
            sbMessage.setSubject(eventType);
            sbMessage.getApplicationProperties().put("eventId", eventId);
            sbMessage.getApplicationProperties().put("eventType", eventType);
            sbMessage.getApplicationProperties().put("receivedAtEpochMs", System.currentTimeMillis());

            if ("INBOUND_MESSAGE".equals(eventType)) {
                sbMessage.setSessionId(mobile != null ? mobile : "unknown");
                inboundSender.sendMessage(sbMessage);
            } else {
                statusSender.sendMessage(sbMessage);
            }
            log.info("stage=QUEUE_PUBLISHED eventId={} eventType={} totalMs={}", eventId, eventType, ms(start));
            return WebhookResult.ACCEPTED;
        } catch (Exception e) {
            log.error("stage=FAILED failedStage=QUEUE_PUBLISH eventId={} error={} totalMs={}",
                    eventId, e.getMessage(), ms(start), e);
            return WebhookResult.UNAVAILABLE;
        }
    }

    // ---------------- helpers ----------------

    private boolean isAuthorized(String presented) {
        if (!authEnabled) return true;
        if (webhookToken == null || webhookToken.isBlank() || presented == null) return false;
        return MessageDigest.isEqual(webhookToken.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? null : v.asText(null);
    }

    private static String digits(String s) {
        if (s == null) return null;
        String d = s.replaceAll("[^0-9]", "");
        return (d.length() >= 10 && d.length() <= 15) ? d : null;
    }

    private static String mask(String mobile) {
        if (mobile == null || mobile.length() < 6) return "****";
        return mobile.substring(0, 2) + "******" + mobile.substring(mobile.length() - 4);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @PreDestroy
    public void close() {
        if (inboundSender != null) inboundSender.close();
        if (statusSender != null) statusSender.close();
    }
}