package com.shopsync.pricing;

import com.shopsync.model.Category;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.shopsync.pricing.OfferRule.Type.BANK;
import static com.shopsync.pricing.OfferRule.Type.COD_FEE;
import static com.shopsync.pricing.OfferRule.Type.COUPON;
import static com.shopsync.pricing.OfferRule.Type.EXCHANGE_BONUS;
import static com.shopsync.pricing.OfferRule.Type.PLATFORM_FEE;

/**
 * Bank offers, coupons, fees and exchange bonuses per store.
 *
 * NOTE: illustrative values modelled on the kinds of offers Indian marketplaces run.
 * Real offers change daily; a production version would load them from each store.
 */
public final class OfferBook {

    private OfferBook() { }

    public static final List<String> BANKS = Collections.unmodifiableList(
            Arrays.asList("HDFC", "ICICI", "SBI", "Axis", "Kotak"));

    public static final String DISCLAIMER =
            "Offers shown are illustrative examples and change often. Always check the store's checkout page.";

    public static final List<OfferRule> RULES = Collections.unmodifiableList(Arrays.asList(
            // ------------------------------------------------------------ Amazon
            rule("Amazon", BANK, "HDFC", Category.ELECTRONICS, null, 5000, 10, 0, 1500, false, "HDFC card: 10% instant discount (up to ₹1,500)"),
            rule("Amazon", BANK, "SBI", null, null, 3000, 7.5, 0, 1000, false, "SBI card: 7.5% instant discount (up to ₹1,000)"),
            rule("Amazon", COUPON, null, Category.ELECTRONICS, null, 1000, 2, 0, 500, false, "Clip coupon: 2% off (up to ₹500)"),
            rule("Amazon", COD_FEE, null, null, null, 0, 0, 10, 0, false, "Pay on Delivery fee"),
            rule("Amazon", EXCHANGE_BONUS, null, null, "phone", 0, 0, 2000, 0, false, "Exchange bonus on phones"),
            rule("Amazon", EXCHANGE_BONUS, null, null, "laptop", 0, 0, 2500, 0, false, "Exchange bonus on laptops"),

            // ------------------------------------------------------------ Flipkart
            rule("Flipkart", BANK, "Axis", null, null, 0, 5, 0, 4000, true, "Flipkart Axis card: 5% cashback"),
            rule("Flipkart", BANK, "ICICI", Category.ELECTRONICS, null, 4000, 10, 0, 1250, false, "ICICI card: 10% instant discount (up to ₹1,250)"),
            rule("Flipkart", COUPON, null, Category.FASHION, null, 999, 0, 100, 0, false, "Coupon: ₹100 off on orders above ₹999"),
            rule("Flipkart", PLATFORM_FEE, null, null, null, 0, 0, 7, 0, false, "Platform fee"),
            rule("Flipkart", COD_FEE, null, null, null, 0, 0, 10, 0, false, "Cash on delivery fee"),
            rule("Flipkart", EXCHANGE_BONUS, null, null, "phone", 0, 0, 1500, 0, false, "Exchange bonus on phones"),
            rule("Flipkart", EXCHANGE_BONUS, null, null, "laptop", 0, 0, 2000, 0, false, "Exchange bonus on laptops"),

            // ------------------------------------------------------------ Myntra
            rule("Myntra", BANK, "Kotak", null, null, 3000, 10, 0, 1000, false, "Kotak card: 10% instant discount (up to ₹1,000)"),
            rule("Myntra", BANK, "HDFC", null, null, 2500, 10, 0, 750, false, "HDFC card: 10% instant discount (up to ₹750)"),
            rule("Myntra", COUPON, null, null, null, 1999, 0, 200, 0, false, "Coupon MYNTRA200: ₹200 off above ₹1,999"),
            rule("Myntra", PLATFORM_FEE, null, null, null, 0, 0, 20, 0, false, "Platform fee"),
            rule("Myntra", COD_FEE, null, null, null, 0, 0, 10, 0, false, "Cash on delivery fee"),

            // ------------------------------------------------------------ Croma
            rule("Croma", BANK, "ICICI", null, null, 10000, 0, 1000, 0, false, "ICICI card: flat ₹1,000 off above ₹10,000"),
            rule("Croma", BANK, "HDFC", null, null, 5000, 5, 0, 2000, false, "HDFC card: 5% instant discount (up to ₹2,000)"),
            rule("Croma", EXCHANGE_BONUS, null, null, "phone", 0, 0, 2500, 0, false, "Exchange bonus on phones"),
            rule("Croma", EXCHANGE_BONUS, null, null, "laptop", 0, 0, 3000, 0, false, "Exchange bonus on laptops"),
            rule("Croma", EXCHANGE_BONUS, null, null, "tv", 0, 0, 2000, 0, false, "Exchange bonus on TVs")
    ));

    private static OfferRule rule(String store, OfferRule.Type type, String bank, Category cat, String tag, double minOrder,
                                  double percent, double flat, double cap, boolean cashback, String label) {
        return new OfferRule(store, type, bank, cat, tag, minOrder, percent, flat, cap, cashback, label);
    }
}
