package com.shopsync.model;

import com.shopsync.util.JsonSerializable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An order the user placed, with its full tracking history.
 *
 * Orders come from two places:
 *  - a connected marketplace (Amazon, Flipkart, Myntra, Croma), source = "Marketplace"
 *  - order emails / SMS the user imported from ANY store, source = "Email", "SMS" or "Pasted".
 *    Details such as the product name or tracking number may arrive in later messages,
 *    so those fields can be filled in after the order is created.
 */
public class Order implements JsonSerializable {

    private final String orderId;
    private final String userId;        // the customer who placed it
    private final String marketplace;
    private final String productId;     // null for imported orders (not in our catalogue)
    private final String productIcon;
    private final int quantity;
    private final LocalDateTime orderedOn;
    private final String source;

    private String productName;
    private double amount;
    private LocalDate expectedDelivery; // may be unknown (null) for imported orders
    private String courier;
    private String trackingNumber;
    private OrderStatus status;
    private final List<TrackingEvent> events = new ArrayList<>();

    /** A marketplace order. */
    public Order(String orderId, String userId, String marketplace, String productId, String productName, String productIcon,
                 int quantity, double amount, LocalDateTime orderedOn,
                 LocalDate expectedDelivery, String courier) {
        this(orderId, userId, marketplace, productId, productName, productIcon, quantity, amount, orderedOn,
                expectedDelivery, courier, "Marketplace", OrderStatus.PLACED, "Order confirmed by " + marketplace, "Online");
    }

    /** Full constructor, also used for orders imported from emails and messages. */
    public Order(String orderId, String userId, String marketplace, String productId, String productName, String productIcon,
                 int quantity, double amount, LocalDateTime orderedOn, LocalDate expectedDelivery, String courier,
                 String source, OrderStatus initialStatus, String firstMessage, String firstLocation) {
        this.orderId = orderId;
        this.userId = userId;
        this.marketplace = marketplace;
        this.productId = productId;
        this.productName = productName;
        this.productIcon = productIcon;
        this.quantity = quantity;
        this.amount = amount;
        this.orderedOn = orderedOn;
        this.expectedDelivery = expectedDelivery;
        this.courier = courier;
        this.source = source;
        this.status = initialStatus;
        events.add(new TrackingEvent(initialStatus, firstMessage, firstLocation, orderedOn));
    }

    /** Moves the order to its next stage and logs a tracking event. Returns false if already final. */
    public synchronized boolean advance(String location, String message, LocalDateTime when) {
        if (status.isFinal()) {
            return false;
        }
        status = status.next();
        events.add(new TrackingEvent(status, message, location, when));
        return true;
    }

    public synchronized void cancel(String reason, LocalDateTime when) {
        if (status.isFinal()) return;
        status = OrderStatus.CANCELLED;
        events.add(new TrackingEvent(status, reason, "Online", when));
    }

    /**
     * Applies a status reported by a new email/message. Jumps straight to it (no invented
     * in-between events). Ignores news older than what we already know, e.g. a late
     * "shipped" SMS arriving after "delivered". Returns true if the status changed.
     */
    public synchronized boolean applyReportedStatus(OrderStatus reported, String message, String location,
                                                    LocalDateTime when) {
        if (reported == null || status.isFinal()) return false;
        if (reported == OrderStatus.CANCELLED) {
            cancel(message, when);
            return true;
        }
        if (reported.getProgress() <= status.getProgress()) return false;
        status = reported;
        events.add(new TrackingEvent(status, message, location, when));
        return true;
    }

    /** Fills in details that a later message revealed, keeping what we already had otherwise. */
    public synchronized void mergeDetails(String product, Double newAmount, LocalDate eta, String newCourier, String tracking) {
        if (product != null && !product.isBlank() && productName.startsWith("Order from ")) productName = product;
        if (newAmount != null && newAmount > 0 && amount <= 0) amount = newAmount;
        if (eta != null) expectedDelivery = eta;
        if (newCourier != null && !newCourier.isBlank()) courier = newCourier;
        if (tracking != null && !tracking.isBlank()) trackingNumber = tracking;
    }

    public synchronized void setTrackingNumber(String tracking) { this.trackingNumber = tracking; }

    public synchronized OrderStatus getStatus() { return status; }

    public synchronized LocalDateTime getLastUpdate() {
        return events.get(events.size() - 1).getTime();
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getMarketplace() { return marketplace; }
    public String getProductId() { return productId; }
    public synchronized String getProductName() { return productName; }
    public synchronized double getAmount() { return amount; }
    public LocalDateTime getOrderedOn() { return orderedOn; }
    public synchronized LocalDate getExpectedDelivery() { return expectedDelivery; }
    public synchronized String getTrackingNumber() { return trackingNumber; }
    public String getSource() { return source; }
    public boolean isImported() { return !"Marketplace".equals(source); }

    @Override
    public synchronized Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", orderId);
        m.put("marketplace", marketplace);
        m.put("productId", productId);
        m.put("productName", productName);
        m.put("productIcon", productIcon);
        m.put("quantity", quantity);
        m.put("amount", amount);
        m.put("orderedOn", orderedOn.toString());
        m.put("expectedDelivery", expectedDelivery == null ? null : expectedDelivery.toString());
        m.put("courier", courier);
        m.put("trackingNumber", trackingNumber);
        m.put("source", source);
        m.put("imported", isImported());
        m.put("status", status);
        m.put("statusLabel", status.getLabel());
        m.put("progress", status.getProgress());
        m.put("lastUpdate", getLastUpdate().toString());
        m.put("events", new ArrayList<>(events));
        return m;
    }
}
