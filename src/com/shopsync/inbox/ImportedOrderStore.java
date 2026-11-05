package com.shopsync.inbox;

import com.shopsync.model.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Orders each user imported from emails and messages (stores ShopSync isn't connected to). */
public class ImportedOrderStore {

    private final Map<String, List<Order>> byUser = new ConcurrentHashMap<>();

    public void add(Order order) {
        byUser.computeIfAbsent(order.getUserId(), k -> new CopyOnWriteArrayList<>()).add(order);
    }

    public List<Order> forUser(String userId) {
        List<Order> list = byUser.get(userId);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }
}
