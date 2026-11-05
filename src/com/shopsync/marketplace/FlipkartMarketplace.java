package com.shopsync.marketplace;

import com.shopsync.model.Product;

import java.util.List;

/** Simulated Flipkart connector: sells everything, slightly cheaper, bigger price swings. */
public class FlipkartMarketplace extends SimulatedMarketplace {

    public FlipkartMarketplace(List<Product> catalog) {
        super("Flipkart", 22L, catalog);
    }

    @Override protected boolean carries(Product product) { return true; }
    @Override protected double pricingFactor() { return 0.985; }
    @Override protected double volatility() { return 0.04; }
    @Override protected int baseDeliveryDays() { return 2; }
    @Override protected double freeDeliveryThreshold() { return 499; }

    @Override protected double festiveSaleDiscount() { return 0.13; }

    @Override
    protected String[] sellers() {
        return new String[] {"RetailNet", "SuperComNet", "OmniTechRetail"};
    }
}
