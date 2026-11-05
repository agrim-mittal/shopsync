package com.shopsync.marketplace;

import com.shopsync.model.Product;

import java.util.List;

/** Simulated Amazon connector: sells everything, fast delivery, moderate price swings. */
public class AmazonMarketplace extends SimulatedMarketplace {

    public AmazonMarketplace(List<Product> catalog) {
        super("Amazon", 11L, catalog);
    }

    @Override protected boolean carries(Product product) { return true; }
    @Override protected double pricingFactor() { return 1.00; }
    @Override protected double volatility() { return 0.03; }
    @Override protected int baseDeliveryDays() { return 1; }
    @Override protected double freeDeliveryThreshold() { return 499; }

    @Override protected double festiveSaleDiscount() { return 0.12; }

    @Override
    protected String[] sellers() {
        return new String[] {"Appario Retail", "Cocoblu Retail", "Clicktech Retail"};
    }
}
