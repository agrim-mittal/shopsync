package com.shopsync.model;

import com.shopsync.util.JsonSerializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A product as it exists in the real world (e.g. "iPhone 15, 128 GB").
 * The same product is sold by many marketplaces; each listing is an {@link Offer}.
 *
 * Specs and the "good for" pitch are set with fluent methods while building the
 * catalogue, e.g. {@code new Product(...).spec("Display", "6.1\" OLED").goodFor("...")}.
 */
public class Product implements JsonSerializable {

    private final String id;
    private final String name;
    private final String brand;
    private final Category category;
    private final String icon;          // emoji used as a lightweight product image
    private final double basePrice;     // typical selling price in INR
    private final double mrp;           // maximum retail price in INR
    private final Set<String> tags;     // used for search and by marketplaces to decide what they stock
    private final Map<String, String> specs = new LinkedHashMap<>();
    private String goodFor = "";
    private final List<String> aliases = new ArrayList<>();   // nicknames people type, e.g. "xm5"

    public Product(String id, String name, String brand, Category category,
                   String icon, double basePrice, double mrp, String... tags) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.icon = icon;
        this.basePrice = basePrice;
        this.mrp = mrp;
        this.tags = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(tags)));
    }

    public Product spec(String key, String value) {
        specs.put(key, value);
        return this;
    }

    /** Other names shoppers use for this product ("xm5", "airpods"). Helps the AI assistant. */
    public Product alias(String... names) {
        for (String n : names) aliases.add(n.toLowerCase(Locale.ROOT));
        return this;
    }

    public List<String> getAliases() { return Collections.unmodifiableList(aliases); }

    public Product goodFor(String who) {
        this.goodFor = who;
        return this;
    }

    /** True if every word of the query appears in the name, brand, category or tags. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = searchText();
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Everything searchable about the product, lower-cased. */
    public String searchText() {
        return (name + " " + brand + " " + category.getLabel() + " " + String.join(" ", tags))
                .toLowerCase(Locale.ROOT);
    }

    /** A short "kind of thing" word, used in generated text: "smartphone", "earbuds"... */
    public String kind() {
        String[] priority = {"smartphone", "headphones", "earbuds", "laptop", "television", "smartwatch",
                "mouse", "sneakers", "running", "jeans", "hoodie", "sunglasses", "air fryer", "cooker", "flask"};
        for (String k : priority) {
            if (tags.contains(k)) return k.equals("running") ? "running shoes" : k;
        }
        return category.getLabel().toLowerCase(Locale.ROOT) + " product";
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public Category getCategory() { return category; }
    public String getIcon() { return icon; }
    public double getBasePrice() { return basePrice; }
    public double getMrp() { return mrp; }
    public Set<String> getTags() { return tags; }
    public Map<String, String> getSpecs() { return Collections.unmodifiableMap(specs); }
    public String getGoodFor() { return goodFor; }

    /** Short display name without the bracketed variant, e.g. "Apple iPhone 15". */
    public String getShortName() {
        int bracket = name.indexOf(" (");
        return bracket > 0 ? name.substring(0, bracket) : name;
    }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("shortName", getShortName());
        m.put("brand", brand);
        m.put("category", category.getLabel());
        m.put("kind", kind());
        m.put("icon", icon);
        m.put("mrp", mrp);
        List<Map<String, String>> specList = new ArrayList<>();
        for (Map.Entry<String, String> e : specs.entrySet()) {
            Map<String, String> s = new LinkedHashMap<>();
            s.put("label", e.getKey());
            s.put("value", e.getValue());
            specList.add(s);
        }
        m.put("specs", specList);
        m.put("goodFor", goodFor);
        return m;
    }

    @Override
    public String toString() {
        return name + " (" + brand + ")";
    }
}
