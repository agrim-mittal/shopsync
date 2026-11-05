package com.shopsync.model;

import com.shopsync.util.JsonSerializable;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** A notification shown in the dashboard's live feed (price drop or order update). */
public class Alert implements JsonSerializable {

    public enum Type { PRICE_DROP, ORDER_UPDATE }

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final long id;
    private final Type type;
    private final String title;
    private final String detail;
    private final String marketplace;
    private final String userId;        // null = shown to everyone (e.g. price drops)
    private final String productId;
    private final LocalDateTime time;

    public Alert(Type type, String title, String detail, String marketplace, String userId, String productId) {
        this.id = SEQUENCE.incrementAndGet();
        this.type = type;
        this.title = title;
        this.detail = detail;
        this.marketplace = marketplace;
        this.userId = userId;
        this.productId = productId;
        this.time = LocalDateTime.now();
    }

    public long getId() { return id; }

    /** Whether this alert should be shown to the given user. */
    public boolean isVisibleTo(String user) {
        return userId == null || userId.equals(user);
    }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("title", title);
        m.put("detail", detail);
        m.put("marketplace", marketplace);
        m.put("productId", productId);
        m.put("time", time.toString());
        return m;
    }
}
