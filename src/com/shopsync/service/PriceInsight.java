package com.shopsync.service;

import com.shopsync.marketplace.SimulatedMarketplace;
import com.shopsync.model.Offer;
import com.shopsync.model.PricePoint;
import com.shopsync.util.JsonSerializable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Analyses a product's price history across all stores that sell it and turns
 * it into a buying verdict ("Great time to buy", "Wait for a drop"...).
 */
public class PriceInsight implements JsonSerializable {

    public enum Verdict { GREAT, GOOD, FAIR, WAIT }

    private final Map<String, List<PricePoint>> seriesByStore = new LinkedHashMap<>();
    private final List<PricePoint> bestPriceSeries = new ArrayList<>();
    private double current, lowest, highest, average30;
    private LocalDate lowestDate;
    private String lowestStore;
    private Verdict verdict;
    private String verdictText;

    public PriceInsight(List<Offer> offers, int days) {
        TreeMap<LocalDate, Double> bestPerDay = new TreeMap<>();
        Map<LocalDate, String> bestStorePerDay = new LinkedHashMap<>();

        for (Offer o : offers) {
            List<PricePoint> series = o.getDailyHistory(days);
            seriesByStore.put(o.getMarketplace(), series);
            for (PricePoint p : series) {
                Double best = bestPerDay.get(p.getDate());
                if (best == null || p.getPrice() < best) {
                    bestPerDay.put(p.getDate(), p.getPrice());
                    bestStorePerDay.put(p.getDate(), o.getMarketplace());
                }
            }
        }
        if (bestPerDay.isEmpty()) {
            verdict = Verdict.FAIR;
            verdictText = "Not enough price data yet.";
            return;
        }

        lowest = Double.MAX_VALUE;
        highest = 0;
        for (Map.Entry<LocalDate, Double> e : bestPerDay.entrySet()) {
            bestPriceSeries.add(new PricePoint(e.getKey(), e.getValue()));
            if (e.getValue() < lowest) {
                lowest = e.getValue();
                lowestDate = e.getKey();
                lowestStore = bestStorePerDay.get(e.getKey());
            }
            highest = Math.max(highest, e.getValue());
        }
        current = bestPerDay.lastEntry().getValue();

        List<Double> last30 = new ArrayList<>(bestPerDay.tailMap(LocalDate.now().minusDays(30)).values());
        double sum = 0;
        for (double d : last30) sum += d;
        average30 = last30.isEmpty() ? current : sum / last30.size();

        decideVerdict();
    }

    private void decideVerdict() {
        double aboveLow = (current - lowest) / lowest * 100;
        double vsAvg = (current - average30) / average30 * 100;
        boolean lowWasInSale = lowestDate != null
                && !lowestDate.isBefore(LocalDate.now().minusDays(SimulatedMarketplace.SALE_FROM_DAYS_AGO))
                && !lowestDate.isAfter(LocalDate.now().minusDays(SimulatedMarketplace.SALE_TO_DAYS_AGO));

        if (aboveLow <= 2) {
            verdict = Verdict.GREAT;
            verdictText = "Great time to buy. The price is at or near its lowest in 90 days.";
        } else if (vsAvg <= -2) {
            verdict = Verdict.GOOD;
            verdictText = String.format("Good price. It's %.0f%% below the 30-day average.", -vsAvg);
        } else if (vsAvg >= 4) {
            verdict = Verdict.WAIT;
            verdictText = String.format("Consider waiting. The price is %.0f%% above its 30-day average", vsAvg)
                    + (lowWasInSale ? " and was much lower during the festive sale." : ".");
        } else {
            verdict = Verdict.FAIR;
            verdictText = String.format("Fair price. Within the usual range, %.0f%% above the 90-day low", aboveLow)
                    + (lowWasInSale ? " (that low was during the festive sale)." : ".");
        }
    }

    public Verdict getVerdict() { return verdict; }
    public String getVerdictText() { return verdictText; }
    public double getCurrent() { return current; }
    public double getLowest() { return lowest; }
    public LocalDate getLowestDate() { return lowestDate; }
    public String getLowestStore() { return lowestStore; }
    public double getHighest() { return highest; }
    public double getAverage30() { return average30; }

    /** Percentage the current best price is below the 30-day average (negative = above). */
    public double percentBelowAverage() {
        return average30 == 0 ? 0 : (average30 - current) / average30 * 100;
    }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("current", current);
        m.put("lowest", lowest);
        m.put("lowestDate", lowestDate == null ? null : lowestDate.toString());
        m.put("lowestStore", lowestStore);
        m.put("highest", highest);
        m.put("average30", Math.round(average30));
        m.put("verdict", verdict);
        m.put("verdictText", verdictText);
        m.put("saleFromDaysAgo", SimulatedMarketplace.SALE_FROM_DAYS_AGO);
        m.put("saleToDaysAgo", SimulatedMarketplace.SALE_TO_DAYS_AGO);
        m.put("series", seriesByStore);
        m.put("best", bestPriceSeries);
        return m;
    }
}
