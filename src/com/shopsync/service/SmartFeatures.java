package com.shopsync.service;

import com.shopsync.alerts.PriceAlertService;
import com.shopsync.budget.BudgetService;
import com.shopsync.pricing.FinalPriceService;
import com.shopsync.protection.ProtectionService;

/** The four money-saving features, grouped so they can be passed around together. */
public class SmartFeatures {

    public final PriceAlertService alerts;
    public final ProtectionService protection;
    public final FinalPriceService finalPrice;
    public final BudgetService budget;

    public SmartFeatures(PriceAlertService alerts, ProtectionService protection,
                         FinalPriceService finalPrice, BudgetService budget) {
        this.alerts = alerts;
        this.protection = protection;
        this.finalPrice = finalPrice;
        this.budget = budget;
    }
}
