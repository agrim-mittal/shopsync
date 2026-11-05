package com.shopsync.service;

import com.shopsync.data.SampleOrders;
import com.shopsync.data.TrackingMessages;
import com.shopsync.marketplace.Marketplace;
import com.shopsync.model.Alert;
import com.shopsync.model.Offer;
import com.shopsync.model.Order;
import com.shopsync.model.OrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background "heartbeat" that keeps the data live:
 *   - every few seconds it refreshes prices on every marketplace
 *   - every few seconds it may move one order to its next tracking stage
 * Each change is pushed into a feed of alerts that the dashboard shows.
 */
public class LiveUpdateEngine {

    private static final int MAX_ALERTS = 120;
    private static final double DROP_ALERT_PERCENT = 4.0;   // only shout about meaningful drops
    /** An order stays at least this long in a stage before moving on, so the demo stays lively for a while. */
    private static final java.time.Duration MIN_TIME_IN_STAGE = java.time.Duration.ofMinutes(3);

    private final Collection<Marketplace> marketplaces;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "live-updates");
        t.setDaemon(true);
        return t;
    });
    private final Deque<Alert> alerts = new ConcurrentLinkedDeque<>();
    private final Random random = new Random();
    private volatile LocalDateTime lastPriceRefresh = LocalDateTime.now();

    public LiveUpdateEngine(Collection<Marketplace> marketplaces) {
        this.marketplaces = marketplaces;
    }

    public void start(int priceRefreshSeconds, int orderUpdateSeconds) {
        scheduler.scheduleAtFixedRate(this::refreshAllPrices, priceRefreshSeconds,
                priceRefreshSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::progressAnOrder, orderUpdateSeconds,
                orderUpdateSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    // ---------------------------------------------------------------- prices

    private void refreshAllPrices() {
        try {
            for (Marketplace m : marketplaces) {
                for (Offer o : m.refreshPrices()) {
                    List<Double> h = o.getLiveHistory();
                    if (h.size() < 2) continue;
                    double before = h.get(h.size() - 2);
                    double after = o.getPrice();
                    double pct = (before - after) / before * 100;
                    if (pct >= DROP_ALERT_PERCENT) {
                        pushAlert(new Alert(Alert.Type.PRICE_DROP,
                                "Price drop: " + o.getProduct().getShortName(),
                                String.format("%s cut the price by %.1f%% to ₹%,.0f", m.getName(), pct, after),
                                m.getName(), null, o.getProduct().getId()));
                    }
                }
            }
            lastPriceRefresh = LocalDateTime.now();
        } catch (RuntimeException e) {
            // Never let an exception kill the scheduled task.
            System.err.println("Price refresh failed: " + e);
        }
    }

    // ---------------------------------------------------------------- orders

    private void progressAnOrder() {
        try {
            List<Order> active = new ArrayList<>();
            LocalDateTime cutoff = LocalDateTime.now().minus(MIN_TIME_IN_STAGE);
            for (Marketplace m : marketplaces) {
                for (Order o : m.getOrders()) {
                    if (!o.getStatus().isFinal() && o.getLastUpdate().isBefore(cutoff)) active.add(o);
                }
            }
            if (active.isEmpty()) return;

            Order o = active.get(random.nextInt(active.size()));
            OrderStatus next = o.getStatus().next();
            if (o.advance(TrackingMessages.locationFor(next), TrackingMessages.messageFor(next, o),
                    LocalDateTime.now())) {
                pushAlert(new Alert(Alert.Type.ORDER_UPDATE,
                        o.getProductName() + ": " + next.getLabel(),
                        o.getMarketplace() + " order " + o.getOrderId(),
                        o.getMarketplace(), o.getUserId(), o.getProductId()));
            }
        } catch (RuntimeException e) {
            System.err.println("Order update failed: " + e);
        }
    }

    /** Demo helper: places a new order for a random in-stock product on a random marketplace. */
    public Order placeRandomOrder(String userId) {
        List<Marketplace> list = new ArrayList<>(marketplaces);
        Marketplace m = list.get(random.nextInt(list.size()));
        List<Offer> offers = new ArrayList<>(m.allOffers());
        offers.removeIf(o -> !o.isInStock());
        if (offers.isEmpty()) return null;
        Offer offer = offers.get(random.nextInt(offers.size()));

        String id = SampleOrders.idFor(m.getName());
        Order order = new Order(id, userId, m.getName(), offer.getProduct().getId(), offer.getProduct().getName(),
                offer.getProduct().getIcon(), 1, offer.getTotalCost(), LocalDateTime.now(),
                LocalDate.now().plusDays(offer.getDeliveryDays()), courierFor(m.getName()));
        m.addOrder(order);
        pushAlert(new Alert(Alert.Type.ORDER_UPDATE, "New order: " + offer.getProduct().getShortName(),
                m.getName() + " order " + id, m.getName(), userId, offer.getProduct().getId()));
        return order;
    }

    private String courierFor(String marketplace) {
        switch (marketplace) {
            case "Amazon": return "Amazon Shipping";
            case "Flipkart": return "Ekart Logistics";
            case "Myntra": return "Myntra Logistics";
            default: return "Blue Dart";
        }
    }

    // ---------------------------------------------------------------- alerts

    /** Adds an alert to the live feed (also used by the email/SMS importer). */
    public void notify(Alert a) {
        pushAlert(a);
    }

    private void pushAlert(Alert a) {
        alerts.addFirst(a);
        while (alerts.size() > MAX_ALERTS) alerts.removeLast();
    }

    /** The latest alerts this user may see (shared price drops + their own order updates). */
    public List<Alert> getAlerts(String userId) {
        List<Alert> visible = new ArrayList<>();
        for (Alert a : alerts) {
            if (a.isVisibleTo(userId)) visible.add(a);
            if (visible.size() >= 40) break;
        }
        return visible;
    }

    public LocalDateTime getLastPriceRefresh() {
        return lastPriceRefresh;
    }
}
