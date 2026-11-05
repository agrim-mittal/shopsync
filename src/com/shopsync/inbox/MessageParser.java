package com.shopsync.inbox;

import com.shopsync.ai.LlmClient;
import com.shopsync.model.OrderStatus;
import com.shopsync.util.JsonParser;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads order emails and SMS from ANY online store and extracts the order details.
 *
 * Step 1 always runs: regular-expression rules that recognise the patterns stores
 * use ("Order ID: …", "has been shipped", "AWB …", "₹1,299", "arriving by 12 Nov").
 * Step 2 runs only when an AI model is configured: the model reads the same text and
 * returns JSON; its answers fill in or correct what the rules found.
 */
public class MessageParser {

    // ---------------------------------------------------------------- known names

    /** regex (lower-case text) → display name. Order matters only for display. */
    private static final String[][] STORES = {
            {"amazon", "Amazon"}, {"flipkart", "Flipkart"}, {"myntra", "Myntra"}, {"croma", "Croma"},
            {"nykaa", "Nykaa"}, {"ajio", "AJIO"}, {"meesho", "Meesho"}, {"tata ?cliq", "Tata CLiQ"},
            {"snapdeal", "Snapdeal"}, {"jiomart", "JioMart"}, {"bigbasket", "BigBasket"}, {"blinkit", "Blinkit"},
            {"zepto", "Zepto"}, {"swiggy instamart|instamart", "Swiggy Instamart"}, {"firstcry", "FirstCry"},
            {"lenskart", "Lenskart"}, {"decathlon", "Decathlon"}, {"pepperfry", "Pepperfry"},
            {"urban ?ladder", "Urban Ladder"}, {"reliance ?digital", "Reliance Digital"}, {"vijay ?sales", "Vijay Sales"},
            {"bewakoof", "Bewakoof"}, {"purplle", "Purplle"}, {"mamaearth", "Mamaearth"}, {"shopsy", "Shopsy"},
            {"ikea", "IKEA"}, {"h&m|hm\\.com", "H&M"}, {"zara", "Zara"}, {"uniqlo", "Uniqlo"},
            {"the souled store|souledstore", "The Souled Store"}, {"healthkart", "HealthKart"},
            {"pharmeasy", "PharmEasy"}, {"tata ?1mg|1mg", "Tata 1mg"}, {"netmeds", "Netmeds"},
            {"boat-lifestyle|boat lifestyle", "boAt"}, {"sugar cosmetics", "SUGAR Cosmetics"},
            {"apple store|apple\\.com", "Apple Store"}, {"samsung shop|samsung\\.com", "Samsung Shop"},
            {"oneplus", "OnePlus"}, {"puma", "Puma"}, {"nike", "Nike"}, {"adidas", "Adidas"},
            {"westside", "Westside"}, {"lifestyle stores", "Lifestyle"}, {"shoppers stop", "Shoppers Stop"},
    };

    private static final String[][] COURIERS = {
            {"delhivery", "Delhivery"}, {"blue ?dart", "Blue Dart"}, {"ekart", "Ekart"}, {"xpressbees", "Xpressbees"},
            {"shadowfax", "Shadowfax"}, {"dtdc", "DTDC"}, {"ecom express", "Ecom Express"},
            {"india post|speed ?post", "India Post"}, {"amazon shipping|amazon transportation", "Amazon Shipping"},
            {"shiprocket", "Shiprocket"}, {"valmo", "Valmo"}, {"gati", "Gati"}, {"smartr", "Smartr"},
            {"professional couriers", "Professional Couriers"}, {"dunzo", "Dunzo"},
    };

    /** Email domains that belong to couriers or mail providers, never the store. */
    private static final Pattern NOT_A_STORE_DOMAIN = Pattern.compile(
            "gmail|yahoo|outlook|hotmail|icloud|mail|delhivery|bluedart|ekart|xpressbees|shadowfax|dtdc|shiprocket|"
            + "ecomexpress|indiapost|amazonses|sendgrid|mailchimp|razorpay|paytm|phonepe|gpay|notification|info|noreply");

