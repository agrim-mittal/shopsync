package com.shopsync.budget;

import com.shopsync.model.Alert;
import com.shopsync.model.Order;
import com.shopsync.model.OrderStatus;
import com.shopsync.service.LiveUpdateEngine;
import com.shopsync.service.OrderCategories;
import com.shopsync.service.OrderTrackingService;
import com.shopsync.service.ProductService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shopping budget guardian.
 *
 *  - Users set a monthly total budget and optional per-category budgets.
 *  - Spending comes from every order: marketplace orders and orders imported from emails/SMS
 *    (cancelled orders don't count), grouped by the month they were placed.
 *  - Warnings at 70%, 90% and 100% of each budget (once per budget, threshold and month).
 *  - Six-month spending pattern, month-end projection and remaining daily capacity.
 */
public class BudgetService {

    public static final String TOTAL = "Total";
    public static final int[] THRESHOLDS = {70, 90, 100};
    private static final int HISTORY_MONTHS = 6;

    private final OrderTrackingService orders;
    private final ProductService products;
    private final LiveUpdateEngine live;
    private final Map<String, Map<String, Double>> budgets = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public BudgetService(OrderTrackingService orders, ProductService products, LiveUpdateEngine live) {
        this.orders = orders;
        this.products = products;
        this.live = live;
    }

    // =============================================================== budgets

    public Map<String, Double> budgets(String userId) {
        return new LinkedHashMap<>(budgets.getOrDefault(userId, new LinkedHashMap<>()));
    }

    /** Replaces the user's budgets. A value of 0 (or missing) means "no budget" for that line. */
    public void setBudgets(String userId, Map<String, Double> values) {
        Map<String, Double> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : values.entrySet()) {
            String key = e.getKey();
            if (!key.equals(TOTAL) && !OrderCategories.ALL.contains(key)) continue;
            double v = e.getValue() == null ? 0 : e.getValue();
            if (v < 0 || v > 10_000_000) throw new IllegalArgumentException("Budgets must be between ₹0 and ₹1 crore");
            if (v > 0) clean.put(key, (double) Math.round(v));
        }
        budgets.put(userId, clean);
    }

    // =============================================================== spending

    /** month → category → amount, for non-cancelled orders. */
    private Map<YearMonth, Map<String, Double>> spending(String userId) {
        Map<YearMonth, Map<String, Double>> out = new LinkedHashMap<>();
        for (Order o : orders.getOrders(userId, null, null)) {
            if (o.getStatus() == OrderStatus.CANCELLED || o.getAmount() <= 0) continue;
            YearMonth ym = YearMonth.from(o.getOrderedOn());
            out.computeIfAbsent(ym, k -> new LinkedHashMap<>())
               .merge(OrderCategories.of(o, products), o.getAmount(), Double::sum);
        }
        return out;
    }

    private static double sum(Map<String, Double> m) {
        double s = 0;
        if (m != null) for (double v : m.values()) s += v;
        return s;
    }

    public Map<String, Object> summary(String userId) {
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        Map<YearMonth, Map<String, Double>> spend = spending(userId);
        Map<String, Double> thisMonth = spend.getOrDefault(month, new LinkedHashMap<>());
        Map<String, Double> limits = budgets(userId);
        double spentTotal = sum(thisMonth);

        int daysInMonth = month.lengthOfMonth();
        int dayOfMonth = today.getDayOfMonth();
        int daysLeft = daysInMonth - dayOfMonth;

        // history for the pattern chart (oldest first)
        List<Map<String, Object>> history = new ArrayList<>();
        double prevSum = 0;
        int prevMonths = 0;
        for (int i = HISTORY_MONTHS - 1; i >= 0; i--) {
            YearMonth ym = month.minusMonths(i);
            Map<String, Double> cats = spend.getOrDefault(ym, new LinkedHashMap<>());
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("month", ym.toString());
            h.put("label", ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            h.put("total", Math.round(sum(cats)));
            h.put("byCategory", cats);
            history.add(h);
            if (i > 0) { prevSum += sum(cats); prevMonths++; }
        }
        double avgPrev = prevMonths == 0 ? 0 : prevSum / prevMonths;

        // projection: early in the month, lean on the user's usual spending
        double paceProjection = dayOfMonth == 0 ? spentTotal : spentTotal / dayOfMonth * daysInMonth;
        double projected = dayOfMonth < 7 ? Math.max(spentTotal, (paceProjection + avgPrev) / 2) : paceProjection;

        // budget lines: Total first, then categories that have a budget or spending
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(line(TOTAL, limits.get(TOTAL), spentTotal, projected, daysLeft));
        for (String cat : OrderCategories.ALL) {
            Double limit = limits.get(cat);
            double spent = thisMonth.getOrDefault(cat, 0.0);
            if (limit == null && spent == 0) continue;
            double catProjected = dayOfMonth < 7 ? spent : spent / dayOfMonth * daysInMonth;
            lines.add(line(cat, limit, spent, catProjected, daysLeft));
        }

        List<String> insights = insights(limits.get(TOTAL), spentTotal, projected, daysLeft, thisMonth, avgPrev, lines);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", month.toString());
        out.put("monthLabel", month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear());
        out.put("daysLeft", daysLeft);
        out.put("daysInMonth", daysInMonth);
        out.put("spent", Math.round(spentTotal));
        out.put("projected", Math.round(projected));
        out.put("averageMonth", Math.round(avgPrev));
        out.put("budgets", limits);
        out.put("lines", lines);
        out.put("history", history);
        out.put("categories", OrderCategories.ALL);
        out.put("insights", insights);
        return out;
    }

    private static Map<String, Object> line(String name, Double limit, double spent, double projected, int daysLeft) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("budget", limit);
        m.put("spent", Math.round(spent));
        if (limit != null && limit > 0) {
            double pct = spent / limit * 100;
            double remaining = limit - spent;
            m.put("percent", Math.round(pct));
            m.put("remaining", Math.round(remaining));
            m.put("perDay", remaining > 0 ? Math.round(remaining / (daysLeft + 1)) : 0);
            m.put("projectedPercent", Math.round(projected / limit * 100));
            m.put("level", pct >= 100 ? "OVER" : pct >= 90 ? "WARNING" : pct >= 70 ? "WATCH" : "OK");
        } else {
            m.put("level", "NO_BUDGET");
        }
        return m;
    }

    private static List<String> insights(Double totalBudget, double spent, double projected, int daysLeft,
                                         Map<String, Double> thisMonth, double avgPrev, List<Map<String, Object>> lines) {
        List<String> out = new ArrayList<>();
        if (totalBudget != null && totalBudget > 0) {
            double pct = spent / totalBudget * 100;
            double remaining = totalBudget - spent;
            if (remaining > 0) {
                out.add(String.format("You've used %.0f%% of your ₹%,.0f monthly budget. You can spend about ₹%,.0f more, "
                        + "roughly ₹%,.0f a day for the %d day%s left.", pct, totalBudget, remaining,
                        remaining / (daysLeft + 1), daysLeft, daysLeft == 1 ? "" : "s"));
            } else {
                out.add(String.format("You're ₹%,.0f over your ₹%,.0f monthly budget with %d day%s to go.",
                        -remaining, totalBudget, daysLeft, daysLeft == 1 ? "" : "s"));
            }
            if (projected > totalBudget * 1.02 && remaining > 0) {
                out.add(String.format("At this pace you'll spend about ₹%,.0f by month end, %.0f%% of your budget.",
                        projected, projected / totalBudget * 100));
            }
        } else {
            out.add("Set a monthly budget to get warnings at 70%, 90% and 100%.");
        }
        String top = null;
        double topVal = 0;
        for (Map.Entry<String, Double> e : thisMonth.entrySet()) if (e.getValue() > topVal) { top = e.getKey(); topVal = e.getValue(); }
        if (top != null && spent > 0) {
            out.add(String.format("%s is your biggest category this month: ₹%,.0f (%.0f%% of spending).", top, topVal, topVal / spent * 100));
        }
        if (avgPrev > 0 && spent > 0) {
            double ratio = spent / avgPrev;
            if (ratio >= 1.3) out.add(String.format("This month is already %.1f× your average month (₹%,.0f).", ratio, avgPrev));
            else if (ratio <= 0.7) out.add(String.format("You're spending less than usual (average month ₹%,.0f).", avgPrev));
        }
        for (Map<String, Object> l : lines) {
            if (!l.get("name").equals(TOTAL) && "OVER".equals(l.get("level"))) {
                out.add(l.get("name") + " is over its budget.");
            }
        }
        return out;
    }

    // =============================================================== warnings

    /** Background check: alerts at 70%, 90% and 100%, once per budget, threshold and month. */
    public void checkWarnings(Collection<String> userIds) {
        YearMonth month = YearMonth.now();
        for (String uid : userIds) {
            Map<String, Double> limits = budgets(uid);
            if (limits.isEmpty()) continue;
            Map<String, Double> thisMonth = spending(uid).getOrDefault(month, new LinkedHashMap<>());
            for (Map.Entry<String, Double> b : limits.entrySet()) {
                double spent = b.getKey().equals(TOTAL) ? sum(thisMonth) : thisMonth.getOrDefault(b.getKey(), 0.0);
                double pct = spent / b.getValue() * 100;
                int crossed = 0;
                for (int t : THRESHOLDS) if (pct >= t) crossed = t;
                if (crossed == 0) continue;
                // only the highest threshold crossed gets an alert (avoids three alerts at once)
                String key = uid + "|" + month + "|" + b.getKey() + "|" + crossed;
                if (!warned.add(key)) continue;
                for (int t : THRESHOLDS) if (t < crossed) warned.add(uid + "|" + month + "|" + b.getKey() + "|" + t);
                String what = b.getKey().equals(TOTAL) ? "monthly budget" : b.getKey() + " budget";
                String title = crossed >= 100 ? "Over your " + what : crossed + "% of your " + what + " used";
                live.notify(new Alert(Alert.Type.BUDGET, title,
                        String.format("₹%,.0f of ₹%,.0f spent this month", spent, b.getValue()), "ShopSync", uid, null));
            }
        }
    }
}
