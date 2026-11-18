package com.shopsync.alerts;

import com.shopsync.model.Alert;
import com.shopsync.model.Offer;
import com.shopsync.model.PricePoint;
import com.shopsync.model.Product;
import com.shopsync.service.LiveUpdateEngine;
import com.shopsync.service.PriceInsight;
import com.shopsync.service.ProductService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smart price alerts and the buy-now-or-wait advisor.
 *
 * Every few seconds {@link #checkAll()} evaluates each watching rule against live prices.
 * When every condition holds, the rule is marked TRIGGERED and the user gets a notification.
 * {@link #advice} uses the 90-day price history to say whether waiting is likely to pay off.
 */
public class PriceAlertService {

    public static final int MAX_RULES_PER_USER = 25;

    private final ProductService products;
    private final LiveUpdateEngine live;
    private final Map<String, List<PriceAlertRule>> byUser = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public PriceAlertService(ProductService products, LiveUpdateEngine live) {
        this.products = products;
        this.live = live;
    }

    // =============================================================== rules

    public PriceAlertRule create(String userId, String productId, double targetPrice, Double minRating,
                                 List<String> stores, boolean inStockOnly) {
        Product p = products.find(productId);
        if (p == null) throw new IllegalArgumentException("Unknown product");
        if (targetPrice <= 0) throw new IllegalArgumentException("Enter a target price above ₹0");
        if (minRating != null && (minRating < 1 || minRating > 5)) throw new IllegalArgumentException("Rating must be between 1 and 5");
        List<PriceAlertRule> list = byUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        if (list.size() >= MAX_RULES_PER_USER) throw new IllegalArgumentException("You can have up to " + MAX_RULES_PER_USER + " alerts");
        PriceAlertRule rule = new PriceAlertRule("A" + nextId.getAndIncrement(), userId, p.getId(), targetPrice,
                minRating, stores, inStockOnly);
        list.add(rule);
        check(rule);   // it may already be met
        return rule;
    }

    public boolean delete(String userId, String ruleId) {
        List<PriceAlertRule> list = byUser.get(userId);
        return list != null && list.removeIf(r -> r.getId().equals(ruleId));
    }

    public PriceAlertRule rearm(String userId, String ruleId) {
        for (PriceAlertRule r : rules(userId)) {
            if (r.getId().equals(ruleId)) { r.rearm(); return r; }
        }
        return null;
    }

    public List<PriceAlertRule> rules(String userId) {
        List<PriceAlertRule> list = byUser.get(userId);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    // =============================================================== checking

    /** Called by the background scheduler. */
    public void checkAll() {
        for (List<PriceAlertRule> list : byUser.values()) {
            for (PriceAlertRule r : list) {
                if (r.getStatus() == PriceAlertRule.Status.WATCHING) check(r);
            }
        }
    }

    private void check(PriceAlertRule r) {
        Offer match = bestMatch(r, true);
        if (match == null || r.getStatus() != PriceAlertRule.Status.WATCHING) return;
        r.trigger(match.getTotalCost(), match.getMarketplace());
        Product p = products.find(r.getProductId());
        live.notify(new Alert(Alert.Type.PRICE_TARGET, "Target reached: " + p.getShortName(),
                String.format("₹%,.0f on %s (your target ₹%,.0f)", match.getTotalCost(), match.getMarketplace(), r.getTargetPrice()),
                match.getMarketplace(), r.getUserId(), p.getId()));
    }

    /**
     * Cheapest offer meeting the rule's non-price conditions; when {@code requirePrice}
     * is true it must also be at or below the target.
     */
    private Offer bestMatch(PriceAlertRule r, boolean requirePrice) {
        Product p = products.find(r.getProductId());
        if (p == null) return null;
        Offer best = null;
        for (Offer o : products.offersFor(p)) {
            if (!r.acceptsStore(o.getMarketplace())) continue;
            if (r.isInStockOnly() && !o.isInStock()) continue;
            if (r.getMinRating() != null && o.getRating() < r.getMinRating()) continue;
            if (requirePrice && o.getTotalCost() > r.getTargetPrice()) continue;
            if (best == null || o.getTotalCost() < best.getTotalCost()) best = o;
        }
        return best;
    }

    // =============================================================== views

    /** Everything the UI shows for one rule: conditions and whether each is met, progress and advice. */
    public Map<String, Object> view(PriceAlertRule r) {
        Product p = products.find(r.getProductId());
        List<Offer> offers = products.offersFor(p);
        Offer candidate = bestMatch(r, false);           // best offer that meets everything except price
        Offer cheapestAny = offers.isEmpty() ? null : offers.stream()
                .filter(o -> r.acceptsStore(o.getMarketplace()))
                .min(Comparator.comparingDouble(Offer::getTotalCost)).orElse(null);

        List<Map<String, Object>> conditions = new ArrayList<>();
        double price = candidate != null ? candidate.getTotalCost() : cheapestAny != null ? cheapestAny.getTotalCost() : 0;
        String where = candidate != null ? candidate.getMarketplace() : cheapestAny != null ? cheapestAny.getMarketplace() : "—";
        conditions.add(condition(String.format("Price ≤ ₹%,.0f", r.getTargetPrice()), candidate != null && price <= r.getTargetPrice(),
                String.format("Now ₹%,.0f on %s", price, where)));
        if (r.getMinRating() != null) {
            double bestRating = 0;
            for (Offer o : offers) if (r.acceptsStore(o.getMarketplace())) bestRating = Math.max(bestRating, o.getRating());
            conditions.add(condition(String.format("Rating ≥ %.1f", r.getMinRating()), candidate != null,
                    candidate != null ? String.format("%.1f on %s", candidate.getRating(), candidate.getMarketplace())
                            : String.format("Best available %.1f", bestRating)));
        }
        if (!r.getStores().isEmpty()) {
            conditions.add(condition("Only on " + String.join(" or ", r.getStores()), candidate != null,
                    candidate != null ? "Sold by " + candidate.getMarketplace() : "Not sold there right now"));
        }
        if (r.isInStockOnly()) {
            conditions.add(condition("In stock", candidate != null, candidate != null ? "Available" : "Out of stock"));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("product", products.summary(p));
        m.put("targetPrice", r.getTargetPrice());
        m.put("minRating", r.getMinRating());
        m.put("stores", new ArrayList<>(r.getStores()));
        m.put("inStockOnly", r.isInStockOnly());
        m.put("status", r.getStatus());
        m.put("createdAt", r.getCreatedAt().toString());
        m.put("triggeredAt", r.getTriggeredAt() == null ? null : r.getTriggeredAt().toString());
        m.put("triggeredPrice", r.getTriggeredPrice());
        m.put("triggeredStore", r.getTriggeredStore());
        m.put("conditions", conditions);
        m.put("currentPrice", price);
        m.put("currentStore", where);
        m.put("gap", Math.max(0, price - r.getTargetPrice()));
        // progress: 100% when at/below target; falls off as the price is further above it (0% at 25% above)
        double over = r.getTargetPrice() <= 0 ? 1 : (price - r.getTargetPrice()) / r.getTargetPrice();
        m.put("progress", (int) Math.round(Math.max(0, Math.min(1, 1 - over / 0.25)) * 100));
        m.putAll(advice(r, p, candidate));
        return m;
    }

    private static Map<String, Object> condition(String label, boolean met, String detail) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("label", label);
        c.put("met", met);
        c.put("detail", detail);
        return c;
    }

    /**
     * Buy-now-or-wait advice, using the 90-day lowest-price history:
     *  BUY_NOW        all conditions are met right now
     *  CLOSE          within 3% of the target
     *  WAIT           the price has been at/below the target recently, so it is realistic
     *  ADJUST_TARGET  the target is below anything seen in 90 days
     */
    public Map<String, Object> advice(PriceAlertRule r, Product p, Offer candidate) {
        PriceInsight ins = products.insight(p, 90);
        double target = r.getTargetPrice();
        int daysAtOrBelow = 0;
        LocalDate lastHit = null;
        for (PricePoint pt : ins.getBestSeries()) {
            if (pt.getPrice() <= target) {
                daysAtOrBelow++;
                lastHit = pt.getDate();
            }
        }
        double now = candidate != null ? candidate.getTotalCost() : ins.getCurrent();
        String code, text;
        if (candidate != null && now <= target) {
            code = "BUY_NOW";
            text = String.format("Buy now: all your conditions are met at ₹%,.0f on %s.", now, candidate.getMarketplace())
                    + (ins.getVerdict() == PriceInsight.Verdict.GREAT ? " It's also near the lowest price in 90 days." : "");
        } else if (candidate == null && r.getMinRating() != null) {
            code = "WAIT";
            text = "No store currently meets your rating/store conditions. ShopSync keeps watching.";
        } else if ((now - target) / target <= 0.03) {
            code = "CLOSE";
            text = String.format("Very close: only ₹%,.0f above your target. Small daily price moves could reach it soon.", now - target);
        } else if (target < ins.getLowest() * 0.97) {
            code = "ADJUST_TARGET";
            text = String.format("Your target is below anything seen in 90 days (lowest ₹%,.0f on %s). "
                    + "Consider a target near ₹%,.0f, or wait for the next big sale.", ins.getLowest(), ins.getLowestStore(), ins.getLowest());
        } else if (daysAtOrBelow > 0) {
            code = "WAIT";
            text = String.format("Worth waiting: the price was at or below your target on %d of the last 90 days (most recently %s).",
                    daysAtOrBelow, lastHit);
        } else {
            code = "WAIT";
            text = String.format("Wait: the price hasn't reached your target in 90 days, but the low was ₹%,.0f, close to it.",
                    ins.getLowest());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("advice", code);
        m.put("adviceText", text);
        m.put("daysAtOrBelow", daysAtOrBelow);
        m.put("lowest90", ins.getLowest());
        m.put("verdict", ins.getVerdict());
        return m;
    }

    public List<Map<String, Object>> views(String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PriceAlertRule r : rules(userId)) out.add(view(r));
        // triggered first, then closest to target
        out.sort(Comparator.comparing((Map<String, Object> m) -> m.get("status") != PriceAlertRule.Status.TRIGGERED)
                .thenComparing(m -> -((Number) m.get("progress")).intValue()));
        return out;
    }
}
