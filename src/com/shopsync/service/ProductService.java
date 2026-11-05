package com.shopsync.service;

import com.shopsync.ai.LlmClient;
import com.shopsync.auth.User;
import com.shopsync.data.PolicyBook;
import com.shopsync.marketplace.Marketplace;
import com.shopsync.model.Category;
import com.shopsync.model.Offer;
import com.shopsync.model.Order;
import com.shopsync.model.OrderStatus;
import com.shopsync.model.Policy;
import com.shopsync.model.Product;
import com.shopsync.model.ProductComparison;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything about products that isn't a live search: details, policies,
 * price-history insight, AI descriptions, side-by-side comparison,
 * personalised recommendations and today's deals.
 */
public class ProductService {

    private final List<Product> catalog;
    private final Collection<Marketplace> marketplaces;
    private final OrderTrackingService orders;
    private final LlmClient llm;
    private final Map<String, String> aiDescriptionCache = new ConcurrentHashMap<>();

    public ProductService(List<Product> catalog, Collection<Marketplace> marketplaces,
                          OrderTrackingService orders, LlmClient llm) {
        this.catalog = catalog;
        this.marketplaces = marketplaces;
        this.orders = orders;
        this.llm = llm;
    }

    // ------------------------------------------------------------ lookups

    public List<Product> catalog() {
        return catalog;
    }

    public Product find(String id) {
        for (Product p : catalog) if (p.getId().equalsIgnoreCase(id)) return p;
        return null;
    }

    public List<Offer> offersFor(Product p) {
        List<Offer> list = new ArrayList<>();
        for (Marketplace m : marketplaces) {
            Offer o = m.findOffer(p.getId());
            if (o != null) list.add(o);
        }
        list.sort(ProductComparison.BEST_DEAL_FIRST);
        return list;
    }

    public PriceInsight insight(Product p, int days) {
        return new PriceInsight(offersFor(p), days);
    }

    public List<Policy> policies(Product p) {
        List<Policy> list = new ArrayList<>();
        for (Offer o : offersFor(p)) {
            Policy policy = PolicyBook.get(o.getMarketplace(), p.getCategory());
            if (policy != null) list.add(policy);
        }
        return list;
    }

    /** The single most buyer-friendly policy, or null if two or more stores tie for best. */
    public static Policy mostFlexible(List<Policy> policies) {
        Policy best = null;
        boolean tie = false;
        for (Policy p : policies) {
            if (best == null || p.flexibilityScore() > best.flexibilityScore()) { best = p; tie = false; }
            else if (p.flexibilityScore() == best.flexibilityScore()) tie = true;
        }
        return tie ? null : best;
    }

    /** Most buyer-friendly policy even when tied (first one wins); used for comparison tables. */
    private static Policy bestPolicy(List<Policy> policies) {
        Policy best = null;
        for (Policy p : policies) if (best == null || p.flexibilityScore() > best.flexibilityScore()) best = p;
        return best;
    }

    public static double averageRating(List<Offer> offers) {
        if (offers.isEmpty()) return 0;
        double sum = 0;
        for (Offer o : offers) sum += o.getRating();
        return Math.round(sum / offers.size() * 10) / 10.0;
    }

    /** Compact card data: product + best price, store count, rating. */
    public Map<String, Object> summary(Product p) {
        List<Offer> offers = offersFor(p);
        Offer best = offers.isEmpty() ? null : offers.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product", p);
        m.put("bestPrice", best == null ? null : best.getTotalCost());
        m.put("bestMarketplace", best == null ? null : best.getMarketplace());
        m.put("discountPercent", best == null ? 0 : best.getDiscountPercent());
        m.put("rating", averageRating(offers));
        m.put("stores", offers.size());
        m.put("history", best == null ? new ArrayList<>() : best.getLiveHistory());
        return m;
    }

    // ------------------------------------------------------- descriptions

