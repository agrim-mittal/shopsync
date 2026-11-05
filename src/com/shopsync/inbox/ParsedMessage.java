package com.shopsync.inbox;

import com.shopsync.model.OrderStatus;
import com.shopsync.util.JsonSerializable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What ShopSync understood from one email or text message. */
public class ParsedMessage implements JsonSerializable {

    boolean orderMessage;
    String store;
    String orderId;
    String product;
    Double amount;
    OrderStatus status;
    String courier;
    String trackingNumber;
    LocalDate expectedDelivery;
    String parser = "rules";            // "rules" or the AI model's name
    final List<String> notes = new ArrayList<>();

    public boolean isOrderMessage() { return orderMessage; }
    public String getStore() { return store; }
    public String getOrderId() { return orderId; }
    public String getProduct() { return product; }
    public Double getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public String getCourier() { return courier; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocalDate getExpectedDelivery() { return expectedDelivery; }

    /** 0–100: how many useful details were found. */
    public int confidence() {
        if (!orderMessage) return 0;
        int score = 20;
        if (store != null) score += 20;
        if (orderId != null) score += 20;
        if (status != null) score += 15;
        if (product != null) score += 10;
        if (amount != null) score += 5;
        if (trackingNumber != null || courier != null) score += 5;
        if (expectedDelivery != null) score += 5;
        return Math.min(100, score);
    }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isOrder", orderMessage);
        m.put("store", store);
        m.put("orderId", orderId);
        m.put("product", product);
        m.put("amount", amount);
        m.put("status", status);
        m.put("statusLabel", status == null ? null : status.getLabel());
        m.put("courier", courier);
        m.put("trackingNumber", trackingNumber);
        m.put("expectedDelivery", expectedDelivery == null ? null : expectedDelivery.toString());
        m.put("confidence", confidence());
        m.put("parser", parser);
        m.put("notes", notes);
        return m;
    }
}