    private static final Pattern ORDER_ID = Pattern.compile(
            "(?i)\\border\\s*(?:id|no\\.?|number|#)?\\s*[:#\\-]?\\s*#?((?=[A-Z0-9-]*\\d)[A-Z0-9][A-Z0-9-]{5,})");
    private static final Pattern HASH_ID = Pattern.compile("#\\s?((?=[A-Z0-9-]*\\d)[A-Z0-9][A-Z0-9-]{5,})");
    private static final Pattern TRACKING = Pattern.compile(
            "(?i)(?:tracking\\s*(?:id|no\\.?|number|#)?|awb\\s*(?:no\\.?|number)?|consignment\\s*(?:no\\.?|number)?)"
            + "\\s*(?:is)?\\s*[:#\\-]?\\s*((?=[A-Z0-9]*\\d)[A-Z0-9]{8,})");
    private static final Pattern AMOUNT = Pattern.compile("(?i)(?:₹|rs\\.?|inr)\\s*([\\d,]+(?:\\.\\d{1,2})?)");
    private static final Pattern AMOUNT_LABEL = Pattern.compile(
            "(?i)(?:total|amount|paid|order value|grand total|payable)[^₹\\d\\n]{0,25}(?:₹|rs\\.?|inr)\\s*([\\d,]+(?:\\.\\d{1,2})?)");
    private static final Pattern YOUR_STORE_ORDER = Pattern.compile(
            "\\b[Yy]our\\s+([A-Z][\\w&.'-]{1,20}(?:\\s[A-Z][\\w&.'-]{1,20})?)\\s+(?:order|package|shipment|parcel)\\b");
    private static final Pattern SHOPPING_WITH = Pattern.compile(
            "(?i)(?:shopping|ordering|order(?:ed)?|purchas(?:e|ing))\\s+(?:with|from|at|on)\\s+([A-Za-z][\\w&.'-]{1,20}(?:\\s[A-Z][\\w&.'-]{1,20})?)");

    private static final Pattern[] PRODUCT_PATTERNS = {
            Pattern.compile("(?im)^\\s*(?:item|product|items)\\s*(?:name)?\\s*[:\\-]\\s*(.+)$"),
            Pattern.compile("(?i)\\byour\\s+order\\s+(?:for|of)\\s+(.{3,90}?)(?=\\s+(?:has|is|was|will|worth|from|with|on|at|placed|\\()|[.!,\\n]|$)"),
            Pattern.compile("(?i)\\border\\s+(?:no\\.?\\s*|id\\s*|#)?[A-Z0-9_-]{5,}\\s+(?:for|of)\\s+(.{3,90}?)(?=\\s+(?:has|is|was|will|worth|from|with|on|at|\\()|[.!,\\n]|$)"),
            Pattern.compile("(?i)\\bcontaining\\s+(.{3,90}?)(?=\\s+(?:has|have|is|are|was|will)\\b|[.!,\\n]|$)"),
            Pattern.compile("(?i)\\b(?:your|the)\\s+(?!order\\b|package\\b|shipment\\b|parcel\\b|item\\b|items\\b)(.{3,70}?)\\s+(?:has been|have been|is|are|was|will be)\\s+(?:shipped|delivered|dispatched|packed|out for delivery|on (?:its|the) way)"),
            Pattern.compile("(?i)\\b(?:ordered|purchased|bought)\\s+(?:a |an |the )?(.{3,80}?)(?=\\s+(?:from|on|at|for)\\b|[.!,\\n]|$)"),
    };

    private static final Pattern DATE_PHRASE = Pattern.compile(
            "(?i)(?:arriving|arrives|arrive|expected(?:\\s+delivery)?(?:\\s+date)?|delivery\\s+by|deliver(?:ed)?\\s+by|"
            + "estimated\\s+delivery(?:\\s+date)?|eta|will\\s+be\\s+delivered|delivery\\s+date)\\s*[:\\-]?\\s*(?:on|by)?\\s*,?\\s*"
            + "(today|tomorrow|(?:mon|tue|tues|wed|wednes|thu|thur|thurs|fri|sat|satur|sun)(?:day)?\\b|"
            + "\\d{1,2}(?:st|nd|rd|th)?\\s+[a-z]{3,9}(?:,?\\s+\\d{4})?|[a-z]{3,9}\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?|"
            + "\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?)");

    private final LlmClient llm;

    public MessageParser(LlmClient llm) {
        this.llm = llm;
    }

    // ================================================================ entry point

