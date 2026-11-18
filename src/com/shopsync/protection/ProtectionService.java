package com.shopsync.protection;

import com.shopsync.data.PolicyBook;
import com.shopsync.model.Alert;
import com.shopsync.model.Category;
import com.shopsync.model.Order;
import com.shopsync.model.OrderStatus;
import com.shopsync.model.Policy;
import com.shopsync.model.Product;
import com.shopsync.service.LiveUpdateEngine;
import com.shopsync.service.OrderCategories;
import com.shopsync.service.OrderTrackingService;
import com.shopsync.service.ProductService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Warranty & return deadline manager.
 *
 *  - Return deadline = delivery date + the store's return window for that category
 *    (from the policy book; a sensible default for imported stores).
 *  - Warranty expiry = delivery date + warranty months (by product type; editable).
 *  - Reminders: a notification when a return window closes within 2 days,
 *    and when a warranty expires within 30 days.
 *  - Users can attach invoices and warranty cards to each order.
 */
public class ProtectionService {

    public static final int RETURN_SOON_DAYS = 2;
    public static final int WARRANTY_SOON_DAYS = 30;
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_FILES_PER_USER = 40;

    private final OrderTrackingService orders;
    private final ProductService products;
    private final LiveUpdateEngine live;

    /** userId|orderId → user's own return-days / warranty-months values. */
    private final Map<String, int[]> overrides = new ConcurrentHashMap<>();
    private final Map<String, StoredDocument> documents = new ConcurrentHashMap<>();
    private final Set<String> remindersSent = ConcurrentHashMap.newKeySet();
    private final AtomicInteger nextDoc = new AtomicInteger(1);

    public ProtectionService(OrderTrackingService orders, ProductService products, LiveUpdateEngine live) {
        this.orders = orders;
        this.products = products;
        this.live = live;
    }

    // =============================================================== defaults

    /** Default return window: the marketplace policy, else a typical value for that store. */
    int defaultReturnDays(Order o) {
        Product p = o.getProductId() == null ? null : products.find(o.getProductId());
        if (p != null) {
            Policy pol = PolicyBook.get(o.getMarketplace(), p.getCategory());
            if (pol != null) return pol.getReturnDays();
        }
        String store = o.getMarketplace().toLowerCase(Locale.ROOT);
        if (store.matches(".*(blinkit|zepto|instamart|bigbasket|jiomart).*")) return 0;    // groceries: report issues at delivery
        if (store.matches(".*(nykaa|ajio|lenskart|bewakoof|souled).*")) return 14;
        if (store.matches(".*(meesho|shopsy).*")) return 7;
        String cat = OrderCategories.of(o, products);
        if (cat.equals(OrderCategories.FASHION)) return 10;
        if (cat.equals(OrderCategories.BEAUTY) || cat.equals(OrderCategories.HEALTH)) return 7;
        return 7;
    }

    /** Default warranty length in months by product type (typical manufacturer warranties). */
    int defaultWarrantyMonths(Order o) {
        Product p = o.getProductId() == null ? null : products.find(o.getProductId());
        if (p != null) {
            if (p.hasTag("cooker")) return 60;
            if (p.hasTag("air fryer")) return 24;
            if (p.hasTag("mouse")) return 12;
            if (p.hasTag("sunglasses")) return 12;
            if (p.getCategory() == Category.ELECTRONICS) return 12;
            if (p.hasTag("flask")) return 12;
            return 0;
        }
        return OrderCategories.of(o, products).equals(OrderCategories.ELECTRONICS) ? 12 : 0;
    }

    private int[] settings(String userId, Order o) {
        int[] ov = overrides.get(userId + "|" + o.getOrderId());
        return ov != null ? ov : new int[] {defaultReturnDays(o), defaultWarrantyMonths(o)};
    }

