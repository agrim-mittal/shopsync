package com.shopsync.marketplace;

import com.shopsync.model.Offer;
import com.shopsync.model.Order;

import java.util.List;

/**
 * The contract every marketplace connector must follow.
 *
 * ShopSync only talks to marketplaces through this interface, so a simulated
 * connector can later be swapped for a real one (official API or scraper)
 * without changing any other part of the application.
 */
public interface Marketplace {

    /** Display name, e.g. "Amazon". */
    String getName();

    /** Listings whose product matches the search text (blank = everything). */
    List<Offer> search(String query);

    /** Every listing this marketplace has, from the local cache (no network call). */
    List<Offer> allOffers();

    /** The listing for one product, or null if this marketplace doesn't sell it. */
    default Offer findOffer(String productId) {
        for (Offer o : allOffers()) {
            if (o.getProduct().getId().equals(productId)) return o;
        }
        return null;
    }

    /** Orders placed on this marketplace (by all customers). */
    List<Order> getOrders();

    /** Pulls the latest prices from the marketplace. Returns offers whose price dropped. */
    List<Offer> refreshPrices();

    /** Records a new order on this marketplace. */
    void addOrder(Order order);
}
