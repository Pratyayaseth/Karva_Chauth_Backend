package org.example.karvachauth.service;

public interface BotEngineService {

    void startWifeFlow(String phone, String name);

    void startHusbandFlow(String phone, String name);

    void startSparkleFlow(String phone, String name);

    boolean onButtonTap(String phone, String path, String payload);
    boolean onTextMessage(String phone, String text);
    /**
     * Saves / refreshes the customer's WhatsApp profile name (Karix sends it on every message).
     * Safe to call on every inbound — does nothing if the name is empty or unchanged.
     */
    void updateCustomerName(String phone, String name);
}
