package com.shopsync.model;

import com.shopsync.util.JsonSerializable;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** One line in an order's tracking timeline. Immutable. */
public class TrackingEvent implements JsonSerializable {

    private final OrderStatus status;
    private final String message;
    private final String location;
    private final LocalDateTime time;

    public TrackingEvent(OrderStatus status, String message, String location, LocalDateTime time) {
        this.status = status;
        this.message = message;
        this.location = location;
        this.time = time;
    }

    public OrderStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public String getLocation() { return location; }
    public LocalDateTime getTime() { return time; }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("statusLabel", status.getLabel());
        m.put("message", message);
        m.put("location", location);
        m.put("time", time.toString());
        return m;
    }
}
