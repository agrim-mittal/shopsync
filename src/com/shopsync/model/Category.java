package com.shopsync.model;

/** Product categories shown as filter chips in the dashboard. */
public enum Category {
    ELECTRONICS("Electronics"),
    FASHION("Fashion"),
    HOME("Home & Kitchen");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
