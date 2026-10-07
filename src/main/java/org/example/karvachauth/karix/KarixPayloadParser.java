//package org.example.karvachauth.karix;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.example.karvachauth.model.InboundEvent;
//import org.example.karvachauth.model.InputType;
//import org.springframework.stereotype.Component;
//
///**
// * Turns Karix's inbound JSON into an InboundEvent.
// *
// * The field names follow Meta's structure under eventContent.message, which Karix mirrors.
// * It checks a couple of spellings for each value, because BSPs differ slightly. If a real
// * Karix message ever comes back as OTHER, the consumer logs the raw JSON — fix the field
// * name here and nothing else changes.
// */
//@Component
//public class KarixPayloadParser {
//
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    /** Null if the JSON is not a customer message (e.g. a delivery receipt). */
//    public InboundEvent parseInbound(JsonNode root) {
//        JsonNode msg = root.path("eventContent").path("message");
//        if (!msg.isObject()) return null;
//
//        String id        = text(msg, "id");
//        String mobile    = digits(text(msg, "from"));
//        String type      = text(msg, "messageType");
//        if (type == null) type = text(msg, "type");
//        String repliedTo = text(msg.path("context"), "id");
//        String t = type == null ? "" : type.toLowerCase();
//
//        // Typed text: { "text": { "body": "..." } }  or  { "text": "..." }
//        if (t.equals("text")) {
//            JsonNode textNode = msg.path("text");
//            String body = textNode.isObject() ? text(textNode, "body") : text(msg, "text");
//            return new InboundEvent(id, mobile, InputType.TEXT, type, null, body, repliedTo, null);
//        }
//
//        // Template quick reply (Step 0): { "button": { "payload": "...", "text": "..." } }
//        if (t.equals("button")) {
//            JsonNode b = msg.path("button");
//            return new InboundEvent(id, mobile, InputType.BUTTON, type,
//                    first(text(b, "payload"), text(b, "id")), text(b, "text"), repliedTo, null);
//        }
//
//        // Session buttons, list rows and WhatsApp Flow replies
//        if (t.equals("interactive")) {
//            JsonNode inter = msg.path("interactive");
//            String sub = first(text(inter, "type"), "");
//
//            JsonNode buttonReply = first(inter.path("button_reply"), inter.path("buttonReply"));
//            if (sub.equals("button_reply") || buttonReply.isObject()) {
//                return new InboundEvent(id, mobile, InputType.BUTTON, type,
//                        text(buttonReply, "id"), text(buttonReply, "title"), repliedTo, null);
//            }
//
//            JsonNode listReply = first(inter.path("list_reply"), inter.path("listReply"));
//            if (sub.equals("list_reply") || listReply.isObject()) {
//                return new InboundEvent(id, mobile, InputType.LIST_REPLY, type,
//                        text(listReply, "id"), text(listReply, "title"), repliedTo, null);
//            }
//
//            JsonNode nfm = first(inter.path("nfm_reply"), inter.path("nfmReply"));
//            if (sub.equals("nfm_reply") || nfm.isObject()) {
//                return new InboundEvent(id, mobile, InputType.FLOW_REPLY, type, null, null, repliedTo,
//                        readJson(first(text(nfm, "response_json"), text(nfm, "responseJson"))));
//            }
//        }
//
//        return new InboundEvent(id, mobile, InputType.OTHER, type, null, null, repliedTo, null);
//    }
//
//    // ---------- helpers (public static so the consumers can reuse them) ----------
//
//    public static String text(JsonNode node, String field) {
//        JsonNode v = node.path(field);
//        return (v.isMissingNode() || v.isNull()) ? null : v.asText(null);
//    }
//
//    /** Keeps digits only; null unless 10-15 digits long. */
//    public static String digits(String s) {
//        if (s == null) return null;
//        String d = s.replaceAll("[^0-9]", "");
//        return (d.length() >= 10 && d.length() <= 15) ? d : null;
//    }
//
//    public static String mask(String mobile) {
//        if (mobile == null || mobile.length() < 6) return "****";
//        return mobile.substring(0, 2) + "******" + mobile.substring(mobile.length() - 4);
//    }
//
//    private static String first(String a, String b) {
//        return (a != null && !a.isBlank()) ? a : b;
//    }
//
//    private static JsonNode first(JsonNode a, JsonNode b) {
//        return a.isObject() ? a : b;
//    }
//
//    private JsonNode readJson(String s) {
//        try {
//            return s == null ? null : mapper.readTree(s);
//        } catch (Exception e) {
//            return null;
//        }
//    }
//}