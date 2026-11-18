package com.shopsync.service;

import com.shopsync.model.Order;
import com.shopsync.model.Product;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Works out a spending category for any order, including orders imported from
 * stores ShopSync isn't connected to (where we only know the store and product name).
 * Used by the budget guardian and the warranty manager.
 */
public final class OrderCategories {

    public static final String ELECTRONICS = "Electronics";
    public static final String FASHION = "Fashion";
    public static final String HOME = "Home & Kitchen";
    public static final String BEAUTY = "Beauty";
    public static final String GROCERIES = "Groceries";
    public static final String HEALTH = "Health";
    public static final String OTHER = "Other";

    public static final List<String> ALL = Arrays.asList(ELECTRONICS, FASHION, HOME, BEAUTY, GROCERIES, HEALTH, OTHER);

    private OrderCategories() { }

    public static String of(Order o, ProductService products) {
        if (o.getProductId() != null) {
            Product p = products.find(o.getProductId());
            if (p != null) return p.getCategory().getLabel();
        }
        String store = o.getMarketplace().toLowerCase(Locale.ROOT);
        String name = o.getProductName().toLowerCase(Locale.ROOT);

        if (store.matches(".*(blinkit|zepto|instamart|bigbasket|jiomart|dmart).*")) return GROCERIES;
        if (store.matches(".*(nykaa|purplle|mamaearth|sugar).*")) return BEAUTY;
        if (store.matches(".*(pharmeasy|1mg|netmeds|healthkart).*")) return HEALTH;
        if (store.matches(".*(myntra|ajio|meesho|bewakoof|souled|zara|h&m|uniqlo|westside|lenskart).*")) return FASHION;
        if (store.matches(".*(croma|reliance digital|vijay sales|apple|samsung|oneplus|boat).*")) return ELECTRONICS;
        if (store.matches(".*(pepperfry|urban ladder|ikea).*")) return HOME;

        if (name.matches(".*(phone|laptop|earbud|headphone|watch|tv|charger|mouse|keyboard|speaker|tablet|camera).*")) return ELECTRONICS;
        if (name.matches(".*(shirt|t-shirt|jeans|kurti|dress|shoe|sneaker|hoodie|jacket|saree|glasses|sunglasses).*")) return FASHION;
        if (name.matches(".*(lipstick|primer|serum|cream|shampoo|perfume|makeup|face wash).*")) return BEAUTY;
        if (name.matches(".*(cooker|pan|bottle|flask|fryer|mixer|bedsheet|lamp|kitchen).*")) return HOME;
        if (name.matches(".*(milk|bread|rice|atta|fruit|vegetable|grocer).*")) return GROCERIES;
        return OTHER;
    }
}