    public ParsedMessage parse(String subject, String body, String from) {
        String text = ((subject == null ? "" : subject + "\n") + (body == null ? "" : body)).trim();
        ParsedMessage p = parseWithRules(text, from == null ? "" : from);
        if (llm.isEnabled() && text.length() > 15) {
            try {
                mergeAi(p, text, from);
            } catch (IOException | RuntimeException e) {
                p.notes.add("AI reading unavailable, used built-in rules");
            }
        }
        return p;
    }

    // ================================================================ rules

    ParsedMessage parseWithRules(String text, String from) {
        ParsedMessage p = new ParsedMessage();
        String lower = text.toLowerCase(Locale.ROOT);

        p.store = findStore(text, lower, from);
        p.orderId = firstGroup(ORDER_ID, text);
        if (p.orderId == null) p.orderId = firstGroup(HASH_ID, text);
        p.trackingNumber = firstGroup(TRACKING, text);
        if (p.trackingNumber != null && p.trackingNumber.equals(p.orderId)) p.trackingNumber = null;
        p.courier = findIn(lower, COURIERS);
        p.amount = findAmount(text);
        p.status = findStatus(lower);
        p.product = findProduct(text, p.store);
        p.expectedDelivery = findDate(text);

        boolean otp = lower.matches("(?s).*\\b(otp|one[- ]time password|verification code)\\b.*") && p.orderId == null;
        boolean promo = lower.matches("(?s).*\\b(sale is live|% off on|use code|coupon|unsubscribe from offers)\\b.*")
                && p.orderId == null && p.trackingNumber == null;
        p.orderMessage = !otp && !promo
                && (p.orderId != null || p.trackingNumber != null
                    || (p.status != null && (p.store != null || p.amount != null || p.courier != null)));

        if (otp) p.notes.add("Looks like a one-time password, not an order");
        if (promo) p.notes.add("Looks like an advert, not an order");
        if (!p.orderMessage && !otp && !promo) p.notes.add("No order number, tracking number or delivery status found");
        if (p.orderMessage && p.status == null) p.status = OrderStatus.PLACED;
        return p;
    }

    private static String findStore(String text, String lower, String from) {
        // 1) the sender's email domain, e.g. "Nykaa <noreply@nykaa.com>" → Nykaa
        Matcher dm = Pattern.compile("@([a-z0-9.-]+)").matcher(from.toLowerCase(Locale.ROOT));
        if (dm.find()) {
            String domain = dm.group(1);
            String known = findIn(domain.replace(".", " ").replace("-", " "), STORES);
            if (known != null) return known;
            String[] parts = domain.split("\\.");
            String label = parts.length >= 2 ? parts[parts.length - 2] : parts[0];
            if (label.length() > 2 && !NOT_A_STORE_DOMAIN.matcher(label).find()) {
                return Character.toUpperCase(label.charAt(0)) + label.substring(1);
            }
        }
        // 2) explicit phrases: "Your Nykaa order", "Thank you for shopping with Ajio"
        for (Pattern pat : new Pattern[] {YOUR_STORE_ORDER, SHOPPING_WITH}) {
            Matcher m = pat.matcher(text);
            while (m.find()) {
                String candidate = m.group(1).trim();
                String known = findIn(candidate.toLowerCase(Locale.ROOT), STORES);
                if (known != null) return known;
                if (Character.isUpperCase(candidate.charAt(0)) && !candidate.matches("(?i)(us|our|the|this|your|order|recent).*")) {
                    return candidate;
                }
            }
        }
        // 3) any known store name, preferring the subject / first line
        String firstLine = lower.contains("\n") ? lower.substring(0, lower.indexOf('\n')) : lower;
        String known = findIn(firstLine, STORES);
        return known != null ? known : findIn(lower, STORES);
    }

