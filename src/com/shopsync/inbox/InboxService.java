package com.shopsync.inbox;

import com.shopsync.model.Alert;
import com.shopsync.model.Order;
import com.shopsync.model.OrderStatus;
import com.shopsync.service.LiveUpdateEngine;
import com.shopsync.service.OrderTrackingService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Turns order emails and SMS into tracked orders.
 *
 *  - A message about a NEW order creates an order (from any store).
 *  - A later message about the SAME order (matched by order ID, tracking number,
 *    or store + product) updates its status instead of creating a duplicate.
 *  - Each user gets a private "forwarding address" (a secret webhook URL) so
 *    Gmail, an iPhone Shortcut or an Android SMS forwarder can send messages
 *    automatically, without the user being logged in.
 */
public class InboxService {

    private static final int LOG_SIZE = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MessageParser parser;
    private final ImportedOrderStore imported;
    private final OrderTrackingService orders;
    private final LiveUpdateEngine live;

    private final Map<String, String> tokenToUser = new ConcurrentHashMap<>();
    private final Map<String, String> userToToken = new ConcurrentHashMap<>();
    private final Map<String, Deque<Map<String, Object>>> activity = new ConcurrentHashMap<>();

    public InboxService(MessageParser parser, ImportedOrderStore imported, OrderTrackingService orders,
                        LiveUpdateEngine live) {
        this.parser = parser;
        this.imported = imported;
        this.orders = orders;
        this.live = live;
    }

    // =========================================================== forwarding tokens

    public synchronized String tokenFor(String userId) {
        return userToToken.computeIfAbsent(userId, id -> {
            String t = newToken();
            tokenToUser.put(t, id);
            return t;
        });
    }

    /** Invalidates the old forwarding address (e.g. if it was shared by mistake). */
    public synchronized String regenerateToken(String userId) {
        String old = userToToken.remove(userId);
        if (old != null) tokenToUser.remove(old);
        return tokenFor(userId);
    }

    public String userForToken(String token) {
        return token == null ? null : tokenToUser.get(token);
    }

    private static String newToken() {
        byte[] b = new byte[18];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // =========================================================== preview & import

    /** Reads a message and says what would happen, without saving anything. */
    public Map<String, Object> preview(String userId, String subject, String body, String from) {
        ParsedMessage p = parser.parse(subject, body, from);
        Order match = p.isOrderMessage() ? findMatch(userId, p) : null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parsed", p);
        out.put("action", !p.isOrderMessage() ? "ignore" : match != null ? "update" : "create");
        out.put("matchedOrder", match);
        return out;
    }

    /** Reads a message and creates or updates the matching order. */
    public Map<String, Object> importMessage(String userId, String subject, String body, String from, String source) {
        String src = normaliseSource(source);
        ParsedMessage p = parser.parse(subject, body, from);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parsed", p);

        if (!p.isOrderMessage()) {
            out.put("action", "ignored");
            out.put("reason", p.notes.isEmpty() ? "Not an order message" : p.notes.get(0));
            log(userId, src, subject, body, "ignored", (String) out.get("reason"), null);
            return out;
        }

        LocalDateTime now = LocalDateTime.now();
        String where = p.getCourier() != null ? p.getCourier() : "Reported by " + src.toLowerCase(Locale.ROOT);
        Order existing = findMatch(userId, p);

        if (existing != null) {
            existing.mergeDetails(p.getProduct(), p.getAmount(), p.getExpectedDelivery(), p.getCourier(), p.getTrackingNumber());
            boolean changed = existing.applyReportedStatus(p.getStatus(), describe(p, src), where, now);
            out.put("action", changed ? "updated" : "unchanged");
            out.put("reason", changed ? "Status changed to " + existing.getStatus().getLabel()
                    : "Already up to date (" + existing.getStatus().getLabel() + ")");
            out.put("order", existing);
            if (changed) {
                live.notify(new Alert(Alert.Type.ORDER_UPDATE, existing.getProductName() + ": " + existing.getStatus().getLabel(),
                        "Updated from " + src.toLowerCase(Locale.ROOT) + " · " + existing.getMarketplace(),
                        existing.getMarketplace(), userId, existing.getProductId()));
            }
            log(userId, src, subject, body, (String) out.get("action"), (String) out.get("reason"), existing.getOrderId());
            return out;
        }

        String store = p.getStore() != null ? p.getStore() : "Other store";
        String id = p.getOrderId() != null ? p.getOrderId() : "IMP-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        String product = p.getProduct() != null ? p.getProduct() : "Order from " + store;
        OrderStatus status = p.getStatus() == null ? OrderStatus.PLACED : p.getStatus();

        Order order = new Order(id, userId, store, null, product, "📦", 1,
                p.getAmount() == null ? 0 : p.getAmount(), now, p.getExpectedDelivery(), p.getCourier(),
                src, status, describe(p, src), where);
        order.setTrackingNumber(p.getTrackingNumber());
        imported.add(order);

        out.put("action", "created");
        out.put("reason", "New " + store + " order added (" + status.getLabel() + ")");
        out.put("order", order);
        live.notify(new Alert(Alert.Type.ORDER_UPDATE, "Imported: " + product,
                store + " order from " + src.toLowerCase(Locale.ROOT), store, userId, null));
        log(userId, src, subject, body, "created", (String) out.get("reason"), id);
        return out;
    }

    private static String describe(ParsedMessage p, String source) {
        String label = p.getStatus() == null ? "Update" : p.getStatus().getLabel();
        return label + " (read from " + source.toLowerCase(Locale.ROOT) + ")"
                + (p.getTrackingNumber() != null ? " · tracking " + p.getTrackingNumber() : "");
    }

    /** Same order ID or tracking number; otherwise same store and similar product while still in progress. */
    private Order findMatch(String userId, ParsedMessage p) {
        List<Order> all = orders.getOrders(userId, null, null);
        for (Order o : all) {
            if (p.getOrderId() != null && normalise(o.getOrderId()).equals(normalise(p.getOrderId()))) return o;
            if (p.getTrackingNumber() != null && o.getTrackingNumber() != null
                    && o.getTrackingNumber().equalsIgnoreCase(p.getTrackingNumber())) return o;
        }
        if (p.getOrderId() != null || p.getStore() == null) return null;
        for (Order o : all) {
            if (!o.isImported() || o.getStatus().isFinal() || !o.getMarketplace().equalsIgnoreCase(p.getStore())) continue;
            if (o.getOrderedOn().isBefore(LocalDateTime.now().minusDays(30))) continue;
            if (p.getProduct() == null || similar(o.getProductName(), p.getProduct())) return o;
        }
        return null;
    }

    private static String normalise(String id) {
        return id.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** True if the two product names share a meaningful word. */
    private static boolean similar(String a, String b) {
        for (String w : b.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() >= 4 && a.toLowerCase(Locale.ROOT).contains(w)) return true;
        }
        return a.startsWith("Order from ");
    }

    private static String normaliseSource(String s) {
        if (s == null) return "Pasted";
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "email": case "gmail": case "mail": return "Email";
            case "sms": case "message": case "text": case "imessage": case "whatsapp": return "SMS";
            default: return "Pasted";
        }
    }