    /** Data-driven description that works without any AI service. */
    public String templateDescription(Product p) {
        List<Offer> offers = offersFor(p);
        PriceInsight insight = new PriceInsight(offers, 90);
        Offer best = offers.isEmpty() ? null : offers.get(0);
        Policy flexible = mostFlexible(policies(p));

        List<String> highlights = new ArrayList<>();
        for (Map.Entry<String, String> e : p.getSpecs().entrySet()) {
            highlights.add(e.getKey() + ": " + e.getValue());
            if (highlights.size() == 3) break;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(p.getShortName()).append(" is ").append(article(p.kind())).append(' ').append(p.kind())
          .append(" from ").append(p.getBrand());
        sb.append(". ");
        if (!highlights.isEmpty()) sb.append("Key specs: ").append(String.join("; ", highlights)).append(". ");
        if (!p.getGoodFor().isEmpty()) sb.append("Best for ").append(p.getGoodFor()).append(". ");
        if (best != null) {
            sb.append(String.format("The best price right now is ₹%,.0f on %s (%d%% below MRP), ",
                    best.getTotalCost(), best.getMarketplace(), best.getDiscountPercent()));
            sb.append(String.format("and shoppers rate it %.1f★ on average across %d store%s. ",
                    averageRating(offers), offers.size(), offers.size() > 1 ? "s" : ""));
        }
        sb.append(insight.getVerdictText());
        if (flexible != null && offers.size() > 1) {
            sb.append(" For the easiest returns, buy from ").append(flexible.getMarketplace())
              .append(" (").append(flexible.summary()).append(").");
        }
        return sb.toString();
    }

    /** AI-written description when a model is configured (cached), otherwise the template. */
    public String aiDescription(Product p) {
        if (!llm.isEnabled()) return templateDescription(p);
        String cached = aiDescriptionCache.get(p.getId());
        if (cached != null) return cached;
        try {
            String prompt = "Write a 3–4 sentence shopping description for this product for Indian shoppers. "
                    + "Mention who it is for, its standout specs, and the current best deal. "
                    + "Use only these facts; do not invent specs or prices.\n\nFACTS:\n" + factSheet(p);
            String text = llm.complete(ShoppingAssistant.SYSTEM_PROMPT, prompt);
            aiDescriptionCache.put(p.getId(), text);
            return text;
        } catch (IOException e) {
            System.err.println("AI description failed, using template: " + e.getMessage());
            return templateDescription(p);
        }
    }

    /** Plain-text facts about a product, given to the AI model so it can't make things up. */
    public String factSheet(Product p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Product: ").append(p.getName()).append(" by ").append(p.getBrand())
          .append(" (").append(p.getCategory().getLabel()).append(")\n");
        sb.append(String.format("MRP: ₹%,.0f\n", p.getMrp()));
        for (Map.Entry<String, String> e : p.getSpecs().entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append("Good for: ").append(p.getGoodFor()).append('\n');
        for (Offer o : offersFor(p)) {
            sb.append(String.format("- %s: ₹%,.0f, %.1f★, delivery %d days, %s, %s\n", o.getMarketplace(),
                    o.getTotalCost(), o.getRating(), o.getDeliveryDays(),
                    o.isInStock() ? "in stock" : "out of stock",
                    describePolicy(PolicyBook.get(o.getMarketplace(), p.getCategory()))));
        }
        PriceInsight insight = insight(p, 90);
        sb.append(String.format("90-day lowest: ₹%,.0f on %s (%s). 30-day average: ₹%,.0f. Verdict: %s\n",
                insight.getLowest(), insight.getLowestStore(), insight.getLowestDate(),
                insight.getAverage30(), insight.getVerdictText()));
        return sb.toString();
    }

    private static String describePolicy(Policy p) {
        return p == null ? "policy unknown" : "returns: " + p.summary() + (p.isCashOnDelivery() ? ", COD available" : "");
    }

    private static String article(String word) {
        return "aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an" : "a";
    }

    // ------------------------------------------------------------ detail

    public Map<String, Object> detail(Product p, int days) {
        List<Policy> policies = policies(p);
        Policy flexible = mostFlexible(policies);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product", p);
        m.put("offers", offersFor(p));
        m.put("rating", averageRating(offersFor(p)));
        m.put("description", templateDescription(p));
        m.put("aiAvailable", llm.isEnabled());
        m.put("policies", policies);
        m.put("mostFlexibleStore", flexible == null ? null : flexible.getMarketplace());
        m.put("policyDisclaimer", PolicyBook.DISCLAIMER);
        m.put("insight", insight(p, days));
        return m;
    }

    // ------------------------------------------------- side-by-side compare

    public Map<String, Object> compareProducts(List<Product> products) {
        List<Map<String, Object>> cols = new ArrayList<>();
        List<Double> prices = new ArrayList<>(), ratings = new ArrayList<>(), discounts = new ArrayList<>(),
                delivery = new ArrayList<>(), returns = new ArrayList<>(), value = new ArrayList<>();
        List<PriceInsight> insights = new ArrayList<>();
        List<Policy> bestPolicies = new ArrayList<>();

        for (Product p : products) {
            List<Offer> offers = offersFor(p);
            Offer best = offers.isEmpty() ? null : offers.get(0);
            Policy pol = bestPolicy(policies(p));
            PriceInsight ins = new PriceInsight(offers, 90);
            int fastest = Integer.MAX_VALUE;
            for (Offer o : offers) if (o.isInStock()) fastest = Math.min(fastest, o.getDeliveryDays());

            prices.add(best == null ? Double.MAX_VALUE : best.getTotalCost());
            ratings.add(averageRating(offers));
            discounts.add(best == null ? 0.0 : best.getDiscountPercent());
            delivery.add((double) fastest);
            returns.add(pol == null ? 0.0 : pol.flexibilityScore());
            value.add(averageRating(offers) * 20 + (best == null ? 0 : best.getDiscountPercent())
                    + Math.max(-5, Math.min(5, ins.percentBelowAverage())));
            insights.add(ins);
            bestPolicies.add(pol);

            Map<String, Object> col = summary(p);
            col.put("verdict", ins.getVerdict());
            cols.add(col);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("Best price", fmtEach(prices, "₹%,.0f"), winner(prices, false)));
        List<Object> stores = new ArrayList<>();
        for (Map<String, Object> c : cols) stores.add(c.get("bestMarketplace"));
        rows.add(row("Cheapest at", stores, -1));
        rows.add(row("Discount off MRP", fmtEach(discounts, "%.0f%%"), winner(discounts, true)));
        rows.add(row("Average rating", fmtEach(ratings, "%.1f ★"), winner(ratings, true)));
        List<Object> days = new ArrayList<>();
        for (double d : delivery) days.add(d >= Integer.MAX_VALUE ? "—" : (int) d + (d == 1 ? " day" : " days"));
        rows.add(row("Fastest delivery", days, winner(delivery, false)));
        List<Object> policyText = new ArrayList<>();
        for (Policy pol : bestPolicies) policyText.add(pol == null ? "—" : pol.summary() + " (" + pol.getMarketplace() + ")");
        rows.add(row("Best return policy", policyText, winner(returns, true)));
        List<Object> lows = new ArrayList<>(), verdicts = new ArrayList<>();
        for (PriceInsight i : insights) {
            lows.add(String.format("₹%,.0f", i.getLowest()));
            verdicts.add(i.getVerdict().name());
        }
        rows.add(row("90-day low", lows, -1));
        rows.add(row("Price verdict", verdicts, -1));

        // Spec rows: the union of spec labels, in first-seen order.
        Set<String> labels = new java.util.LinkedHashSet<>();
        for (Product p : products) labels.addAll(p.getSpecs().keySet());
        List<Map<String, Object>> specRows = new ArrayList<>();
        for (String label : labels) {
            List<Object> vals = new ArrayList<>();
            for (Product p : products) vals.add(p.getSpecs().getOrDefault(label, "—"));
            specRows.add(row(label, vals, -1));
        }

        int cheapest = indexOfMin(prices), topRated = indexOfMax(ratings), bestValue = indexOfMax(value);
        StringBuilder verdict = new StringBuilder();
        verdict.append(String.format("%s is the cheapest at ₹%,.0f. ",
                products.get(cheapest).getShortName(), prices.get(cheapest)));
        if (topRated != cheapest) {
            verdict.append(String.format("%s is rated highest (%.1f★). ",
                    products.get(topRated).getShortName(), ratings.get(topRated)));
        }
        verdict.append(String.format("Best overall value right now: %s.", products.get(bestValue).getShortName()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("products", cols);
        m.put("rows", rows);
        m.put("specRows", specRows);
        m.put("bestValueIndex", bestValue);
        m.put("verdict", verdict.toString());
        return m;
    }

    private static Map<String, Object> row(String label, List<Object> values, int winner) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("values", values);
        m.put("winner", winner);
        return m;
    }

    private static List<Object> fmtEach(List<Double> values, String fmt) {
        List<Object> out = new ArrayList<>();
        for (double v : values) out.add(v == Double.MAX_VALUE || v == Integer.MAX_VALUE ? "—" : String.format(fmt, v));
        return out;
    }

    /** Index of the single best value, or -1 when the best value is shared (no winner on a tie). */
    private static int winner(List<Double> v, boolean higherIsBetter) {
        int idx = higherIsBetter ? indexOfMax(v) : indexOfMin(v);
        for (int i = 0; i < v.size(); i++) {
            if (i != idx && v.get(i).equals(v.get(idx))) return -1;
        }
        return idx;
    }

    private static int indexOfMin(List<Double> v) {
        int idx = 0;
        for (int i = 1; i < v.size(); i++) if (v.get(i) < v.get(idx)) idx = i;
        return idx;
    }

    private static int indexOfMax(List<Double> v) {
        int idx = 0;
        for (int i = 1; i < v.size(); i++) if (v.get(i) > v.get(idx)) idx = i;
        return idx;
    }

    // ---------------------------------------------------- recommendations

    /**
     * Scores products for a user, optionally narrowed by a search query, budget and category.
     * Score = value (discount) + quality (rating) + timing (below its usual price) + personal interest.
     */
    public List<Map<String, Object>> recommend(User user, String query, Double budget,
                                               Category category, int limit) {
        Map<String, Double> interest = new HashMap<>();   // tag/brand/category -> weight
        Set<String> owned = new HashSet<>();
        Map<String, String> reasonSource = new HashMap<>(); // tag -> product that created the interest

        if (user != null) {
            for (Order o : orders.getOrders(user.getId(), null, null)) {
                if (o.getStatus() == OrderStatus.CANCELLED) continue;
                Product p = find(o.getProductId());
                if (p == null) continue;
                owned.add(p.getId());
                addInterest(interest, reasonSource, p, 1.0, "your " + p.getShortName() + " order");
            }
            int rank = 0;
            for (String id : user.getRecentlyViewed()) {
                Product p = find(id);
                if (p != null) addInterest(interest, reasonSource, p, 1.5 - rank++ * 0.1, "you viewed " + p.getShortName());
            }
        }

        List<Map<String, Object>> scored = new ArrayList<>();
        for (Product p : catalog) {
            if (category != null && p.getCategory() != category) continue;
            if (query != null && !query.isBlank() && !p.matches(query)) continue;
            List<Offer> offers = offersFor(p);
            if (offers.isEmpty() || !offers.get(0).isInStock()) continue;
            Offer best = offers.get(0);
            if (budget != null && best.getTotalCost() > budget) continue;

            PriceInsight ins = new PriceInsight(offers, 90);
            double rating = averageRating(offers);
            double score = best.getDiscountPercent() * 0.6 + (rating - 3.5) * 25
                    + Math.max(-5, Math.min(10, ins.percentBelowAverage())) * 2;

            List<String> reasons = new ArrayList<>();
            double personal = 0;
            String because = null;
            for (String key : interestKeys(p)) {
                Double w = interest.get(key);
                if (w != null) {
                    personal += w;
                    if (because == null) because = reasonSource.get(key);
                }
            }
            if (owned.contains(p.getId())) personal -= 6;   // don't push what they already bought
            score += Math.min(personal, 6) * 6;

            if (because != null && !owned.contains(p.getId())) reasons.add("Because " + because);
            if (ins.getVerdict() == PriceInsight.Verdict.GREAT) reasons.add("Near its 90-day low");
            else if (ins.getVerdict() == PriceInsight.Verdict.GOOD) reasons.add("Below its usual price");
            if (rating >= 4.4) reasons.add(String.format("Top rated %.1f★", rating));
            if (best.getDiscountPercent() >= 25) reasons.add(best.getDiscountPercent() + "% off MRP");
            if (reasons.isEmpty()) reasons.add("Best deal on " + best.getMarketplace());

            Map<String, Object> entry = summary(p);
            entry.put("score", Math.round(score));
            entry.put("reasons", reasons.subList(0, Math.min(3, reasons.size())));
            entry.put("verdict", ins.getVerdict());
            scored.add(entry);
        }
        scored.sort(Comparator.comparingDouble((Map<String, Object> e) -> ((Number) e.get("score")).doubleValue()).reversed());
        return scored.subList(0, Math.min(limit, scored.size()));
    }

    private static void addInterest(Map<String, Double> interest, Map<String, String> source,
                                    Product p, double weight, String reason) {
        for (String key : interestKeys(p)) {
            boolean broad = key.startsWith("cat:");
            interest.merge(key, broad ? weight * 0.3 : weight, Double::sum);
            if (!broad) source.putIfAbsent(key, reason);
        }
    }

    /** Signals a product contributes to a user's taste profile. */
    private static List<String> interestKeys(Product p) {
        List<String> keys = new ArrayList<>();
        keys.add("brand:" + p.getBrand());
        keys.add("cat:" + p.getCategory());
        for (String t : p.getTags()) {
            if (!t.equals("gift") && !t.equals("budget") && !t.equals("premium")) keys.add("tag:" + t);
        }
        return keys;
    }

    // -------------------------------------------------------------- deals

    /** Products whose best price is furthest below their 30-day average right now. */
    public List<Map<String, Object>> deals(int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Product p : catalog) {
            List<Offer> offers = offersFor(p);
            if (offers.isEmpty() || !offers.get(0).isInStock()) continue;
            PriceInsight ins = new PriceInsight(offers, 90);
            Map<String, Object> e = summary(p);
            e.put("belowAverage", Math.round(ins.percentBelowAverage() * 10) / 10.0);
            e.put("average30", Math.round(ins.getAverage30()));
            e.put("verdict", ins.getVerdict());
            list.add(e);
        }
        list.sort(Comparator.comparingDouble((Map<String, Object> e) -> ((Number) e.get("belowAverage")).doubleValue()).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }
}
