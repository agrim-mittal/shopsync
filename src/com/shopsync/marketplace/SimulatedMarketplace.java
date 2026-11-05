package com.shopsync.marketplace;

import com.shopsync.model.Offer;
import com.shopsync.model.Order;
import com.shopsync.model.PricePoint;
import com.shopsync.model.Product;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared behaviour for all simulated marketplaces.
 *
 * Each subclass only decides WHAT it sells and HOW it prices (Template
 * Method pattern): {@link #carries(Product)}, {@link #pricingFactor()},
 * {@link #volatility()} and a few delivery settings. Searching, price
 * fluctuation and order storage are implemented once, here.
 */
public abstract class SimulatedMarketplace implements Marketplace {

    private final String name;
    private final List<Product> catalog;
    private List<Offer> offers;            // created on first use, see offers()
    private final List<Order> orders = new CopyOnWriteArrayList<>();
    protected final Random random;

    protected SimulatedMarketplace(String name, long seed, List<Product> catalog) {
        this.name = name;
        this.random = new Random(seed);
        this.catalog = catalog;
    }

    /**
     * Builds this marketplace's listings the first time they are needed.
     * (Doing it in the constructor would call subclass methods before the
     * subclass is fully constructed, which is a classic Java pitfall.)
     */
    private synchronized List<Offer> offers() {
        if (offers == null) {
            List<Offer> list = new ArrayList<>();
            for (Product p : catalog) {
                if (carries(p)) {
                    list.add(createOffer(p));
                }
            }
            offers = list;
        }
        return offers;
    }

    // ---- hooks each marketplace customises -------------------------------

    /** Whether this marketplace stocks the product. */
    protected abstract boolean carries(Product product);

    /** Multiplier on the product's base price (0.98 = 2% cheaper on average). */
    protected abstract double pricingFactor();

    /** Maximum fraction a price can move in one refresh (0.03 = 3%). */
    protected abstract double volatility();

    /** Fastest delivery this marketplace usually offers, in days. */
    protected abstract int baseDeliveryDays();

    /** Order value at or above which delivery is free. */
    protected abstract double freeDeliveryThreshold();

    /** Seller names used for listings. */
    protected abstract String[] sellers();

    /** Extra discount during the big festive sale (overridden by stores that discount harder). */
    protected double festiveSaleDiscount() {
        return 0.08;
    }

    /** Days of simulated price history created for every listing. */
    public static final int HISTORY_DAYS = 90;
    /** The festive sale ran between these many days ago (inclusive). */
    public static final int SALE_FROM_DAYS_AGO = 40, SALE_TO_DAYS_AGO = 31;

    // ---- shared behaviour -------------------------------------------------

    private Offer createOffer(Product p) {
        double price = roundPrice(p.getBasePrice() * pricingFactor() * (0.95 + random.nextDouble() * 0.10), p);
        double rating = Math.round((3.8 + random.nextDouble() * 1.1) * 10) / 10.0;
        int days = baseDeliveryDays() + random.nextInt(3);
        int reviews = 150 + random.nextInt(p.getBasePrice() < 5000 ? 40000 : 9000);
        String seller = sellers()[random.nextInt(sellers().length)];
        return new Offer(p, name, seller, price, rating, reviews, days,
                price >= freeDeliveryThreshold(), generateHistory(p, price));
    }

    /**
     * Simulates the last {@value #HISTORY_DAYS} days of prices: small random moves every
     * few days, a deep dip during the festive sale and a short mid-quarter deal,
     * ending exactly at today's price.
     */
    private List<PricePoint> generateHistory(Product p, double todayPrice) {
        List<PricePoint> history = new ArrayList<>();
        LocalDate today = LocalDate.now();
        double target = p.getBasePrice() * pricingFactor();
        double level = target * (1.0 + random.nextDouble() * 0.06);

        for (int daysAgo = HISTORY_DAYS - 1; daysAgo >= 1; daysAgo--) {
            if (random.nextDouble() < 0.3) {   // e-commerce prices change every few days, not daily
                double move = (random.nextDouble() * 2 - 1) * volatility();
                double pull = (target - level) / target * 0.15;
                level = level * (1 + move + pull);
            }
            double discount = 0;
            if (daysAgo >= SALE_TO_DAYS_AGO && daysAgo <= SALE_FROM_DAYS_AGO) {
                discount = festiveSaleDiscount() * (0.8 + random.nextDouble() * 0.4);
            } else if (daysAgo >= 60 && daysAgo <= 62) {
                discount = festiveSaleDiscount() / 2;
            }
            history.add(new PricePoint(today.minusDays(daysAgo), roundPrice(level * (1 - discount), p)));
        }
        history.add(new PricePoint(today, todayPrice));
        return history;
    }

    /** Indian retail prices usually end in 9 (e.g. 1,299); keep that look and never exceed MRP. */
    protected double roundPrice(double raw, Product p) {
        double floor = p.getBasePrice() * 0.80;
        double clamped = Math.max(floor, Math.min(raw, p.getMrp()));
        long rounded = Math.round(clamped / 10.0) * 10 - 1;
        return Math.min(rounded, p.getMrp());
    }

    /** Pretends to call a remote server; makes parallel fetching in the service layer worthwhile. */
    protected void simulateNetworkDelay() {
        try {
            Thread.sleep(80 + random.nextInt(170));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Offer> search(String query) {
        simulateNetworkDelay();
        List<Offer> results = new ArrayList<>();
        for (Offer o : offers()) {
            if (o.getProduct().matches(query)) {
                results.add(o);
            }
        }
        return results;
    }

    @Override
    public List<Offer> allOffers() {
        return Collections.unmodifiableList(offers());
    }

    @Override
    public synchronized List<Offer> refreshPrices() {
        List<Offer> drops = new ArrayList<>();
        for (Offer o : offers()) {
            // Most refreshes leave a price untouched; roughly 1 in 7 listings moves.
            if (random.nextDouble() > 0.15) continue;

            Product p = o.getProduct();
            double movement = (random.nextDouble() * 2 - 1) * volatility();
            // Occasional flash sale: a sharper drop.
            if (random.nextDouble() < 0.05) {
                movement = -volatility() * 2.5;
            }
            // Gently pull prices back toward the marketplace's normal level.
            double target = p.getBasePrice() * pricingFactor();
            double pull = (target - o.getPrice()) / target * 0.3;

            double newPrice = roundPrice(o.getPrice() * (1 + movement + pull), p);
            if (newPrice != o.getPrice()) {
                double change = o.updatePrice(newPrice);
                if (change < 0) drops.add(o);
            }
            // Rarely, an item goes out of stock or comes back.
            if (random.nextDouble() < 0.02) {
                o.setInStock(!o.isInStock());
            }
        }
        return drops;
    }

    @Override
    public List<Order> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    @Override
    public void addOrder(Order order) {
        orders.add(order);
    }
}
