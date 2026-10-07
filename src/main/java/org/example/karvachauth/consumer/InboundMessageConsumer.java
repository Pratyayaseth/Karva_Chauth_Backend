//package org.example.karvachauth.consumer;
//
//import com.azure.messaging.servicebus.ServiceBusClientBuilder;
//import com.azure.messaging.servicebus.ServiceBusErrorContext;
//import com.azure.messaging.servicebus.ServiceBusProcessorClient;
//import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
//import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.annotation.PreDestroy;
//import lombok.extern.slf4j.Slf4j;
//import org.example.karvachauth.entity.ProcessedMessage;
//import org.example.karvachauth.karix.KarixPayloadParser;
//import org.example.karvachauth.model.InboundEvent;
//import org.example.karvachauth.repository.ProcessedMessageRepository;
//import org.example.karvachauth.service.BotEngineService;
//import org.slf4j.MDC;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.boot.context.event.ApplicationReadyEvent;
//import org.springframework.context.event.EventListener;
//import org.springframework.dao.DataIntegrityViolationException;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.Arrays;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//import static org.example.karvachauth.karix.KarixPayloadParser.mask;
//
///**
// * Reads customer messages from the "karvachauth-inbound" Service Bus queue and hands them to the bot.
// *
// *   Karix → webhook → Service Bus (SessionId = mobile) → THIS → BotEngineService
// *
// * The queue is session-enabled with SessionId = mobile, so one customer's messages are processed
// * one at a time and in order, while different customers are processed in parallel.
// *
// * Off in LOCAL mode (karvachauth.servicebus.enabled=false) — use /test there.
// */
//@Slf4j
//@Component
//@ConditionalOnProperty(name = "karvachauth.servicebus.enabled", havingValue = "true", matchIfMissing = true)
//public class InboundMessageConsumer {
//
//    private final BotEngineService botEngineService;
//    private final KarixPayloadParser parser;
//    private final ProcessedMessageRepository processedMessageRepository;
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    private final String connectionString;
//    private final String queueName;
//    private final int maxConcurrentSessions;
//
//    /** Step 0 template button payloads — set these to whatever the Mia team puts on the template. */
//    private final Set<String> wifePayloads;
//    private final Set<String> husbandPayloads;
//    private final Set<String> sparklePayloads;
//
//    private ServiceBusProcessorClient processor;
//
//    public InboundMessageConsumer(
//            BotEngineService botEngineService,
//            KarixPayloadParser parser,
//            ProcessedMessageRepository processedMessageRepository,
//            @Value("${karvachauth.servicebus.connection-string}") String connectionString,
//            @Value("${karvachauth.servicebus.inbound-queue:karvachauth-inbound}") String queueName,
//            @Value("${karvachauth.consumer.max-concurrent-sessions:16}") int maxConcurrentSessions,
//            @Value("${karvachauth.step0.wife-payloads:BTN_CELEBRATING}") String wifePayloads,
//            @Value("${karvachauth.step0.husband-payloads:BTN_SHOPPING_FOR_HER}") String husbandPayloads,
//            @Value("${karvachauth.step0.sparkle-payloads:BTN_SPARKLE}") String sparklePayloads) {
//        this.botEngineService = botEngineService;
//        this.parser = parser;
//        this.processedMessageRepository = processedMessageRepository;
//        this.connectionString = connectionString;
//        this.queueName = queueName;
//        this.maxConcurrentSessions = maxConcurrentSessions;
//        this.wifePayloads = toSet(wifePayloads);
//        this.husbandPayloads = toSet(husbandPayloads);
//        this.sparklePayloads = toSet(sparklePayloads);
//    }
//
//    // ==================================================================
//    // START / STOP
//    // ==================================================================
//
//    /** Starts only once the whole app is up — never processes a message before the bot is ready. */
//    @EventListener(ApplicationReadyEvent.class)
//    public void start() {
//        processor = new ServiceBusClientBuilder()
//                .connectionString(connectionString)
//                .sessionProcessor()
//                .queueName(queueName)
//                .maxConcurrentSessions(maxConcurrentSessions)
//                .disableAutoComplete()                 // we complete only after the bot has handled it
//                .processMessage(this::onMessage)
//                .processError(this::onError)
//                .buildProcessorClient();
//        processor.start();
//        log.info("stage=INBOUND_CONSUMER_STARTED queue={} maxConcurrentSessions={}", queueName, maxConcurrentSessions);
//    }
//
//    @PreDestroy
//    public void stop() {
//        if (processor != null) {
//            processor.close();
//            log.info("stage=INBOUND_CONSUMER_STOPPED");
//        }
//    }
//
//    // ==================================================================
//    // ONE MESSAGE
//    // ==================================================================
//
//    private void onMessage(ServiceBusReceivedMessageContext ctx) {
//        ServiceBusReceivedMessage sbMessage = ctx.getMessage();
//        Object eventIdProp = sbMessage.getApplicationProperties().get("eventId");
//        String eventId = eventIdProp != null ? eventIdProp.toString() : sbMessage.getMessageId();
//        MDC.put("eventId", eventId);
//
//        try {
//            // 1. Parse. Bad JSON will never get better on retry → dead-letter it straight away.
//            InboundEvent event;
//            try {
//                JsonNode root = objectMapper.readTree(sbMessage.getBody().toString());
//                event = parser.parseInbound(root);
//            } catch (Exception e) {
//                log.error("stage=CONSUMER_BAD_JSON eventId={} — dead-lettering", eventId, e);
//                ctx.deadLetter();
//                return;
//            }
//            if (event == null || event.mobile() == null) {
//                log.warn("stage=CONSUMER_NOT_A_CUSTOMER_MESSAGE eventId={} — skipped", eventId);
//                ctx.complete();
//                return;
//            }
//            MDC.put("mobile", mask(event.mobile()));
//
//            // 2. Already handled? (Karix retry after a slow response, or a redelivery after a crash)
//            String pid = event.providerMessageId();
//            if (pid != null && processedMessageRepository.existsById(pid)) {
//                log.info("stage=CONSUMER_DUPLICATE eventId={} providerMessageId={} — skipped", eventId, pid);
//                ctx.complete();
//                return;
//            }
//
//            log.info("stage=CONSUMER_RECEIVED eventId={} type={} messageType={} payload={}",
//                    eventId, event.type(), event.messageType(), event.payloadId());
//
//            // 3. Hand it to the bot
//            route(event);
//
//            // 4. Remember it, then remove it from the queue
//            if (pid != null) markProcessed(pid);
//            ctx.complete();
//
//        } catch (Exception e) {
//            // Unexpected failure (DB down, etc.) — put it back; Service Bus retries it and
//            // dead-letters it after the queue's max delivery count.
//            log.error("stage=CONSUMER_FAILED eventId={} — abandoning for retry", eventId, e);
//            ctx.abandon();
//        } finally {
//            MDC.clear();
//        }
//    }
//
//    /** Decides which bot method this message goes to. */
//    private void route(InboundEvent event) {
//        String phone = event.mobile();
//
//        switch (event.type()) {
//            case BUTTON, LIST_REPLY -> {
//                // Step 0 template buttons start a journey
//                String path = step0Path(event);
//                if (path != null) {
//                    log.info("stage=CONSUMER_STEP0 path={}", path);
//                    switch (path) {
//                        case "WIFE"    -> botEngineService.startWifeFlow(phone, null);
//                        case "HUSBAND" -> botEngineService.startHusbandFlow(phone, null);
//                        case "SPARKLE" -> botEngineService.startSparkleFlow(phone, null);
//                    }
//                    return;
//                }
//                if (event.payloadId() == null || event.payloadId().isBlank()) {
//                    log.warn("stage=CONSUMER_BUTTON_WITHOUT_PAYLOAD messageType={}", event.messageType());
//                    return;
//                }
//                // Every other button: path = null → the bot uses the customer's own session path
//                boolean handled = botEngineService.onButtonTap(phone, null, event.payloadId());
//                log.info("stage=CONSUMER_BUTTON_HANDLED payload={} handled={}", event.payloadId(), handled);
//            }
//            case TEXT -> {
//                boolean handled = botEngineService.onTextMessage(phone, event.text());
//                log.info("stage=CONSUMER_TEXT_HANDLED handled={}", handled);
//            }
//            case FLOW_REPLY -> log.info("stage=NOT_BUILT — WhatsApp Flow reply (C2 store booking)");
//            default -> log.info("stage=CONSUMER_IGNORED messageType={} — not a type the bot handles "
//                    + "(image, sticker, location…). If this was a button/text, fix KarixPayloadParser.", event.messageType());
//        }
//    }
//
//    /**
//     * Which journey a Step 0 template button starts, or null if it's not a Step 0 button.
//     * First by payload (from properties); then — only for template buttons — by the visible text,
//     * so it still works if the template's payloads differ from what's configured.
//     */
//    private String step0Path(InboundEvent event) {
//        String payload = event.payloadId();
//        if (payload != null) {
//            if (wifePayloads.contains(payload))    return "WIFE";
//            if (husbandPayloads.contains(payload)) return "HUSBAND";
//            if (sparklePayloads.contains(payload)) return "SPARKLE";
//        }
//        if ("button".equalsIgnoreCase(event.messageType()) && event.text() != null) {
//            String t = event.text().toLowerCase();
//            if (t.contains("celebrating"))      return "WIFE";
//            if (t.contains("shopping for her")) return "HUSBAND";
//            if (t.contains("sparkle"))          return "SPARKLE";
//        }
//        return null;
//    }
//
//    private void markProcessed(String providerMessageId) {
//        try {
//            processedMessageRepository.save(ProcessedMessage.builder().providerMessageId(providerMessageId).build());
//        } catch (DataIntegrityViolationException e) {
//            // Another delivery of the same message got there first — fine
//        }
//    }
//
//    private void onError(ServiceBusErrorContext ctx) {
//        log.error("stage=INBOUND_CONSUMER_ERROR source={} entity={}",
//                ctx.getErrorSource(), ctx.getEntityPath(), ctx.getException());
//    }
//
//    /** Daily: forget processed ids older than 7 days (duplicates only arrive within minutes). */
//    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
//    public void purgeProcessedMessages() {
//        try {
//            int deleted = processedMessageRepository.deleteOlderThan(LocalDateTime.now().minusDays(7));
//            log.info("stage=PROCESSED_MESSAGES_PURGED deleted={}", deleted);
//        } catch (Exception e) {
//            log.error("stage=PROCESSED_MESSAGES_PURGE_FAILED", e);
//        }
//    }
//
//    private static Set<String> toSet(String csv) {
//        return Arrays.stream(csv.split(","))
//                .map(String::trim)
//                .filter(s -> !s.isEmpty())
//                .collect(Collectors.toSet());
//    }
//}