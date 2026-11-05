package com.shopsync.service;

import com.shopsync.inbox.ImportedOrderStore;
import com.shopsync.marketplace.Marketplace;
import com.shopsync.model.Order;
import com.shopsync.model.OrderStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects one customer's orders into one list: orders from the connected marketplaces
 * plus orders imported from emails/SMS for any other store.
 */
public class OrderTrackingService {

    private final Collection<Marketplace> marketplaces;
    private final ImportedOrderStore imported;

    public OrderTrackingService(Collection<Marketplace> marketplaces, ImportedOrderStore imported) {
        this.marketplaces = marketplaces;
        this.imported = imported;
    }

    /**
     * @param userId      whose orders
     * @param marketplace optional store filter: a marketplace name, "Other" (imported orders from
     *                    other stores) or null/blank/"All" for everything
     * @param filter      "active", "delivered", "cancelled" or null for all
     */
    public List<Order> getOrders(String userId, String marketplace, String filter) {
        List<Order> all = new ArrayList<>();
        for (Marketplace m : marketplaces) {
            for (Order o : m.getOrders()) {
                if (o.getUserId().equals(userId)) all.add(o);
            }
        }
        all.addAll(imported.forUser(userId));
        all.removeIf(o -> !matchesFilter(o.getStatus(), filter) || !matchesStore(o, marketplace));
        // Most recently updated orders first.
        all.sort(Comparator.comparing(Order::getLastUpdate).reversed());
        return all;
    }

    public Order findById(String userId, String orderId) {
        for (Order o : getOrders(userId, null, null)) {
            if (o.getOrderId().equalsIgnoreCase(orderId)) return o;
        }
        return null;
    }

    public Map<String, Object> getSummary(String userId) {
        int total = 0, active = 0, outToday = 0, delivered = 0, importedCount = 0;
        double spent = 0;
        Map<String, Integer> perMarketplace = new LinkedHashMap<>();
        for (Marketplace m : marketplaces) perMarketplace.put(m.getName(), 0);

        for (Order o : getOrders(userId, null, null)) {
            total++;
            perMarketplace.merge(o.getMarketplace(), 1, Integer::sum);
            if (o.isImported()) importedCount++;
            OrderStatus s = o.getStatus();
            if (s != OrderStatus.CANCELLED) spent += o.getAmount();
            if (s == OrderStatus.DELIVERED) delivered++;
            else if (s != OrderStatus.CANCELLED) active++;
            if (s == OrderStatus.OUT_FOR_DELIVERY) outToday++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("active", active);
        summary.put("outForDelivery", outToday);
        summary.put("delivered", delivered);
        summary.put("totalSpent", spent);
        summary.put("perMarketplace", perMarketplace);
        summary.put("imported", importedCount);
        return summary;
    }

    private boolean matchesStore(Order o, String store) {
        if (isBlankOrAll(store)) return true;
        if (store.equalsIgnoreCase("Other")) {
            for (Marketplace m : marketplaces) if (m.getName().equalsIgnoreCase(o.getMarketplace())) return false;
            return true;
        }
        return o.getMarketplace().equalsIgnoreCase(store);
    }

    private static boolean isBlankOrAll(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("All");
    }

    private static boolean matchesFilter(OrderStatus s, String filter) {
        if (isBlankOrAll(filter)) return true;
        switch (filter.toLowerCase()) {
            case "active": return !s.isFinal();
            case "delivered": return s == OrderStatus.DELIVERED;
            case "cancelled": return s == OrderStatus.CANCELLED;
            default: return true;
        }
    }
}