    private static String findIn(String lower, String[][] table) {
        int bestPos = Integer.MAX_VALUE;
        String best = null;
        for (String[] entry : table) {
            Matcher m = Pattern.compile("\\b(?:" + entry[0] + ")\\b").matcher(lower);
            if (m.find() && m.start() < bestPos) {
                bestPos = m.start();
                best = entry[1];
            }
        }
        return best;
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).replaceAll("-+$", "") : null;
    }

    private static Double findAmount(String text) {
        String v = firstGroup(AMOUNT_LABEL, text);
        if (v == null) v = firstGroup(AMOUNT, text);
        if (v == null) return null;
        try {
            double d = Double.parseDouble(v.replace(",", ""));
            return d > 0 && d < 10_000_000 ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static OrderStatus findStatus(String t) {
        if (t.matches("(?s).*\\b(cancell?ed|cancellation (?:is )?confirmed|refund (?:has been )?(?:initiated|processed))\\b.*"))
            return OrderStatus.CANCELLED;
        if (t.matches("(?s).*(\\b(?:has been|have been|was|were|is|got|successfully)\\s+delivered\\b|\\bdelivered\\s+(?:successfully|today|on|at)\\b|^delivered\\b|\\border delivered\\b|\\bdelivered[:!]).*")
                && !t.matches("(?s).*\\b(?:will be|to be|expected to be)\\s+delivered\\b.*"))
            return OrderStatus.DELIVERED;
        if (t.matches("(?s).*\\b(out for delivery|arriving today|will be delivered today|delivery agent|ofd)\\b.*"))
            return OrderStatus.OUT_FOR_DELIVERY;
        if (t.matches("(?s).*\\b(shipped|dispatched|in transit|on (?:its|the) way|handed over to|has left)\\b.*"))
            return OrderStatus.SHIPPED;
        if (t.matches("(?s).*\\b(packed|ready to ship|ready for dispatch|being processed|is being prepared)\\b.*"))
            return OrderStatus.PACKED;
        if (t.matches("(?s).*\\b(order (?:is |has been )?(?:placed|confirmed|received)|thank(?:s| you) for (?:your )?(?:order|shopping|purchase)|order confirmation|we(?:'ve| have) received your order|successfully placed)\\b.*"))
            return OrderStatus.PLACED;
        return null;
    }

    private static String findProduct(String text, String store) {
        for (Pattern pat : PRODUCT_PATTERNS) {
            Matcher m = pat.matcher(text);
            while (m.find()) {
                String c = m.group(1).trim().replaceAll("\\s+", " ")
                        .replaceAll("(?i)\\s*(?:x|qty:?)\\s*\\d+$", "")
                        .replaceAll("[\"“”]", "").trim();
                if (c.length() < 3 || c.length() > 90) continue;
                if (c.matches("(?i).*\\b(order|package|shipment|parcel|items?|your|account|payment|refund|otp)\\b.*")
                        && c.split("\\s+").length <= 3) continue;
                if (store != null && c.equalsIgnoreCase(store)) continue;
                if (c.matches("[\\d\\s#-]+")) continue;
                return c;
            }
        }
        return null;
    }

    static LocalDate findDate(String text) {
        Matcher m = DATE_PHRASE.matcher(text);
        if (!m.find()) return null;
        String d = m.group(1).toLowerCase(Locale.ROOT).replaceAll("(\\d)(st|nd|rd|th)", "$1").replace(",", "").trim();
        LocalDate today = LocalDate.now();
        if (d.equals("today")) return today;
        if (d.equals("tomorrow")) return today.plusDays(1);
        for (DayOfWeek dow : DayOfWeek.values()) {
            if (dow.name().toLowerCase(Locale.ROOT).startsWith(d.substring(0, Math.min(3, d.length())))
                    && d.matches("[a-z]+")) {
                return today.with(TemporalAdjusters.nextOrSame(dow));
            }
        }
        try {
            String[] parts = d.split("[\\s/-]+");
            int day, month;
            Integer year = null;
            if (parts[0].matches("\\d+") && parts.length >= 2 && parts[1].matches("\\d+")) {       // 12/11(/2026), day first
                day = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]);
                if (parts.length > 2) year = Integer.parseInt(parts[2]);
            } else if (parts[0].matches("\\d+")) {                                                  // 12 nov (2026)
                day = Integer.parseInt(parts[0]);
                month = monthOf(parts[1]);
                if (parts.length > 2) year = Integer.parseInt(parts[2]);
            } else {                                                                                // nov 12 (2026)
                month = monthOf(parts[0]);
                day = Integer.parseInt(parts[1]);
                if (parts.length > 2) year = Integer.parseInt(parts[2]);
            }
            if (year != null && year < 100) year += 2000;
            LocalDate date = LocalDate.of(year == null ? today.getYear() : year, month, day);
            if (year == null && date.isBefore(today.minusMonths(2))) date = date.plusYears(1);
            return date;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int monthOf(String s) {
        String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < 12; i++) if (s.startsWith(months[i])) return i + 1;
        throw new IllegalArgumentException("Unknown month " + s);
    }

    // ================================================================ AI (optional)

    private static final String AI_INSTRUCTION =
            "You extract order details from shopping emails and SMS. Reply with ONLY a JSON object, no prose, with keys: "
            + "is_order (boolean: true only for an order confirmation, shipping, delivery or cancellation update; "
            + "false for ads, OTPs, newsletters), store (shop name or null), order_id, product (short product name), "
            + "amount (number in rupees or null), status (one of PLACED, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, "
            + "CANCELLED or null), courier, tracking_number, expected_delivery (YYYY-MM-DD or null). Use null when unknown.";

    private void mergeAi(ParsedMessage p, String text, String from) throws IOException {
        String prompt = "Today's date: " + LocalDate.now() + "\nSender: " + (from == null ? "unknown" : from)
                + "\n\nMESSAGE:\n" + text.substring(0, Math.min(6000, text.length()));
        String reply = llm.complete(AI_INSTRUCTION, prompt).trim();
        reply = reply.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
        int start = reply.indexOf('{'), end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IOException("AI did not return JSON");
        Map<String, Object> j = JsonParser.parseObject(reply.substring(start, end + 1));
        if (j.isEmpty()) throw new IOException("AI JSON could not be read");

        Object isOrder = j.get("is_order");
        if (isOrder instanceof Boolean) p.orderMessage = (Boolean) isOrder || (p.orderMessage && p.orderId != null);
        p.store = pick(str(j.get("store")), p.store);
        p.orderId = pick(str(j.get("order_id")), p.orderId);
        p.product = pick(str(j.get("product")), p.product);
        p.courier = pick(str(j.get("courier")), p.courier);
        p.trackingNumber = pick(str(j.get("tracking_number")), p.trackingNumber);
        if (j.get("amount") instanceof Number) p.amount = ((Number) j.get("amount")).doubleValue();
        String status = str(j.get("status"));
        if (status != null) {
            try { p.status = OrderStatus.valueOf(status.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { }
        }
        String eta = str(j.get("expected_delivery"));
        if (eta != null) {
            try { p.expectedDelivery = LocalDate.parse(eta); } catch (RuntimeException ignored) { }
        }
        if (p.orderMessage && p.status == null) p.status = OrderStatus.PLACED;
        if (p.orderMessage) p.notes.removeIf(n -> n.startsWith("No order number"));
        p.parser = llm.getName();
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
    }

    private static String pick(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    /** Handy for the UI: some realistic sample messages to try. */
    public static Map<String, String[]> samples() {
        Map<String, String[]> s = new LinkedHashMap<>();
        s.put("Nykaa order email", new String[] {"Email", "Nykaa <orders@nykaa.com>",
                "Your Nykaa order has been placed!",
                "Hi Agrim,\nThank you for shopping with Nykaa. Your order #NYK10294831 has been confirmed.\n"
                + "Item: Lakme 9 to 5 Primer + Matte Lipstick\nOrder total: ₹849\n"
                + "Expected delivery by 12 Nov."});
        s.put("Nykaa shipped SMS", new String[] {"SMS", "", "",
                "Your Nykaa order NYK10294831 has been shipped via Delhivery. AWB 1490827734561. "
                + "Track: nyk.aa/t/xyz. Arriving by 11 Nov."});
        s.put("AJIO delivered SMS", new String[] {"SMS", "", "",
                "AJIO: Your package with Order No FN7781203 containing Levi's Men Slim Fit T-shirt has been delivered. "
                + "Hope you love it!"});
        s.put("Meesho out for delivery", new String[] {"SMS", "", "",
                "Meesho: Your order 145587992_1 for Women Printed Kurti is out for delivery today with Valmo. "
                + "Pay ₹329 on delivery."});
        s.put("Blinkit cancelled", new String[] {"Email", "Blinkit <no-reply@blinkit.com>",
                "Your Blinkit order was cancelled",
                "Order ID ORD7741820993 has been cancelled. A refund of Rs. 412 has been initiated to your UPI."});
        return s;
    }
}