    public void setOverride(String userId, String orderId, int returnDays, int warrantyMonths) {
        if (orders.findById(userId, orderId) == null) throw new IllegalArgumentException("Order not found");
        if (returnDays < 0 || returnDays > 365 || warrantyMonths < 0 || warrantyMonths > 120)
            throw new IllegalArgumentException("Use 0–365 return days and 0–120 warranty months");
        overrides.put(userId + "|" + orderId, new int[] {returnDays, warrantyMonths});
    }

    // =============================================================== view

    public Map<String, Object> overview(String userId) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> items = new ArrayList<>();
        int returnsSoon = 0, warrantySoon = 0, activeWarranties = 0, awaitingDelivery = 0;

        for (Order o : orders.getOrders(userId, null, null)) {
            if (o.getStatus() == OrderStatus.CANCELLED) continue;
            int[] set = settings(userId, o);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", o.getOrderId());
            m.put("productId", o.getProductId());
            m.put("productName", o.getProductName());
            m.put("store", o.getMarketplace());
            m.put("amount", o.getAmount());
            m.put("category", OrderCategories.of(o, products));
            m.put("source", o.getSource());
            m.put("returnDays", set[0]);
            m.put("warrantyMonths", set[1]);
            m.put("documents", documentsFor(userId, o.getOrderId()));

            if (o.getDeliveredAt() == null) {
                awaitingDelivery++;
                m.put("delivered", false);
                m.put("returnStatus", "PENDING");
                m.put("warrantyStatus", set[1] > 0 ? "PENDING" : "NONE");
                items.add(m);
                continue;
            }
            LocalDate delivered = o.getDeliveredAt().toLocalDate();
            m.put("delivered", true);
            m.put("deliveredOn", delivered.toString());

            LocalDate returnBy = delivered.plusDays(set[0]);
            long returnLeft = ChronoUnit.DAYS.between(today, returnBy);
            m.put("returnBy", returnBy.toString());
            m.put("returnDaysLeft", returnLeft);
            String rs = set[0] == 0 ? "NONE" : returnLeft < 0 ? "CLOSED" : returnLeft <= RETURN_SOON_DAYS ? "CLOSING_SOON" : "OPEN";
            m.put("returnStatus", rs);
            m.put("returnProgress", set[0] == 0 ? 100 : (int) Math.max(0, Math.min(100,
                    Math.round(100.0 * ChronoUnit.DAYS.between(delivered, today) / set[0]))));
            if (rs.equals("CLOSING_SOON")) returnsSoon++;

            if (set[1] > 0) {
                LocalDate warrantyEnd = delivered.plusMonths(set[1]);
                long wLeft = ChronoUnit.DAYS.between(today, warrantyEnd);
                m.put("warrantyUntil", warrantyEnd.toString());
                m.put("warrantyDaysLeft", wLeft);
                String ws = wLeft < 0 ? "EXPIRED" : wLeft <= WARRANTY_SOON_DAYS ? "EXPIRING_SOON" : "ACTIVE";
                m.put("warrantyStatus", ws);
                long total = ChronoUnit.DAYS.between(delivered, warrantyEnd);
                m.put("warrantyProgress", (int) Math.max(0, Math.min(100,
                        Math.round(100.0 * ChronoUnit.DAYS.between(delivered, today) / Math.max(1, total)))));
                if (ws.equals("EXPIRING_SOON")) warrantySoon++;
                if (!ws.equals("EXPIRED")) activeWarranties++;
            } else {
                m.put("warrantyStatus", "NONE");
            }
            items.add(m);
        }

