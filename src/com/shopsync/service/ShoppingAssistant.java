package com.shopsync.service;

import com.shopsync.ai.LlmClient;
import com.shopsync.auth.User;
import com.shopsync.data.PolicyBook;
import com.shopsync.model.Category;
import com.shopsync.model.Order;
import com.shopsync.model.Policy;
import com.shopsync.model.Product;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ShopSync AI shopping assistant.
 *
 * How it works:
 *  1. Understand the message: detect the INTENT (recommend, compare, describe,
 *     policies, price history, orders) and extract ENTITIES (products, budget,
 *     category) with keyword rules.
 *  2. Fetch REAL data for that intent from the services (prices, policies, history).
 *  3. Write the reply: from templates by default, or, when an AI model is
 *     configured, by asking the model to phrase an answer using only that data
 *     (so it can't invent prices).
 * The reply also carries structured data (product cards, comparison table,
 * policy table, price chart) that the web page renders.
 */
public class ShoppingAssistant {

    public static final String SYSTEM_PROMPT =
            "You are ShopSync AI, a friendly, concise shopping assistant for Indian online shoppers. "
            + "You compare products across Amazon, Flipkart, Myntra and Croma. Prices are in Indian rupees (₹). "
            + "Only use facts given to you in the prompt; never invent prices, specs or policies. "
            + "Write in plain text with at most light **bold** for product names. Keep answers under 120 words.";

    public enum Intent { GREETING, ORDERS, COMPARE, POLICY, HISTORY, DESCRIBE, RECOMMEND, UNKNOWN }

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "the", "and", "with", "for", "vs", "versus", "compare", "between", "what", "which", "is", "are",
            "me", "my", "a", "an", "of", "to", "in", "on", "it", "its", "about", "tell", "show", "best", "good",
            "gb", "pro", "gen", "price", "prices", "policy", "return", "history", "under", "below", "buy",
            "should", "i", "can", "you", "do", "does", "how", "much", "than", "or", "please", "want", "need",
            "new", "classic", "fit", "inch", "series", "max"));

    private static final Pattern BUDGET = Pattern.compile(
            "(?:under|below|less than|within|upto|up to|max|maximum|budget(?: of| is)?|<)\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d+)?)\\s*(k|thousand|lakh)?",
            Pattern.CASE_INSENSITIVE);

    private final ProductService products;
    private final OrderTrackingService orders;
    private final LlmClient llm;
    /** Last products discussed per user, so follow-ups like "what's its return policy?" work. */
    private final Map<String, List<Product>> context = new ConcurrentHashMap<>();

    public ShoppingAssistant(ProductService products, OrderTrackingService orders, LlmClient llm) {
        this.products = products;
        this.orders = orders;
        this.llm = llm;
    }

    public String poweredBy() {
        return llm.isEnabled() ? llm.getName() : "ShopSync built-in AI engine";
    }

    // ===================================================================== entry point

    public Map<String, Object> reply(User user, String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        String text = message.toLowerCase(Locale.ROOT);

        List<Product> mentioned = findMentionedProducts(text);
        boolean followUp = mentioned.isEmpty() && refersBack(text);
        if (followUp) mentioned = context.getOrDefault(user.getId(), new ArrayList<>());

        Intent intent = detectIntent(text, mentioned);
        Reply r;
        switch (intent) {
            case GREETING:  r = greeting(user); break;
            case ORDERS:    r = orderStatus(user); break;
            case COMPARE:   r = compare(user, text); break;
            case POLICY:    r = policies(followUp ? mentioned.subList(0, Math.min(3, mentioned.size()))
                                    : mentioned.subList(0, Math.min(1, mentioned.size())), text); break;
            case HISTORY:   r = history(mentioned); break;
            case DESCRIBE:  r = describe(mentioned); break;
            case RECOMMEND: r = recommend(user, text); break;
            default:        r = fallback(user, text); break;
        }
        if (!r.focus.isEmpty()) context.put(user.getId(), r.focus);

        // Let the AI model phrase the answer from the facts we gathered (if configured).
        if (llm.isEnabled() && r.facts.length() > 0) {
            try {
                String prompt = "Customer (" + user.getFirstName() + ") asked: \"" + message + "\"\n\n"
                        + "FACTS FROM SHOPSYNC:\n" + r.facts
                        + "\nAnswer the customer using only these facts. The app will also show cards/tables for this data, "
                        + "so summarise and advise rather than listing every number.";
                r.text = llm.complete(SYSTEM_PROMPT, prompt);
            } catch (IOException e) {
                System.err.println("AI model unavailable, using built-in reply: " + e.getMessage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intent", intent);
        out.put("text", r.text);
        out.putAll(r.data);
        out.put("suggestions", r.suggestions);
        out.put("poweredBy", poweredBy());
        return out;
    }

    /** Working state for building one reply. */
    private static final class Reply {
        String text = "";
        final StringBuilder facts = new StringBuilder();
        final Map<String, Object> data = new LinkedHashMap<>();
        final List<String> suggestions = new ArrayList<>();
        List<Product> focus = new ArrayList<>();
    }

    // ===================================================================== understanding

    Intent detectIntent(String t, List<Product> mentioned) {
        if (t.isEmpty() || t.matches("^(hi|hii+|hello|hey|namaste|help|yo|good (morning|evening|afternoon))\\b.*")
                && mentioned.isEmpty() && t.length() < 30) return Intent.GREETING;
        if (has(t, "what can you do", "how do you work", "help me use")) return Intent.GREETING;
        if (has(t, "my order", "my orders", "track", "where is my", "delivery status", "when will my",
                "out for delivery", "shipped")) return Intent.ORDERS;
        if (has(t, "compare", " vs ", " vs.", "versus", "difference between", " or ") && !has(t, "return or", "refund or")
                && (mentioned.size() >= 2 || has(t, "compare"))) return Intent.COMPARE;
        if (has(t, "return", "refund", "policy", "policies", "warranty", "exchange", "cod", "cash on delivery",
                "replace", "replacement", "emi")) return Intent.POLICY;
        if (has(t, "history", "trend", "price change", "changed", "when should i buy", "when to buy", "good time",
                "right time", "should i buy", "lowest", "cheapest ever", "graph", "chart", "wait", "drop", "worth buying now",
                "buy now", "festive", "sale")) return Intent.HISTORY;
        if (!mentioned.isEmpty() && !has(t, "similar", "alternative", "instead of") && has(t, "tell me", "about", "describe", "what is", "details", "spec", "feature",
                "review", "worth", "good for", "explain", "info")) return Intent.DESCRIBE;
        if (mentioned.isEmpty() && findByTopic(t).isEmpty() && has(t, "deal", "deals", "discount", "offers", "hot"))
            return Intent.HISTORY;   // "what deals are hot?" → today's biggest price drops
        if (has(t, "recommend", "suggest", "best", "gift", "looking for", "need", "want", "show me", "under",
                "budget", "cheap", "affordable", "top", "deal", "should i get", "which", "similar", "alternative",
                "instead of", "options")) return Intent.RECOMMEND;
        if (!mentioned.isEmpty()) return Intent.DESCRIBE;
        return Intent.UNKNOWN;
    }

    private static boolean has(String t, String... words) {
        String padded = " " + t + " ";
        for (String w : words) {
            if (w.length() <= 4 && !w.contains(" ")) {
                if (padded.matches("(?s).*[^a-z0-9]" + Pattern.quote(w) + "[^a-z0-9].*")) return true;
            } else if (padded.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static boolean refersBack(String t) {
        return has(t, "it", "its", "it's", "this", "that", "them", "these", "those", "both", "the same");
    }

    /** Words that describe a KIND of product rather than a specific one; they count for less. */
    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList(
            "wireless", "headphones", "earbuds", "tws", "shoes", "running", "jeans", "sneakers", "laptop",
            "smart", "tv", "mouse", "flask", "hoodie", "sunglasses", "slim", "smartwatch", "air", "fryer",
            "pressure", "cooker", "black", "crystal", "4k", "essentials", "thermosteel", "watch"));

    /**
     * Products the message names specifically, best match first.
     * Scoring: a nickname/alias phrase = 4, a distinctive model word ("iphone", "s24") = 3,
     * the brand = 2, a generic word ("headphones") = 1. A product needs 3+ to count.
     */
    List<Product> findMentionedProducts(String text) {
        Map<Product, Integer> scores = new LinkedHashMap<>();
        String padded = " " + text.replaceAll("[^a-z0-9'\\-]+", " ") + " ";
        Set<String> words = new HashSet<>(Arrays.asList(text.split("[^a-z0-9]+")));
        for (Product p : products.catalog()) {
            int score = 0;
            String brand = p.getBrand().toLowerCase(Locale.ROOT);
            if (words.contains(brand.replaceAll("[^a-z0-9]", "")) || padded.contains(" " + brand + " ")) score += 2;
            for (String token : nameTokens(p)) {
                if (words.contains(token)) score += GENERIC.contains(token) ? 1 : 3;
            }
            for (String alias : p.getAliases()) {
                if (padded.contains(" " + alias + " ")) { score += 4; break; }
            }
            if (score >= 3) scores.put(p, score);
        }
        List<Map.Entry<Product, Integer>> entries = new ArrayList<>(scores.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        // Keep only matches reasonably close to the best one.
        List<Product> result = new ArrayList<>();
        int top = entries.isEmpty() ? 0 : entries.get(0).getValue();
        for (Map.Entry<Product, Integer> e : entries) {
            if (e.getValue() >= Math.max(3, top - 3)) result.add(e.getKey());
        }
        return result;
    }

    private static List<String> nameTokens(Product p) {
        List<String> tokens = new ArrayList<>();
        String brand = p.getBrand().toLowerCase(Locale.ROOT);
        for (String tok : p.getName().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (tok.length() < 2 || STOP.contains(tok) || tok.equals(brand)) continue;
            if (tok.matches("\\d+") && tok.length() > 2) continue;   // "128", "512" are storage sizes
            tokens.add(tok);
        }
        return tokens;
    }

    /** Products in a category the message names generically ("phones", "earbuds", "gifts"). */
    private List<Product> findByTopic(String text) {
        Set<String> topics = new LinkedHashSet<>();
        String[][] synonyms = {
                {"phone", "phone"}, {"mobile", "phone"}, {"smartphone", "phone"},
                {"headphone", "headphones"}, {"earbud", "earbuds"}, {"earphone", "earbuds"}, {"tws", "earbuds"},
                {"audio", "audio"}, {"music", "music"}, {"laptop", "laptop"}, {"computer", "computer"},
                {"tv", "tv"}, {"television", "tv"}, {"watch", "watch"}, {"mouse", "mouse"},
                {"shoe", "shoes"}, {"sneaker", "sneakers"}, {"running", "running"}, {"jeans", "jeans"},
                {"denim", "jeans"}, {"hoodie", "hoodie"}, {"sweatshirt", "hoodie"}, {"sunglass", "sunglasses"},
                {"cloth", "clothing"}, {"kitchen", "kitchen"}, {"cook", "cooking"}, {"fryer", "air fryer"},
                {"bottle", "bottle"}, {"flask", "flask"}, {"gift", "gift"}, {"travel", "travel"},
                {"fitness", "fitness"}, {"gym", "fitness"}, {"student", "student"}, {"college", "student"},
                {"work", "work"}, {"office", "work"}, {"winter", "winter"}, {"camera", "camera"},
                {"noise cancel", "noise cancelling"}, {"battery", "battery"}, {"gaming", "earbuds"}};
        for (String[] s : synonyms) {
            if (Pattern.compile("(^|[^a-z])" + Pattern.quote(s[0])).matcher(text).find()) topics.add(s[1]);
        }

        List<Product> list = new ArrayList<>();
        for (Product p : products.catalog()) {
            for (String topic : topics) {
                if (p.hasTag(topic)) { list.add(p); break; }
            }
        }
        return list;
    }

    private static Category findCategory(String text) {
        if (has(text, "electronic", "gadget")) return Category.ELECTRONICS;
        if (has(text, "fashion", "clothes", "clothing", "wear", "outfit")) return Category.FASHION;
        if (has(text, "home", "kitchen", "appliance")) return Category.HOME;
        return null;
    }

    static Double findBudget(String text) {
        Matcher m = BUDGET.matcher(text);
        if (!m.find()) return null;
        double value = Double.parseDouble(m.group(1).replace(",", ""));
        String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
        if (unit.equals("k") || unit.equals("thousand")) value *= 1000;
        if (unit.equals("lakh")) value *= 100_000;
        return value;
    }

    // ===================================================================== intents

    private Reply greeting(User user) {
        Reply r = new Reply();
        r.text = "Hi " + user.getFirstName() + ". I'm your ShopSync shopping assistant. I can:\n"
                + "• **Recommend** products for your needs and budget\n"
                + "• **Compare** products side by side across Amazon, Flipkart, Myntra and Croma\n"
                + "• **Describe** a product and tell you who it's best for\n"
                + "• Explain each store's **return, COD and warranty policies**\n"
                + "• Show **price history** and whether now is a good time to buy\n"
                + "• Check on your **orders**";
        r.suggestions.addAll(Arrays.asList("Best earbuds under ₹5,000", "Compare iPhone 15 vs Galaxy S24",
                "Return policy for Nike Air Max", "Should I buy the Sony XM5 now?"));
        return r;
    }

    private Reply orderStatus(User user) {
        Reply r = new Reply();
        List<Order> active = orders.getOrders(user.getId(), null, "active");
        if (active.isEmpty()) {
            r.text = "You have no orders in progress right now. Everything has been delivered.";
        } else {
            StringBuilder sb = new StringBuilder("You have **" + active.size() + " order"
                    + (active.size() > 1 ? "s" : "") + "** on the way:\n");
            for (Order o : active) {
                sb.append("• ").append(o.getProductName()).append(" (").append(o.getMarketplace()).append("): **")
                  .append(o.getStatus().getLabel()).append("**")
                  .append(o.getExpectedDelivery() != null ? ", expected " + o.getExpectedDelivery() : "").append('\n');
                r.facts.append(o.getProductName()).append(" from ").append(o.getMarketplace()).append(": ")
                       .append(o.getStatus().getLabel())
                       .append(o.getExpectedDelivery() != null ? ", expected delivery " + o.getExpectedDelivery() : "").append('\n');
            }
            r.text = sb.toString().trim();
        }
        r.data.put("orders", active);
        r.suggestions.addAll(Arrays.asList("Recommend something for me", "What deals are hot today?"));
        return r;
    }

    private Reply compare(User user, String text) {
        Reply r = new Reply();
        List<Product> picked = new ArrayList<>();

        // 1) "A vs B", "A and B", "A or B": match each side separately.
        String body = text.replaceAll("^.*?compare\\s*", "").replaceAll("difference between", "");
        for (String part : body.split("\\s+(?:vs\\.?|versus|and|or|with)\\s+|,")) {
            List<Product> m = findMentionedProducts(part);
            if (!m.isEmpty() && !picked.contains(m.get(0))) picked.add(m.get(0));
        }
        // 2) otherwise: every product named in the message, or follow-up context.
        if (picked.size() < 2) {
            for (Product p : findMentionedProducts(text)) if (!picked.contains(p)) picked.add(p);
        }
        if (picked.size() < 2 && refersBack(text)) {
            for (Product p : context.getOrDefault(user.getId(), new ArrayList<>())) if (!picked.contains(p)) picked.add(p);
        }
        // 3) otherwise: a topic like "compare earbuds" → the top products of that kind.
        if (picked.size() < 2) {
            List<Product> topic = findByTopic(text);
            if (topic.size() >= 2) {
                picked.clear();
                for (Map<String, Object> rec : products.recommend(null, null, findBudget(text), null, 30)) {
                    Product p = (Product) rec.get("product");
                    if (topic.contains(p) && picked.size() < 3) picked.add(p);
                }
            }
        }
        if (picked.size() < 2) {
            r.text = "Which products should I compare? Try something like **\"Compare iPhone 15 vs Galaxy S24\"** "
                    + "or **\"Compare earbuds\"**.";
            r.suggestions.addAll(Arrays.asList("Compare iPhone 15 vs Galaxy S24", "Compare Sony XM5 and AirPods Pro",
                    "Compare running shoes"));
            return r;
        }
        if (picked.size() > 3) picked = picked.subList(0, 3);

        Map<String, Object> comparison = products.compareProducts(picked);
        r.data.put("comparison", comparison);
        r.focus = new ArrayList<>(picked);
        r.text = "Here's how they stack up across every store. " + comparison.get("verdict");
        for (Product p : picked) r.facts.append(products.factSheet(p)).append('\n');
        r.facts.append("Comparison verdict: ").append(comparison.get("verdict")).append('\n');
        r.suggestions.add("Return policies for these");
        r.suggestions.add("Price history of " + picked.get(0).getShortName());
        return r;
    }

    private Reply policies(List<Product> mentioned, String text) {
        Reply r = new Reply();
        if (mentioned.isEmpty()) {
            // General question: summarise each store's policy for the category asked about (default electronics).
            Category c = findCategory(text);
            if (c == null) {
                List<Product> topic = findByTopic(text);
                c = topic.isEmpty() ? Category.ELECTRONICS : topic.get(0).getCategory();
            }
            List<Policy> list = new ArrayList<>();
            for (String store : new String[] {"Amazon", "Flipkart", "Myntra", "Croma"}) {
                Policy p = PolicyBook.get(store, c);
                if (p != null && p.getReturnDays() > 0) list.add(p);
            }
            Policy best = ProductService.mostFlexible(list);
            StringBuilder sb = new StringBuilder("Typical return policies for **" + c.getLabel() + "**:\n");
            for (Policy p : list) {
                sb.append("• ").append(p.getMarketplace()).append(": ").append(p.summary())
                  .append(p.isCashOnDelivery() ? ", COD available" : "").append('\n');
                r.facts.append(p.getMarketplace()).append(": ").append(p.summary()).append(", warranty: ")
                       .append(p.getWarranty()).append(", highlights: ").append(p.getHighlights()).append('\n');
            }
            if (best != null) sb.append("Most flexible: **").append(best.getMarketplace()).append("**.");
            r.text = sb.toString().trim();
            Map<String, Object> pol = new LinkedHashMap<>();
            pol.put("title", c.getLabel() + " policies");
            pol.put("policies", list);
            pol.put("mostFlexibleStore", best == null ? null : best.getMarketplace());
            pol.put("disclaimer", PolicyBook.DISCLAIMER);
            r.data.put("policies", Arrays.asList(pol));
            r.suggestions.addAll(Arrays.asList("Return policy for iPhone 15", "Fashion return policies"));
            return r;
        }

        List<Map<String, Object>> blocks = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (Product p : mentioned) {
            List<Policy> list = products.policies(p);
            Policy best = ProductService.mostFlexible(list);
            sb.append("Policies for **").append(p.getShortName()).append("** differ by store:\n");
            for (Policy pol : list) {
                sb.append("• **").append(pol.getMarketplace()).append("**: ").append(pol.summary())
                  .append(pol.isCashOnDelivery() ? ", COD available" : "")
                  .append(pol.hasExchangeOffer() ? ", exchange offer" : "").append('\n');
                r.facts.append(p.getShortName()).append(" on ").append(pol.getMarketplace()).append(": ")
                       .append(pol.summary()).append("; COD: ").append(pol.isCashOnDelivery())
                       .append("; warranty: ").append(pol.getWarranty())
                       .append("; highlights: ").append(pol.getHighlights()).append('\n');
            }
            if (best != null) {
                sb.append("Best for peace of mind: **").append(best.getMarketplace()).append("** (")
                  .append(best.summary()).append(").\n\n");
            }
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("title", p.getShortName());
            block.put("product", products.summary(p));
            block.put("policies", list);
            block.put("mostFlexibleStore", best == null ? null : best.getMarketplace());
            block.put("disclaimer", PolicyBook.DISCLAIMER);
            blocks.add(block);
        }
        r.text = sb.toString().trim();
        r.data.put("policies", blocks);
        r.focus = new ArrayList<>(mentioned);
        Product first = mentioned.get(0);
        r.suggestions.add("Price history of " + first.getShortName());
        r.suggestions.add("Tell me about " + first.getShortName());
        return r;
    }

    private Reply history(List<Product> mentioned) {
        Reply r = new Reply();
        if (mentioned.isEmpty()) {
            // No product named: show today's best drops instead.
            List<Map<String, Object>> deals = products.deals(4);
            StringBuilder sb = new StringBuilder("Here are the products furthest below their usual price right now:\n");
            for (Map<String, Object> d : deals) {
                Product p = (Product) d.get("product");
                sb.append(String.format("• **%s**: ₹%,.0f on %s, %.1f%% below its 30-day average%n",
                        p.getShortName(), ((Number) d.get("bestPrice")).doubleValue(), d.get("bestMarketplace"),
                        ((Number) d.get("belowAverage")).doubleValue()));
                r.facts.append(products.factSheet(p)).append('\n');
            }
            r.text = sb.toString().trim();
            r.data.put("products", deals);
            r.suggestions.add("Price history of iPhone 15");
            return r;
        }
        Product p = mentioned.get(0);
        PriceInsight insight = products.insight(p, 90);
        r.text = String.format("**%s** has ranged from ₹%,.0f to ₹%,.0f over the last 90 days. "
                        + "Its lowest was ₹%,.0f on %s (%s). Today's best price is ₹%,.0f and the 30-day average is ₹%,.0f. %s",
                p.getShortName(), insight.getLowest(), insight.getHighest(), insight.getLowest(),
                insight.getLowestStore(), insight.getLowestDate(), insight.getCurrent(), insight.getAverage30(),
                insight.getVerdictText());
        r.facts.append(products.factSheet(p));
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("product", products.summary(p));
        h.put("insight", insight);
        r.data.put("history", h);
        r.focus = new ArrayList<>(Arrays.asList(p));
        r.suggestions.add("Return policy for " + p.getShortName());
        r.suggestions.add("Similar products to " + p.getShortName());
        return r;
    }

    private Reply describe(List<Product> mentioned) {
        Reply r = new Reply();
        if (mentioned.isEmpty()) return fallbackHelp();
        Product p = mentioned.get(0);
        r.text = products.templateDescription(p);
        r.facts.append(products.factSheet(p));
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(products.summary(p));
        r.data.put("products", cards);
        r.focus = new ArrayList<>(Arrays.asList(p));
        r.suggestions.add("Price history of " + p.getShortName());
        r.suggestions.add("Return policy for " + p.getShortName());
        if (mentioned.size() > 1) r.suggestions.add("Compare " + p.getShortName() + " vs " + mentioned.get(1).getShortName());
        return r;
    }

    private Reply recommend(User user, String text) {
        Reply r = new Reply();
        Double budget = findBudget(text);
        Category category = findCategory(text);
        List<Product> topic = findByTopic(text);
        List<Product> named = findMentionedProducts(text);
        boolean similar = has(text, "similar", "like", "alternative", "instead of");

        List<Map<String, Object>> picks = new ArrayList<>();
        for (Map<String, Object> rec : products.recommend(user, null, budget, category, 50)) {
            Product p = (Product) rec.get("product");
            if (similar && !named.isEmpty()) {
                Product ref = named.get(0);
                if (p.equals(ref) || !shareKind(p, ref)) continue;
            } else if (!topic.isEmpty() && !topic.contains(p)) {
                continue;
            } else if (topic.isEmpty() && !named.isEmpty() && !named.contains(p)) {
                continue;
            }
            picks.add(rec);
            if (picks.size() == 4) break;
        }

        String what = topic.isEmpty() ? (category == null ? "picks" : category.getLabel().toLowerCase() + " picks")
                : "picks for " + String.join(", ", topicWords(text));
        if (picks.isEmpty()) {
            r.text = "I couldn't find anything matching that" + (budget != null ? String.format(" under ₹%,.0f", budget) : "")
                    + ". Try a higher budget or a different category.";
            r.suggestions.addAll(Arrays.asList("Recommend something for me", "Best deals today"));
            return r;
        }
        StringBuilder sb = new StringBuilder("Here are my top " + what
                + (budget != null ? String.format(" under ₹%,.0f", budget) : "") + ":\n");
        for (Map<String, Object> rec : picks) {
            Product p = (Product) rec.get("product");
            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>) rec.get("reasons");
            sb.append(String.format("• **%s**: ₹%,.0f on %s. %s%n", p.getShortName(),
                    ((Number) rec.get("bestPrice")).doubleValue(), rec.get("bestMarketplace"), String.join(" · ", reasons)));
            r.facts.append(products.factSheet(p)).append("Why recommended: ").append(reasons).append("\n\n");
            r.focus.add(p);
        }
        r.text = sb.toString().trim();
        r.data.put("products", picks);
        if (picks.size() >= 2) {
            r.suggestions.add("Compare " + ((Product) picks.get(0).get("product")).getShortName() + " vs "
                    + ((Product) picks.get(1).get("product")).getShortName());
        }
        r.suggestions.add("Tell me about " + ((Product) picks.get(0).get("product")).getShortName());
        return r;
    }

    private static boolean shareKind(Product a, Product b) {
        return a.kind().equals(b.kind()) || (a.getCategory() == b.getCategory()
                && (a.hasTag("audio") && b.hasTag("audio") || a.hasTag("shoes") && b.hasTag("shoes")
                || a.hasTag("phone") && b.hasTag("phone") || a.hasTag("kitchen") && b.hasTag("kitchen")
                || a.hasTag("clothing") && b.hasTag("clothing")));
    }

    private static List<String> topicWords(String text) {
        List<String> words = new ArrayList<>();
        for (String w : new String[] {"phones", "earbuds", "headphones", "laptops", "tv", "watch", "shoes",
                "sneakers", "gifts", "kitchen", "travel", "fitness", "students", "office", "winter"}) {
            if (Pattern.compile("(^|[^a-z])" + w.replaceAll("s$", "")).matcher(text).find()) words.add(w);
            if (words.size() == 2) break;
        }
        return words.isEmpty() ? Arrays.asList("you") : words;
    }

    private Reply fallback(User user, String text) {
        // With an AI model available, let it try to answer using a compact view of the whole catalogue.
        if (llm.isEnabled()) {
            Reply r = new Reply();
            r.text = fallbackHelp().text;
            for (Product p : products.catalog()) {
                Map<String, Object> s = products.summary(p);
                r.facts.append(String.format("%s (%s): best ₹%,.0f on %s, %.1f★, good for %s%n", p.getName(),
                        p.getCategory().getLabel(), ((Number) s.get("bestPrice")).doubleValue(), s.get("bestMarketplace"),
                        ((Number) s.get("rating")).doubleValue(), p.getGoodFor()));
            }
            r.suggestions.addAll(fallbackHelp().suggestions);
            return r;
        }
        // Otherwise treat the message as a search.
        List<Product> topic = findByTopic(text);
        if (!topic.isEmpty()) return recommend(user, text);
        return fallbackHelp();
    }

    private Reply fallbackHelp() {
        Reply r = new Reply();
        r.text = "I'm not sure I understood that. You can ask me things like:\n"
                + "• \"Best phone under ₹30,000\"\n• \"Compare Sony XM5 vs AirPods Pro\"\n"
                + "• \"Return policy for Levi's jeans\"\n• \"Price history of Galaxy S24\"\n• \"Where are my orders?\"";
        r.suggestions.addAll(Arrays.asList("Best phone under ₹30,000", "Gift ideas under ₹2,000",
                "Price history of Galaxy S24"));
        return r;
    }
}
