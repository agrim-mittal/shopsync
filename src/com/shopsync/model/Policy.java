package com.shopsync.model;

import com.shopsync.util.JsonSerializable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One marketplace's buyer policies for one product category:
 * returns, cash on delivery, warranty, exchange and EMI.
 */
public class Policy implements JsonSerializable {

    private final String marketplace;
    private final Category category;
    private final int returnDays;
    private final String returnType;        // e.g. "Replacement only", "Refund or exchange"
    private final boolean refundAllowed;    // can you get your money back, or only a replacement?
    private final boolean cashOnDelivery;
    private final String warranty;
    private final boolean exchangeOffer;
    private final boolean noCostEmi;
    private final List<String> highlights;

    public Policy(String marketplace, Category category, int returnDays, String returnType,
                  boolean refundAllowed, boolean cashOnDelivery, String warranty,
                  boolean exchangeOffer, boolean noCostEmi, String... highlights) {
        this.marketplace = marketplace;
        this.category = category;
        this.returnDays = returnDays;
        this.returnType = returnType;
        this.refundAllowed = refundAllowed;
        this.cashOnDelivery = cashOnDelivery;
        this.warranty = warranty;
        this.exchangeOffer = exchangeOffer;
        this.noCostEmi = noCostEmi;
        this.highlights = Arrays.asList(highlights);
    }

    /** Higher = more buyer-friendly. Used to pick the "most flexible" store. */
    public int flexibilityScore() {
        return returnDays * 2 + (refundAllowed ? 10 : 0) + (cashOnDelivery ? 3 : 0)
                + (exchangeOffer ? 2 : 0) + (noCostEmi ? 2 : 0);
    }

    public String getMarketplace() { return marketplace; }
    public Category getCategory() { return category; }
    public int getReturnDays() { return returnDays; }
    public String getReturnType() { return returnType; }
    public boolean isRefundAllowed() { return refundAllowed; }
    public boolean isCashOnDelivery() { return cashOnDelivery; }
    public String getWarranty() { return warranty; }
    public boolean hasExchangeOffer() { return exchangeOffer; }
    public boolean hasNoCostEmi() { return noCostEmi; }
    public List<String> getHighlights() { return highlights; }

    /** One-line human summary, e.g. "10-day refund or exchange". */
    public String summary() {
        return returnDays + "-day " + returnType.toLowerCase();
    }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("marketplace", marketplace);
        m.put("category", category.getLabel());
        m.put("returnDays", returnDays);
        m.put("returnType", returnType);
        m.put("refundAllowed", refundAllowed);
        m.put("summary", summary());
        m.put("cashOnDelivery", cashOnDelivery);
        m.put("warranty", warranty);
        m.put("exchangeOffer", exchangeOffer);
        m.put("noCostEmi", noCostEmi);
        m.put("highlights", highlights);
        m.put("score", flexibilityScore());
        return m;
    }
}
