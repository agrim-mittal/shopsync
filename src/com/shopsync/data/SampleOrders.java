package com.shopsync.data;

import com.shopsync.inbox.ImportedOrderStore;
import com.shopsync.marketplace.Marketplace;
import com.shopsync.model.Order;
import com.shopsync.model.OrderStatus;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

/**
 * Gives every new account a realistic set of past and in-progress orders so the
 * tracking dashboard has something to show straight away.
 * (In a real app these would be imported from each marketplace account.)
 */
public final class SampleOrders {

    private static final Random RANDOM = new Random();

    private SampleOrders() { }

    /** Current orders plus past purchases (for warranty tracking and monthly spending history). */
    public static void loadFor(String userId, Map<String, Marketplace> marketplaces, ImportedOrderStore imported) {
        loadFor(userId, marketplaces);
        LocalDateTime now = LocalDateTime.now();

        // Delivered 6 days ago on a 7-day replacement policy: return window closes tomorrow.
        historic(marketplaces, new Order(amazonId(), userId, "Amazon", "P06", "Apple AirPods Pro (2nd Gen)", "🎧",
                1, 22900, now.minusDays(9), LocalDate.now().minusDays(6), "Amazon Shipping"), 3);
        // Delivered ~350 days ago: 12-month warranty ends in about two weeks.
        historic(marketplaces, new Order(amazonId(), userId, "Amazon", "P05", "boAt Airdopes 141 TWS Earbuds", "🎧",
                1, 1199, now.minusDays(353), LocalDate.now().minusDays(350), "Delhivery"), 3);
        // Warranty already expired.
        historic(marketplaces, new Order(flipkartId(), userId, "Flipkart", "P07", "HP Pavilion 15 Laptop (i5, 16 GB, 512 GB SSD)", "💻",
                1, 61490, now.minusDays(404), LocalDate.now().minusDays(400), "Ekart Logistics"), 4);
        // Spread over recent months, for the spending pattern.
        historic(marketplaces, new Order(flipkartId(), userId, "Flipkart", "P03", "OnePlus Nord CE4 (8 GB / 128 GB)", "📱",
                1, 24499, now.minusDays(98), LocalDate.now().minusDays(95), "Ekart Logistics"), 3);
        historic(marketplaces, new Order(myntraId(), userId, "Myntra", "P12", "Adidas Runfalcon 3.0 Running Shoes", "👟",
                1, 3349, now.minusDays(64), LocalDate.now().minusDays(60), "Myntra Logistics"), 4);
        historic(marketplaces, new Order(amazonId(), userId, "Amazon", "P17", "Prestige Svachh 5 L Pressure Cooker", "🍲",
                1, 1799, now.minusDays(127), LocalDate.now().minusDays(124), "Amazon Shipping"), 3);
        historic(marketplaces, new Order(myntraId(), userId, "Myntra", "P15", "Ray-Ban Aviator Classic Sunglasses", "🕶️",
                1, 7290, now.minusDays(156), LocalDate.now().minusDays(152), "Myntra Logistics"), 4);
        historic(marketplaces, new Order(cromaId(), userId, "Croma", "P10", "Logitech MX Master 3S Mouse", "🖱️",
                1, 8790, now.minusDays(38), LocalDate.now().minusDays(35), "Blue Dart"), 3);

        // Orders from other stores, as if imported from emails/SMS earlier.
        importedHistoric(imported, userId, "NYK" + (10_000_000 + RANDOM.nextInt(89_999_999)), "Nykaa",
                "Maybelline Fit Me Foundation + Kajal", 1249, now.minusDays(33), 3, "Email");
        importedHistoric(imported, userId, "ORD" + (100_000_000 + RANDOM.nextInt(899_999_999)), "Blinkit",
                "Weekly groceries", 1386, now.minusDays(12), 0, "SMS");
        importedHistoric(imported, userId, "FN" + (1_000_000 + RANDOM.nextInt(8_999_999)), "AJIO",
                "Levi's Men Slim Fit T-shirt", 1099, now.minusDays(71), 4, "Email");
    }

    /** A delivered order from the past: each stage a day or so apart, delivered after {@code days}. */
    private static void historic(Map<String, Marketplace> marketplaces, Order order, int days) {
        LocalDateTime t = order.getOrderedOn();
        double step = days * 24.0 / 4;
        while (order.getStatus() != OrderStatus.DELIVERED) {
            t = t.plusMinutes((long) (step * 60));
            OrderStatus next = order.getStatus().next();
            order.advance(TrackingMessages.locationFor(next), TrackingMessages.messageFor(next, order), t);
        }
        marketplaces.get(order.getMarketplace()).addOrder(order);
    }

