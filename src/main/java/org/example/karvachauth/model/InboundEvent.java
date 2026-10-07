package org.example.karvachauth.model;

import com.fasterxml.jackson.databind.JsonNode;

/** One customer message, parsed from Karix's JSON. The bot never sees raw Karix JSON. */
public record InboundEvent(
        String providerMessageId,   // Karix message id — used for de-duplication
        String mobile,              // digits only, e.g. 919876543210
        InputType type,
        String messageType,         // raw Karix type ("text", "button", "interactive", …) — for logs
        String payloadId,           // button / list-row id (what we route on)
        String text,                // typed text, or the tapped button's visible title
        String repliedToMid,        // the message she replied to, if any
        JsonNode flowResponse       // WhatsApp Flow answers (C2) — null otherwise
) {}