package com.shopsync.marketplace;

import com.shopsync.model.Category;
import com.shopsync.model.Product;

import java.util.List;

/** Simulated Croma connector: electronics and appliances, stable but slightly higher prices. */
public class CromaMarketplace extends SimulatedMarketplace {

    public CromaMarketplace(List<Product> catalog) {
        super("Croma", 44L, catalog);
    }

    @Override
    protected boolean carries(Product product) {
        return product.getCategory() == Category.ELECTRONICS || product.hasTag("appliance");
    }

    @Override protected double pricingFactor() { return 1.02; }
    @Override protected double volatility() { return 0.02; }
    @Override protected int baseDeliveryDays() { return 2; }
    @Override protected double freeDeliveryThreshold() { return 0; }

    @Override protected double festiveSaleDiscount() { return 0.06; }

    @Override
    protected String[] sellers() {
        return new String[] {"Croma (Infiniti Retail)"};
    }
}
