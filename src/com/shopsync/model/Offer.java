package com.shopsync.model;

import com.shopsync.util.JsonSerializable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * One marketplace's listing of a product: its live price, delivery time,
 * rating, a short "live" price history (for sparklines) and a long daily
 * price history (for the price-history chart).
 *
 * Prices are changed by a background thread while web requests read them,
 * so all price access is synchronized.
 */
public class Offer implements JsonSerializable {

    private static final int LIVE_HISTORY_SIZE = 20;

    private final Product product;
    private final String marketplace;
    private final String seller;
    private final double rating;
    private final int reviewCount;
    private final int deliveryDays;
    private final boolean freeDelivery;

    private double price;
    private boolean inStock = true;
    private LocalDateTime lastUpdated = LocalDateTime.now();
    private final LinkedList<Double> liveHistory = new LinkedList<>();
    private final List<PricePoint> dailyHistory;

    public Offer(Product product, String marketplace, String seller, double price, double rating,
                 int reviewCount, int deliveryDays, boolean freeDelivery, List<PricePoint> dailyHistory) {
        this.product = product;
        this.marketplace = marketplace;
        this.seller = seller;
        this.price = price;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.deliveryDays = deliveryDays;
        this.freeDelivery = freeDelivery;
        this.dailyHistory = new ArrayList<>(dailyHistory);
        liveHistory.add(price);
    }

    /** Updates the price and records it in both histories. Returns the change (negative = drop). */
    public synchronized double updatePrice(double newPrice) {
        double change = newPrice - price;
        price = newPrice;
        lastUpdated = LocalDateTime.now();
        liveHistory.addLast(newPrice);
        if (liveHistory.size() > LIVE_HISTORY_SIZE) liveHistory.removeFirst();

        // Today's entry in the daily history always holds the latest price.
        LocalDate today = LocalDate.now();
        int last = dailyHistory.size() - 1;
        if (last >= 0 && dailyHistory.get(last).getDate().equals(today)) {
            dailyHistory.set(last, new PricePoint(today, newPrice));
        } else {
            dailyHistory.add(new PricePoint(today, newPrice));
        }
        return change;
    }

    public synchronized double getPrice() { return price; }

    public synchronized boolean isInStock() { return inStock; }

    public synchronized void setInStock(boolean inStock) {
        this.inStock = inStock;
        this.lastUpdated = LocalDateTime.now();
    }

    /** Discount compared to MRP, as a whole-number percentage. */
    public synchronized int getDiscountPercent() {
        return (int) Math.round((1 - price / product.getMrp()) * 100);
    }

    /** "UP", "DOWN" or "SAME" compared to the previous live price. */
    public synchronized String getTrend() {
        if (liveHistory.size() < 2) return "SAME";
        double prev = liveHistory.get(liveHistory.size() - 2);
        if (price < prev) return "DOWN";
        if (price > prev) return "UP";
        return "SAME";
    }

    /** Daily prices for the last {@code days} days (oldest first). */
    public synchronized List<PricePoint> getDailyHistory(int days) {
        int from = Math.max(0, dailyHistory.size() - days);
        return new ArrayList<>(dailyHistory.subList(from, dailyHistory.size()));
    }

    public synchronized List<Double> getLiveHistory() {
        return new ArrayList<>(liveHistory);
    }

    public Product getProduct() { return product; }
    public String getMarketplace() { return marketplace; }
    public String getSeller() { return seller; }
    public double getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public int getDeliveryDays() { return deliveryDays; }
    public boolean isFreeDelivery() { return freeDelivery; }

    /** Effective cost to the buyer including a flat delivery fee where applicable. */
    public synchronized double getTotalCost() {
        return price + (freeDelivery ? 0 : 40);
    }

    @Override
    public synchronized Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("marketplace", marketplace);
        m.put("seller", seller);
        m.put("price", price);
        m.put("totalCost", getTotalCost());
        m.put("discountPercent", getDiscountPercent());
        m.put("rating", rating);
        m.put("reviewCount", reviewCount);
        m.put("deliveryDays", deliveryDays);
        m.put("freeDelivery", freeDelivery);
        m.put("inStock", inStock);
        m.put("trend", getTrend());
        m.put("history", new ArrayList<>(liveHistory));
        m.put("lastUpdated", lastUpdated.toString());
        return m;
    }
}
