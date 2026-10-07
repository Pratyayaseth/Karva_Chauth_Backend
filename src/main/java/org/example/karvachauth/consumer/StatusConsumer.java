//package org.example.karvachauth.consumer;
//
//import com.azure.messaging.servicebus.ServiceBusClientBuilder;
//import com.azure.messaging.servicebus.ServiceBusErrorContext;
//import com.azure.messaging.servicebus.ServiceBusProcessorClient;
//import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.annotation.PreDestroy;
//import lombok.extern.slf4j.Slf4j;
//import org.example.karvachauth.entity.Message;
//import org.example.karvachauth.repository.MessageRepository;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.boot.context.event.ApplicationReadyEvent;
//import org.springframework.context.event.EventListener;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.Map;
//import java.util.Optional;
//
//import static jdk.vm.ci.code.CodeUtil.mask;
//import static org.example.karvachauth.constants.KarvaChauthConstants.*;
//import static org.example.karvachauth.karix.KarixPayloadParser.digits;
//import static org.example.karvachauth.karix.KarixPayloadParser.mask;
//import static org.example.karvachauth.karix.KarixPayloadParser.text;
//
///**
// * Reads delivery receipts from the "karvachauth-status" queue and updates the messages table:
// * SENT → DELIVERED → READ, or FAILED (with Karix's error code / reason).
// *
// * Also records the Step 0 campaign template (sent from the Karix portal, not by us) the first
// * time a receipt for it arrives — so the dashboard can show campaign sent / delivered / read.
// *
// * Off in LOCAL mode (karvachauth.servicebus.enabled=false).
// */
//@Slf4j
//@Component
//@ConditionalOnProperty(name = "karvachauth.servicebus.enabled", havingValue = "true", matchIfMissing = true)
//public class StatusConsumer {
//
//    /** Status order — a late "delivered" must never overwrite "read". */
//    private static final Map<String, Integer> RANK = Map.of(
//            MSG_STATUS_SENT, 1,
//            MSG_STATUS_DELIVERED, 2,
//            MSG_STATUS_READ, 3,
//            MSG_STATUS_FAILED, 4);
//
//    private final MessageRepository messageRepository;
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    private final String connectionString;
//    private final String queueName;
//    private final int maxConcurrentCalls;
//
//    private ServiceBusProcessorClient processor;
//
//    public StatusConsumer(
//            MessageRepository messageRepository,
//            @Value("${karvachauth.servicebus.connection-string}") String connectionString,
//            @Value("${karvachauth.servicebus.status-queue:karvachauth-status}") String queueName,
//            @Value("${karvachauth.consumer.status-max-concurrent-calls:8}") int maxConcurrentCalls) {
//        this.messageRepository = messageRepository;
//        this.connectionString = connectionString;
//        this.queueName = queueName;
//        this.maxConcurrentCalls = maxConcurrentCalls;
//    }
//
//    @EventListener(ApplicationReadyEvent.class)
//    public void start() {
//        processor = new ServiceBusClientBuilder()
//                .connectionString(connectionString)
//                .processor()                           // status queue has NO sessions
//                .queueName(queueName)
//                .maxConcurrentCalls(maxConcurrentCalls)
//                .disableAutoComplete()
//                .processMessage(this::onMessage)
//                .processError(this::onError)
//                .buildProcessorClient();
//        processor.start();
//        log.info("stage=STATUS_CONSUMER_STARTED queue={} maxConcurrentCalls={}", queueName, maxConcurrentCalls);
//    }
//
//    @PreDestroy
//    public void stop() {
//        if (processor != null) {
//            processor.close();
//            log.info("stage=STATUS_CONSUMER_STOPPED");
//        }
//    }
//
//    private void onMessage(ServiceBusReceivedMessageContext ctx) {
//        JsonNode root;
//        try {
//            root = objectMapper.readTree(ctx.getMessage().getBody().toString());
//        } catch (Exception e) {
//            log.error("stage=STATUS_BAD_JSON — dead-lettering", e);
//            ctx.deadLetter();
//            return;
//        }
//
//        try {
//            JsonNode attrs = root.path("notificationAttributes");
//            String mid        = text(root.path("events"), "mid");
//            String rawStatus  = text(attrs, "status");
//            String code       = text(attrs, "code");
//            String reason     = text(attrs, "reason");
//            String phone      = digits(text(root.path("recipient"), "to"));
//            String templateId = text(root, "templateId");
//
//            String status = mapStatus(rawStatus);
//            if (mid == null || status == null) {
//                log.warn("stage=STATUS_SKIPPED mid={} rawStatus={} — missing id or unknown status", mid, rawStatus);
//                ctx.complete();
//                return;
//            }
//
//            Optional<Message> found = messageRepository.findFirstByKarixMessageId(mid);
//            if (found.isPresent()) {
//                updateExisting(found.get(), status, code, reason);
//            } else if (templateId != null && phone != null) {
//                recordCampaignMessage(mid, phone, templateId, status, code, reason);
//            } else {
//                // Usually a message sent outside this bot. (Rarely: the receipt beat our own insert.)
//                log.info("stage=STATUS_NO_MATCH mid={} status={}", mid, status);
//            }
//            ctx.complete();
//
//        } catch (Exception e) {
//            log.error("stage=STATUS_FAILED — abandoning for retry", e);
//            ctx.abandon();
//        }
//    }
//
//    /** Moves a message we sent forward: SENT → DELIVERED → READ (never backwards), or FAILED. */
//    private void updateExisting(Message message, String status, String code, String reason) {
//        int current = RANK.getOrDefault(message.getStatus(), 0);
//        int incoming = RANK.get(status);
//        if (incoming <= current) {
//            log.debug("stage=STATUS_OLDER_IGNORED mid={} current={} incoming={}",
//                    message.getKarixMessageId(), message.getStatus(), status);
//            return;
//        }
//        message.setStatus(status);
//        message.setStatusUpdatedAt(LocalDateTime.now());
//        if (MSG_STATUS_FAILED.equals(status)) {
//            message.setErrorCode(code);
//            message.setErrorReason(reason == null ? null : reason.substring(0, Math.min(reason.length(), 500)));
//        }
//        messageRepository.save(message);
//        log.info("stage=STATUS_UPDATED mid={} phone={} status={} code={}",
//                message.getKarixMessageId(), mask(message.getPhone()), status, code);
//    }
//
//    /** First receipt for a Step 0 campaign template (sent from the Karix portal) → one row per recipient. */
//    private void recordCampaignMessage(String mid, String phone, String templateId,
//                                       String status, String code, String reason) {
//        messageRepository.save(Message.builder()
//                .phone(phone)
//                .direction(DIR_OUTBOUND)
//                .karixMessageId(mid)
//                .status(status)
//                .messageType("template")
//                .templateName(templateId)
//                .errorCode(MSG_STATUS_FAILED.equals(status) ? code : null)
//                .errorReason(MSG_STATUS_FAILED.equals(status) ? reason : null)
//                .statusUpdatedAt(LocalDateTime.now())
//                .build());
//        log.info("stage=CAMPAIGN_MESSAGE_RECORDED mid={} phone={} template={} status={}",
//                mid, mask(phone), templateId, status);
//    }
//
//    /** Karix status → our status. Null for anything we don't track. */
//    private static String mapStatus(String raw) {
//        if (raw == null) return null;
//        return switch (raw.trim().toLowerCase()) {
//            case "sent", "submitted", "accepted"            -> MSG_STATUS_SENT;
//            case "delivered"                                -> MSG_STATUS_DELIVERED;
//            case "read", "seen"                             -> MSG_STATUS_READ;
//            case "failed", "undelivered", "rejected", "error" -> MSG_STATUS_FAILED;
//            default -> null;
//        };
//    }
//
//    private void onError(ServiceBusErrorContext ctx) {
//        log.error("stage=STATUS_CONSUMER_ERROR source={} entity={}",
//                ctx.getErrorSource(), ctx.getEntityPath(), ctx.getException());
//    }
//}