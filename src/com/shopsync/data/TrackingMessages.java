package com.shopsync.data;

import com.shopsync.model.Order;
import com.shopsync.model.OrderStatus;

/** Human-readable tracking text for each stage, shared by sample data and the live simulator. */
public final class TrackingMessages {

    private TrackingMessages() { }

    public static String locationFor(OrderStatus status) {
        switch (status) {
            case PACKED: return "Seller warehouse, Bhiwandi";
            case SHIPPED: return "Regional sorting hub, Gurugram";
            case OUT_FOR_DELIVERY: return "Local delivery centre";
            case DELIVERED: return "Your address";
            default: return "Online";
        }
    }

    public static String messageFor(OrderStatus status, Order order) {
        switch (status) {
            case PACKED: return "Item packed and ready for pickup";
            case SHIPPED: return "Handed over to courier and in transit";
            case OUT_FOR_DELIVERY: return "Delivery agent is on the way";
            case DELIVERED: return "Package delivered successfully";
            default: return "Order confirmed by " + order.getMarketplace();
        }
    }
}
