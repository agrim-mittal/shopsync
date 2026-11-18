package com.shopsync.alerts;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A user's price-alert rule. All conditions must hold at the same time (AND):
 *   price at or below {@code targetPrice}
 *   AND (optional) store rating at least {@code minRating}
 *   AND (optional) sold by one of {@code stores}
 *   AND (optional) in stock.
 */
public class PriceAlertRule {

    public enum Status { WATCHING, TRIGGERED }

    private final String id;
    private final String userId;
    private final String productId;
    private final double targetPrice;
    private final Double minRating;          // null = any rating
    private final Set<String> stores;        // empty = any store
    private final boolean inStockOnly;
    private final LocalDateTime createdAt = LocalDateTime.now();

    private Status status = Status.WATCHING;
    private LocalDateTime triggeredAt;
    private Double triggeredPrice;
    private String triggeredStore;

    public PriceAlertRule(String id, String userId, String productId, double targetPrice,
                          Double minRating, List<String> stores, boolean inStockOnly) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.targetPrice = targetPrice;
        this.minRating = minRating;
        this.stores = stores == null ? new LinkedHashSet<>() : new LinkedHashSet<>(stores);
        this.inStockOnly = inStockOnly;
    }

    synchronized void trigger(double price, String store) {
        status = Status.TRIGGERED;
        triggeredAt = LocalDateTime.now();
        triggeredPrice = price;
        triggeredStore = store;
    }

    /** Starts watching again after the user has seen the alert. */
    synchronized void rearm() {
        status = Status.WATCHING;
        triggeredAt = null;
        triggeredPrice = null;
        triggeredStore = null;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getProductId() { return productId; }
    public double getTargetPrice() { return targetPrice; }
    public Double getMinRating() { return minRating; }
    public Set<String> getStores() { return Collections.unmodifiableSet(stores); }
    public boolean isInStockOnly() { return inStockOnly; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public synchronized Status getStatus() { return status; }
    public synchronized LocalDateTime getTriggeredAt() { return triggeredAt; }
    public synchronized Double getTriggeredPrice() { return triggeredPrice; }
    public synchronized String getTriggeredStore() { return triggeredStore; }

    public boolean acceptsStore(String store) {
        return stores.isEmpty() || stores.contains(store);
    }

    /** Human-readable conditions, e.g. ["Price ≤ ₹20,000", "Rating ≥ 4.2"]. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add(String.format("Price ≤ ₹%,.0f", targetPrice));
        if (minRating != null) out.add(String.format("Rating ≥ %.1f", minRating));
        if (!stores.isEmpty()) out.add("Only on " + String.join(" or ", stores));
        if (inStockOnly) out.add("In stock");
        return out;
    }
}
