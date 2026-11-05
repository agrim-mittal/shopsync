package com.shopsync.data;

import com.shopsync.model.Category;
import com.shopsync.model.Policy;

import java.util.HashMap;
import java.util.Map;

/**
 * Buyer policies per marketplace and category.
 *
 * NOTE: these are typical, illustrative values modelled on how Indian
 * marketplaces generally work. Real policies change often and vary by seller,
 * so the app tells users to confirm on the product page before buying.
 */
public final class PolicyBook {

    private static final Map<String, Policy> POLICIES = new HashMap<>();

    static {
        // ------------------------------------------------------------ Amazon
        put(new Policy("Amazon", Category.ELECTRONICS, 7, "Replacement only", false, true,
                "Brand warranty (1 year typical)", true, true,
                "Replacement for damaged, defective or wrong items",
                "Exchange offer on phones and laptops",
                "Pay on Delivery on most orders"));
        put(new Policy("Amazon", Category.FASHION, 10, "Refund or exchange", true, true,
                "Not applicable", false, false,
                "Free returns with doorstep pickup",
                "Size exchange available"));
        put(new Policy("Amazon", Category.HOME, 10, "Refund or replacement", true, true,
                "Brand warranty", false, true,
                "Installation on select appliances",
                "Return if damaged or not as described"));

        // ---------------------------------------------------------- Flipkart
        put(new Policy("Flipkart", Category.ELECTRONICS, 7, "Replacement only", false, true,
                "Brand warranty (1 year typical)", true, true,
                "Open-box delivery: check the item before accepting",
                "Exchange offer on phones and laptops",
                "Complete Mobile Protection add-on"));
        put(new Policy("Flipkart", Category.FASHION, 10, "Refund or exchange", true, true,
                "Not applicable", false, false,
                "Easy returns with pickup",
                "Size exchange on most items"));
        put(new Policy("Flipkart", Category.HOME, 7, "Refund or replacement", true, true,
                "Brand warranty", false, true,
                "Free installation on large appliances"));

        // ------------------------------------------------------------ Myntra
        put(new Policy("Myntra", Category.FASHION, 14, "Refund or exchange", true, true,
                "Not applicable", false, false,
                "Longest return window for fashion",
                "Try & Buy on select pincodes",
                "Instant refund to Myntra credit"));
        put(new Policy("Myntra", Category.ELECTRONICS, 7, "Refund or exchange", true, true,
                "Brand warranty", false, false,
                "Only lifestyle electronics (earbuds, watches)",
                "Must be unused with tags and box"));
        put(new Policy("Myntra", Category.HOME, 7, "Refund or exchange", true, true,
                "Brand warranty", false, false));

        // ------------------------------------------------------------- Croma
        put(new Policy("Croma", Category.ELECTRONICS, 7, "Replacement for defects", false, true,
                "Brand warranty + optional ZipCare extended warranty", true, true,
                "Store pickup and in-store returns",
                "Exchange old devices at any Croma store",
                "Free delivery on all orders"));
        put(new Policy("Croma", Category.HOME, 7, "Replacement for defects", false, true,
                "Brand warranty + optional ZipCare", true, true,
                "Free demo and installation on appliances",
                "Store pickup available"));
        put(new Policy("Croma", Category.FASHION, 0, "Not sold", false, false,
                "Not applicable", false, false));
    }

    private PolicyBook() { }

    private static void put(Policy p) {
        POLICIES.put(p.getMarketplace() + "|" + p.getCategory(), p);
    }

    public static Policy get(String marketplace, Category category) {
        return POLICIES.get(marketplace + "|" + category);
    }

    public static final String DISCLAIMER =
            "Policies shown are typical examples and can differ by seller. Always confirm on the product page.";
}