    private static void importedHistoric(ImportedOrderStore imported, String userId, String id, String store, String product,
                                         double amount, LocalDateTime placed, int deliveryDays, String source) {
        Order o = new Order(id, userId, store, null, product, "📦", 1, amount, placed,
                placed.toLocalDate().plusDays(Math.max(deliveryDays, 0)), null, source, OrderStatus.PLACED,
                "Placed (read from " + source.toLowerCase() + ")", "Reported by " + source.toLowerCase());
        o.applyReportedStatus(OrderStatus.DELIVERED, "Delivered (read from " + source.toLowerCase() + ")",
                "Your address", placed.plusDays(deliveryDays).plusHours(deliveryDays == 0 ? 1 : 0));
        imported.add(o);
    }

    public static void loadFor(String userId, Map<String, Marketplace> marketplaces) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        add(marketplaces, new Order(amazonId(), userId, "Amazon", "P04", "Sony WH-1000XM5 Wireless Headphones", "🎧",
                1, 26990, now.minusDays(1).minusHours(3), today.plusDays(1), "Amazon Shipping"), OrderStatus.SHIPPED, now);

        add(marketplaces, new Order(flipkartId(), userId, "Flipkart", "P13", "Levi's 511 Slim Fit Jeans", "👖",
                2, 4798, now.minusDays(2), today, "Ekart Logistics"), OrderStatus.OUT_FOR_DELIVERY, now);

        add(marketplaces, new Order(myntraId(), userId, "Myntra", "P11", "Nike Air Max SC Sneakers", "👟",
                1, 5795, now.minusHours(5), today.plusDays(4), "Myntra Logistics"), OrderStatus.PLACED, now);

        add(marketplaces, new Order(cromaId(), userId, "Croma", "P16", "Philips Air Fryer HD9252 (4.1 L)", "🍟",
                1, 7149, now.minusDays(1), today.plusDays(2), "Blue Dart"), OrderStatus.PACKED, now);

        add(marketplaces, new Order(amazonId(), userId, "Amazon", "P18", "Milton Thermosteel Flask (1 L)", "🧴",
                2, 1798, now.minusDays(6), today.minusDays(3), "Amazon Shipping"), OrderStatus.DELIVERED, now);

        add(marketplaces, new Order(flipkartId(), userId, "Flipkart", "P09", "Noise ColorFit Pro 5 Smartwatch", "⌚",
                1, 3899, now.minusDays(9), today.minusDays(5), "Ekart Logistics"), OrderStatus.DELIVERED, now);

        Order cancelled = new Order(myntraId(), userId, "Myntra", "P14", "Puma Essentials Hoodie", "🧥",
                1, 1849, now.minusDays(4), today.minusDays(1), "Myntra Logistics");
        cancelled.cancel("Cancelled by customer before shipping", now.minusDays(4).plusHours(2));
        marketplaces.get("Myntra").addOrder(cancelled);

        add(marketplaces, new Order(amazonId(), userId, "Amazon", "P10", "Logitech MX Master 3S Mouse", "🖱️",
                1, 8995, now.minusHours(20), today.plusDays(2), "Delhivery"), OrderStatus.PACKED, now);
    }

    /** Fast-forwards an order through its stages so it already has a tracking history. */
    private static void add(Map<String, Marketplace> marketplaces, Order order,
                            OrderStatus target, LocalDateTime now) {
        LocalDateTime t = order.getOrderedOn();
        long hoursAvailable = Math.max(1, Duration.between(t, now).toHours());
        int steps = target.getProgress() / 25;
        long gap = Math.max(1, hoursAvailable / (steps + 1));

        while (order.getStatus() != target) {
            t = t.plusHours(gap);
            OrderStatus next = order.getStatus().next();
            order.advance(TrackingMessages.locationFor(next), TrackingMessages.messageFor(next, order), t);
        }
        marketplaces.get(order.getMarketplace()).addOrder(order);
    }

    // Order ID formats that look like each marketplace's real ones.
    public static String amazonId() {
        return String.format("403-%07d-%07d", RANDOM.nextInt(10_000_000), RANDOM.nextInt(10_000_000));
    }

    public static String flipkartId() {
        return String.format("OD4313%011d", (long) (RANDOM.nextDouble() * 1e11));
    }

    public static String myntraId() {
        return String.format("MYN10%08d", RANDOM.nextInt(100_000_000));
    }

    public static String cromaId() {
        return String.format("CR-%09d", RANDOM.nextInt(1_000_000_000));
    }

    public static String idFor(String marketplace) {
        switch (marketplace) {
            case "Amazon": return amazonId();
            case "Flipkart": return flipkartId();
            case "Myntra": return myntraId();
            default: return cromaId();
        }
    }
}
