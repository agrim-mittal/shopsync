package com.shopsync.model;

import com.shopsync.util.JsonSerializable;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** The closing price of an offer on one day. Immutable. */
public class PricePoint implements JsonSerializable {

    private final LocalDate date;
    private final double price;

    public PricePoint(LocalDate date, double price) {
        this.date = date;
        this.price = price;
    }

    public LocalDate getDate() { return date; }
    public double getPrice() { return price; }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("price", price);
        return m;
    }
}
