package com.shopsync.pricing;

import com.shopsync.model.Category;
import com.shopsync.model.Product;

/**
 * One checkout adjustment a store applies: a bank offer, coupon, fee or exchange bonus.
 * Amount = {@code flat} + {@code percent} of the price, capped at {@code cap} (0 = no cap),
 * and only when the order value is at least {@code minOrder}.
 */
public class OfferRule {

    public enum Type { BANK, COUPON, PLATFORM_FEE, COD_FEE, EXCHANGE_BONUS }

    final String marketplace;
    final Type type;
    final String bank;          // for BANK offers, e.g. "HDFC"
    final Category category;    // null = every category
    final String tag;           // optional product tag filter, e.g. "phone"
    final double minOrder;
    final double percent;
    final double flat;
    final double cap;
    final boolean cashback;     // credited later rather than taken off at checkout
    final String label;

    OfferRule(String marketplace, Type type, String bank, Category category, String tag, double minOrder,
              double percent, double flat, double cap, boolean cashback, String label) {
        this.marketplace = marketplace;
        this.type = type;
        this.bank = bank;
        this.category = category;
        this.tag = tag;
        this.minOrder = minOrder;
        this.percent = percent;
        this.flat = flat;
        this.cap = cap;
        this.cashback = cashback;
        this.label = label;
    }

    boolean appliesTo(String store, Product p, double price) {
        return marketplace.equals(store)
                && (category == null || p.getCategory() == category)
                && (tag == null || p.hasTag(tag))
                && price >= minOrder;
    }

    double amountFor(double price) {
        double a = flat + price * percent / 100.0;
        if (cap > 0) a = Math.min(a, cap);
        return Math.round(a);
    }
}