        // most urgent first
        items.sort(Comparator.comparingInt(ProtectionService::urgency));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("returnsClosingSoon", returnsSoon);
        out.put("warrantiesExpiringSoon", warrantySoon);
        out.put("activeWarranties", activeWarranties);
        out.put("awaitingDelivery", awaitingDelivery);
        out.put("documentCount", countDocuments(userId));
        return out;
    }

    private static int urgency(Map<String, Object> m) {
        String rs = (String) m.get("returnStatus"), ws = (String) m.get("warrantyStatus");
        if ("CLOSING_SOON".equals(rs)) return 0;
        if ("EXPIRING_SOON".equals(ws)) return 1;
        if ("OPEN".equals(rs)) return 2;
        if ("ACTIVE".equals(ws)) return 3;
        if ("PENDING".equals(rs)) return 4;
        return 5;
    }

    // =============================================================== reminders

    /** Background check: one reminder per item per kind. */
    public void checkReminders(Collection<String> userIds) {
        for (String uid : userIds) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) overview(uid).get("items");
            for (Map<String, Object> m : items) {
                String key = uid + "|" + m.get("orderId");
                if ("CLOSING_SOON".equals(m.get("returnStatus")) && remindersSent.add(key + "|return")) {
                    long left = ((Number) m.get("returnDaysLeft")).longValue();
                    live.notify(new Alert(Alert.Type.REMINDER, "Return window closing: " + m.get("productName"),
                            (left == 0 ? "Last day today" : left + " day" + (left > 1 ? "s" : "") + " left")
                                    + " to return it to " + m.get("store"), (String) m.get("store"), uid, (String) m.get("productId")));
                }
                if ("EXPIRING_SOON".equals(m.get("warrantyStatus")) && remindersSent.add(key + "|warranty")) {
                    live.notify(new Alert(Alert.Type.REMINDER, "Warranty ending: " + m.get("productName"),
                            "Expires on " + m.get("warrantyUntil") + " (" + m.get("warrantyDaysLeft") + " days left)",
                            (String) m.get("store"), uid, (String) m.get("productId")));
                }
            }
        }
    }

    // =============================================================== documents

    public StoredDocument upload(String userId, String orderId, String kind, String fileName, String mimeType, String base64) {
        if (orders.findById(userId, orderId) == null) throw new IllegalArgumentException("Order not found");
        if (countDocuments(userId) >= MAX_FILES_PER_USER) throw new IllegalArgumentException("You can store up to " + MAX_FILES_PER_USER + " files");
        byte[] data;
        try {
            String b64 = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;   // strip "data:...;base64,"
            data = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The file couldn't be read");
        }
        if (data.length == 0) throw new IllegalArgumentException("The file is empty");
        if (data.length > MAX_FILE_BYTES) throw new IllegalArgumentException("Files must be 5 MB or smaller");
        String mime = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
        if (!mime.matches("application/pdf|image/(png|jpeg|jpg|webp|heic)")) throw new IllegalArgumentException("Upload a PDF or an image");
        StoredDocument.Kind k;
        try { k = StoredDocument.Kind.valueOf(kind == null ? "INVOICE" : kind.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { k = StoredDocument.Kind.OTHER; }
        String name = fileName == null || fileName.isBlank() ? "document" : fileName.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        StoredDocument d = new StoredDocument("D" + nextDoc.getAndIncrement(), userId, orderId, k, name, mime, data);
        documents.put(d.getId(), d);
        return d;
    }

    public StoredDocument document(String userId, String docId) {
        StoredDocument d = documents.get(docId);
        return d != null && d.getUserId().equals(userId) ? d : null;   // users only see their own files
    }

    public boolean deleteDocument(String userId, String docId) {
        StoredDocument d = document(userId, docId);
        return d != null && documents.remove(docId) != null;
    }

    private List<StoredDocument> documentsFor(String userId, String orderId) {
        List<StoredDocument> list = new ArrayList<>();
        for (StoredDocument d : documents.values()) {
            if (d.getUserId().equals(userId) && d.getOrderId().equals(orderId)) list.add(d);
        }
        return list;
    }

    private int countDocuments(String userId) {
        int n = 0;
        for (StoredDocument d : documents.values()) if (d.getUserId().equals(userId)) n++;
        return n;
    }
}
