package com.shopsync.service;

import com.shopsync.marketplace.Marketplace;
import com.shopsync.model.Category;
import com.shopsync.model.Offer;
import com.shopsync.model.Product;
import com.shopsync.model.ProductComparison;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Searches every marketplace AT THE SAME TIME (one thread each) and groups
 * the results by product so prices can be compared side by side.
 *
 * Asking four marketplaces one after another would take the sum of their
 * response times; asking them in parallel takes only as long as the slowest.
 */
public class PriceComparisonService {

    private final Collection<Marketplace> marketplaces;
    private final ExecutorService pool;

    public PriceComparisonService(Collection<Marketplace> marketplaces) {
        this.marketplaces = marketplaces;
        this.pool = Executors.newFixedThreadPool(Math.max(2, marketplaces.size()));
    }

    /**
     * @param query    search text (blank = all products)
     * @param category optional category label filter, e.g. "Fashion" (null/blank = all)
     */
    public List<ProductComparison> compare(String query, String category) {
        // 1. Fire one search task per marketplace.
        List<Callable<List<Offer>>> tasks = new ArrayList<>();
        for (Marketplace m : marketplaces) {
            tasks.add(() -> m.search(query));
        }

        // 2. Collect results and group offers by product.
        Map<Product, List<Offer>> byProduct = new LinkedHashMap<>();
        try {
            for (Future<List<Offer>> future : pool.invokeAll(tasks)) {
                try {
                    for (Offer offer : future.get()) {
                        if (!matchesCategory(offer.getProduct(), category)) continue;
                        byProduct.computeIfAbsent(offer.getProduct(), p -> new ArrayList<>()).add(offer);
                    }
                } catch (ExecutionException e) {
                    // One marketplace failing should not break the whole comparison.
                    System.err.println("Marketplace search failed: " + e.getCause());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. Build comparisons; products with the biggest savings first.
        List<ProductComparison> results = new ArrayList<>();
        for (Map.Entry<Product, List<Offer>> e : byProduct.entrySet()) {
            results.add(new ProductComparison(e.getKey(), e.getValue()));
        }
        results.sort(Comparator.comparing((ProductComparison c) -> c.getProduct().getId()));
        return results;
    }

    private boolean matchesCategory(Product p, String category) {
        if (category == null || category.isBlank() || category.equalsIgnoreCase("All")) {
            return true;
        }
        for (Category c : Category.values()) {
            if (c.getLabel().equalsIgnoreCase(category) || c.name().equalsIgnoreCase(category)) {
                return p.getCategory() == c;
            }
        }
        return true;
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
