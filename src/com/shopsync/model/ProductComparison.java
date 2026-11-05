package com.shopsync.model;

import com.shopsync.util.JsonSerializable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of comparing one product across every marketplace that sells it.
 * Offers are ordered best deal first (in-stock, then lowest total cost).
 */
public class ProductComparison implements JsonSerializable {

    /** In-stock offers first, then cheapest total cost, then fastest delivery. */
    public static final Comparator<Offer> BEST_DEAL_FIRST =
            Comparator.comparing((Offer o) -> !o.isInStock())
                      .thenComparingDouble(Offer::getTotalCost)
                      .thenComparingInt(Offer::getDeliveryDays);

    private final Product product;
    private final List<Offer> offers;

    public ProductComparison(Product product, List<Offer> offers) {
        this.product = product;
        this.offers = new ArrayList<>(offers);
        this.offers.sort(BEST_DEAL_FIRST);
    }

    public Product getProduct() { return product; }
    public List<Offer> getOffers() { return offers; }

    public Offer getBestOffer() {
        return offers.isEmpty() ? null : offers.get(0);
    }

    /** How much the user saves by picking the best deal instead of the most expensive in-stock one. */
    public double getSavings() {
        double best = Double.MAX_VALUE;
        double worst = 0;
        for (Offer o : offers) {
            if (!o.isInStock()) continue;
            best = Math.min(best, o.getTotalCost());
            worst = Math.max(worst, o.getTotalCost());
        }
        return worst > best ? worst - best : 0;
    }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product", product);
        Offer best = getBestOffer();
        m.put("bestMarketplace", best == null ? null : best.getMarketplace());
        m.put("bestPrice", best == null ? null : best.getTotalCost());
        m.put("savings", getSavings());
        m.put("offers", offers);
        return m;
    }
}
