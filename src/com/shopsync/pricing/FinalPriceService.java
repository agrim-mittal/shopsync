package com.shopsync.pricing;

import com.shopsync.data.PolicyBook;
import com.shopsync.model.Offer;
import com.shopsync.model.Policy;
import com.shopsync.model.Product;
import com.shopsync.service.ProductService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * True final price calculator: what you would actually pay at checkout in each store,
 * not just the displayed price.
 *
 *   final = listed price + delivery + platform fee + COD fee
 *           − coupon − best eligible bank offer − old-device value − exchange bonus
 *
 * Only one bank offer applies per order (the best one for the user's cards), bank offers
 * don't apply to cash on delivery, and cashback offers are shown but credited later.
 */
public class FinalPriceService {

    private final ProductService products;
    private final Map<String, CheckoutProfile> profiles = new ConcurrentHashMap<>();

    public FinalPriceService(ProductService products) {
        this.products = products;
    }

    public CheckoutProfile profile(String userId) {
        return profiles.getOrDefault(userId, new CheckoutProfile());
    }

    public void saveProfile(String userId, CheckoutProfile p) {
        profiles.put(userId, p);
    }

    /** Per-store breakdown for one product, cheapest final price first. */
    public Map<String, Object> calculate(Product p, CheckoutProfile profile) {
        List<Map<String, Object>> quotes = new ArrayList<>();
        for (Offer o : products.offersFor(p)) {
            if (!o.isInStock()) continue;
            quotes.add(quote(p, o, profile));
        }
        quotes.sort(Comparator.comparingDouble(q -> ((Number) q.get("finalPrice")).doubleValue()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productId", p.getId());
        out.put("profile", profile);
        out.put("quotes", quotes);
        out.put("disclaimer", OfferBook.DISCLAIMER);
        if (!quotes.isEmpty()) {
            Map<String, Object> best = quotes.get(0);
            Map<String, Object> cheapestListed = quotes.stream()
                    .min(Comparator.comparingDouble(q -> ((Number) q.get("listedPrice")).doubleValue())).get();
            double worst = ((Number) quotes.get(quotes.size() - 1).get("finalPrice")).doubleValue();
            out.put("bestStore", best.get("store"));
            out.put("bestFinal", best.get("finalPrice"));
            out.put("savingsVsWorst", worst - ((Number) best.get("finalPrice")).doubleValue());
            String summary;
            if (!best.get("store").equals(cheapestListed.get("store"))) {
                summary = String.format("%s shows the lowest price (₹%,.0f), but after your offers %s is cheapest at ₹%,.0f, ₹%,.0f less.",
                        cheapestListed.get("store"), ((Number) cheapestListed.get("listedPrice")).doubleValue(),
                        best.get("store"), ((Number) best.get("finalPrice")).doubleValue(),
                        ((Number) cheapestListed.get("finalPrice")).doubleValue() - ((Number) best.get("finalPrice")).doubleValue());
            } else {
                summary = String.format("%s is cheapest both before and after offers: you pay ₹%,.0f (saving ₹%,.0f on the listed price).",
                        best.get("store"), ((Number) best.get("finalPrice")).doubleValue(), ((Number) best.get("totalSavings")).doubleValue());
            }
            out.put("summary", summary);
        }
        return out;
    }

    private Map<String, Object> quote(Product p, Offer o, CheckoutProfile prof) {
        String store = o.getMarketplace();
        double listed = o.getPrice();
        List<Map<String, Object>> lines = new ArrayList<>();
        double total = listed;
        double savings = 0, cashback = 0;

        double delivery = o.getTotalCost() - o.getPrice();
        if (delivery > 0) { lines.add(line("Delivery charge", delivery)); total += delivery; }

        for (OfferRule r : OfferBook.RULES) {
            if (!r.appliesTo(store, p, listed)) continue;
            if (r.type == OfferRule.Type.PLATFORM_FEE) { double a = r.amountFor(listed); lines.add(line(r.label, a)); total += a; }
            if (r.type == OfferRule.Type.COD_FEE && prof.isCashOnDelivery()) { double a = r.amountFor(listed); lines.add(line(r.label, a)); total += a; }
        }
        if (prof.isUseCoupons()) {
            OfferRule coupon = best(store, p, listed, OfferRule.Type.COUPON, null);
            if (coupon != null) { double a = coupon.amountFor(listed); lines.add(line(coupon.label, -a)); total -= a; savings += a; }
        }
        if (!prof.isCashOnDelivery()) {
            OfferRule bank = null;
            for (String card : prof.getCards()) {
                OfferRule b = best(store, p, listed, OfferRule.Type.BANK, card);
                if (b != null && (bank == null || b.amountFor(listed) > bank.amountFor(listed))) bank = b;
            }
            if (bank != null) {
                double a = bank.amountFor(listed);
                if (bank.cashback) {
                    cashback += a;
                    lines.add(line(bank.label + " (credited later)", -a));
                } else {
                    lines.add(line(bank.label, -a));
                }
                total -= a;
                savings += a;
            }
        }
        Policy pol = PolicyBook.get(store, p.getCategory());
        if (prof.getExchangeValue() > 0 && pol != null && pol.hasExchangeOffer()) {
            OfferRule bonus = best(store, p, listed, OfferRule.Type.EXCHANGE_BONUS, null);
            if (bonus != null) {
                double value = Math.min(prof.getExchangeValue(), listed * 0.6);   // stores cap trade-in value
                lines.add(line("Old device exchange value", -value));
                lines.add(line(bonus.label, -bonus.flat));
                total -= value + bonus.flat;
                savings += bonus.flat;
            }
        }
        total = Math.max(0, Math.round(total));

        Map<String, Object> q = new LinkedHashMap<>();
        q.put("store", store);
        q.put("listedPrice", listed);
        q.put("lines", lines);
        q.put("finalPrice", total);
        q.put("totalSavings", Math.round(savings));
        q.put("cashbackLater", Math.round(cashback));
        q.put("deliveryDays", o.getDeliveryDays());
        q.put("rating", o.getRating());
        return q;
    }

    private static OfferRule best(String store, Product p, double price, OfferRule.Type type, String bank) {
        OfferRule best = null;
        for (OfferRule r : OfferBook.RULES) {
            if (r.type != type || !r.appliesTo(store, p, price)) continue;
            if (bank != null && !bank.equals(r.bank)) continue;
            if (best == null || r.amountFor(price) > best.amountFor(price)) best = r;
        }
        return best;
    }

    private static Map<String, Object> line(String label, double amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("amount", amount);
        return m;
    }

    /** Lowest final price for the product with this profile (used in comparisons and by the assistant). */
    public Map<String, Object> bestQuote(Product p, CheckoutProfile prof) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> quotes = (List<Map<String, Object>>) calculate(p, prof).get("quotes");
        return quotes.isEmpty() ? null : quotes.get(0);
    }
}
