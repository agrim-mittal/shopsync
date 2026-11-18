package com.shopsync.pricing;

import com.shopsync.util.JsonSerializable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** How the user pays: which bank cards they hold, prepaid or cash on delivery, and any old device to exchange. */
public class CheckoutProfile implements JsonSerializable {

    private final Set<String> cards = new LinkedHashSet<>();
    private boolean cashOnDelivery;
    private double exchangeValue;      // estimated value of an old phone/laptop to trade in (0 = none)
    private boolean useCoupons = true;

    public CheckoutProfile() { }

    public CheckoutProfile(List<String> cards, boolean cashOnDelivery, double exchangeValue, boolean useCoupons) {
        if (cards != null) {
            for (String c : cards) if (OfferBook.BANKS.contains(c)) this.cards.add(c);
        }
        this.cashOnDelivery = cashOnDelivery;
        this.exchangeValue = Math.max(0, Math.min(200_000, exchangeValue));
        this.useCoupons = useCoupons;
    }

    public Set<String> getCards() { return cards; }
    public boolean isCashOnDelivery() { return cashOnDelivery; }
    public double getExchangeValue() { return exchangeValue; }
    public boolean isUseCoupons() { return useCoupons; }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cards", new ArrayList<>(cards));
        m.put("cashOnDelivery", cashOnDelivery);
        m.put("exchangeValue", exchangeValue);
        m.put("useCoupons", useCoupons);
        m.put("availableBanks", OfferBook.BANKS);
        return m;
    }
}
