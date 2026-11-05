package com.shopsync.marketplace;

import com.shopsync.model.Category;
import com.shopsync.model.Product;

import java.util.List;

/** Simulated Myntra connector: fashion and lifestyle products only, aggressive discounts. */
public class MyntraMarketplace extends SimulatedMarketplace {

    public MyntraMarketplace(List<Product> catalog) {
        super("Myntra", 33L, catalog);
    }

    @Override
    protected boolean carries(Product product) {
        return product.getCategory() == Category.FASHION || product.hasTag("lifestyle");
    }

    @Override protected double pricingFactor() { return 0.97; }
    @Override protected double volatility() { return 0.05; }
    @Override protected int baseDeliveryDays() { return 3; }
    @Override protected double freeDeliveryThreshold() { return 799; }

    @Override protected double festiveSaleDiscount() { return 0.18; }

    @Override
    protected String[] sellers() {
        return new String[] {"Brand Official Store", "Myntra Fashion Hub"};
    }
}