    // =========================================================== activity log

    private void log(String userId, String source, String subject, String body, String action, String reason, String orderId) {
        String preview = (subject != null && !subject.isBlank() ? subject : body == null ? "" : body).replaceAll("\\s+", " ").trim();
        if (preview.length() > 90) preview = preview.substring(0, 87) + "…";
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("time", LocalDateTime.now().toString());
        e.put("source", source);
        e.put("preview", preview);
        e.put("action", action);
        e.put("reason", reason);
        e.put("orderId", orderId);
        Deque<Map<String, Object>> d = activity.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        d.addFirst(e);
        while (d.size() > LOG_SIZE) d.removeLast();
    }

    public List<Map<String, Object>> activity(String userId) {
        Deque<Map<String, Object>> d = activity.get(userId);
        return d == null ? new ArrayList<>() : new ArrayList<>(d);
    }

    // =========================================================== Gmail helper script

    /** A ready-to-paste Google Apps Script that forwards new order emails to this user's address. */
    public String gmailScript(String hookUrl) {
        return "/**\n"
             + " * ShopSync: forward order emails from Gmail automatically.\n"
             + " * 1. Go to https://script.google.com, click New project, paste this file.\n"
             + " * 2. Click Run once (choose forwardOrderEmails) and allow access to Gmail.\n"
             + " * 3. Click Triggers (clock icon) > Add trigger > forwardOrderEmails > Time-driven > Every 15 minutes.\n"
             + " */\n"
             + "const SHOPSYNC_URL = '" + hookUrl + "';\n"
             + "const QUERY = 'newer_than:2d (order OR shipped OR dispatched OR delivered OR \"out for delivery\" OR cancelled) -category:promotions';\n\n"
             + "function forwardOrderEmails() {\n"
             + "  const props = PropertiesService.getUserProperties();\n"
             + "  const seen = JSON.parse(props.getProperty('shopsyncSeen') || '[]');\n"
             + "  const threads = GmailApp.search(QUERY, 0, 30);\n"
             + "  threads.forEach(function (thread) {\n"
             + "    thread.getMessages().forEach(function (msg) {\n"
             + "      const id = msg.getId();\n"
             + "      if (seen.indexOf(id) !== -1) return;\n"
             + "      UrlFetchApp.fetch(SHOPSYNC_URL, {\n"
             + "        method: 'post',\n"
             + "        contentType: 'application/json',\n"
             + "        payload: JSON.stringify({\n"
             + "          source: 'Email',\n"
             + "          from: msg.getFrom(),\n"
             + "          subject: msg.getSubject(),\n"
             + "          body: msg.getPlainBody().slice(0, 8000)\n"
             + "        }),\n"
             + "        muteHttpExceptions: true\n"
             + "      });\n"
             + "      seen.push(id);\n"
             + "    });\n"
             + "  });\n"
             + "  props.setProperty('shopsyncSeen', JSON.stringify(seen.slice(-400)));\n"
             + "}\n";
    }
}
