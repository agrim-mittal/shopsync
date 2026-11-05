package com.shopsync.model;

/**
 * The life-cycle of an order. Every marketplace uses different words
 * ("Dispatched", "In transit", "Shipped"...); ShopSync maps them all onto
 * these common stages so orders can be shown side by side.
 */
public enum OrderStatus {
    PLACED("Order placed", 0),
    PACKED("Packed", 25),
    SHIPPED("Shipped", 50),
    OUT_FOR_DELIVERY("Out for delivery", 75),
    DELIVERED("Delivered", 100),
    CANCELLED("Cancelled", 0);

    private final String label;
    private final int progress;

    OrderStatus(String label, int progress) {
        this.label = label;
        this.progress = progress;
    }

    public String getLabel() { return label; }
    public int getProgress() { return progress; }

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /** The next stage in the normal flow (final stages stay where they are). */
    public OrderStatus next() {
        switch (this) {
            case PLACED: return PACKED;
            case PACKED: return SHIPPED;
            case SHIPPED: return OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY: return DELIVERED;
            default: return this;
        }
    }
}
