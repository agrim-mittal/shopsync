package com.shopsync.server;

import com.shopsync.auth.SessionManager;
import com.shopsync.auth.User;
import com.shopsync.auth.UserStore;
import com.shopsync.inbox.InboxService;
import com.shopsync.inbox.MessageParser;
import com.shopsync.model.Category;
import com.shopsync.model.Order;
import com.shopsync.model.Product;
import com.shopsync.service.LiveUpdateEngine;
import com.shopsync.service.OrderTrackingService;
import com.shopsync.service.PriceComparisonService;
import com.shopsync.service.ProductService;
import com.shopsync.service.ShoppingAssistant;
import com.shopsync.util.Json;
import com.shopsync.util.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * HTTP layer built on the JDK's own lightweight server (com.sun.net.httpserver),
 * so no framework is required.
 *
 * Public:   /login.html, static assets, POST /api/auth/signup, POST /api/auth/login,
 *           POST /api/inbox/hook/{secret} (authenticated by the secret)
 * Signed-in only (session cookie): everything else, see {@link #route}.
 */
public class WebServer {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final HttpServer server;
    private final Path webRoot;
    private final UserStore users;
    private final SessionManager sessions;
    private final PriceComparisonService priceService;
    private final OrderTrackingService orderService;
    private final ProductService productService;
    private final ShoppingAssistant assistant;
    private final LiveUpdateEngine liveEngine;
    private final InboxService inbox;
    private final Consumer<User> onSignup;

    public WebServer(int port, Path webRoot, UserStore users, SessionManager sessions,
                     PriceComparisonService priceService, OrderTrackingService orderService,
                     ProductService productService, ShoppingAssistant assistant,
                     LiveUpdateEngine liveEngine, InboxService inbox, Consumer<User> onSignup) throws IOException {
        this.webRoot = webRoot.toAbsolutePath().normalize();
        this.users = users;
        this.sessions = sessions;
        this.priceService = priceService;
        this.orderService = orderService;
        this.productService = productService;
        this.assistant = assistant;
        this.liveEngine = liveEngine;
        this.inbox = inbox;
        this.onSignup = onSignup;

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(12));
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
    }

    public void start() { server.start(); }

    public void stop() { server.stop(0); }

    // ==================================================================== API

    /** A parsed request: the bits every route needs. */
    private static final class Request {
        final HttpExchange ex;
        final String method;
        final String path;
        final Map<String, String> query;
        User user;

        Request(HttpExchange ex) {
            this.ex = ex;
            this.method = ex.getRequestMethod();
            this.path = ex.getRequestURI().getPath();
            this.query = parseQuery(ex.getRequestURI().getRawQuery());
        }

        String q(String key) { return query.get(key); }

        int qInt(String key, int def, int min, int max) {
            try {
                return Math.max(min, Math.min(max, Integer.parseInt(query.get(key))));
            } catch (RuntimeException e) {
                return def;
            }
        }
    }

    private void handleApi(HttpExchange ex) throws IOException {
        Request req = new Request(ex);
        try {
            if (req.path.startsWith("/api/auth/")) {
                handleAuth(req);
                return;
            }
            if (req.path.startsWith("/api/inbox/hook/")) {   // authenticated by the secret in the URL, not a cookie
                handleInboxHook(req);
                return;
            }
            req.user = currentUser(ex);
            if (req.user == null) {
                sendJson(ex, 401, error("Please sign in."));
                return;
            }
            route(req);
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendJson(ex, 500, error("Server error: " + e.getMessage()));
        }
    }

    private void route(Request r) throws IOException {
        String p = r.path, m = r.method;
        HttpExchange ex = r.ex;
        String uid = r.user.getId();

        if (p.equals("/api/compare") && m.equals("GET")) {
            sendJson(ex, 200, priceService.compare(r.q("q"), r.q("category")));

        } else if (p.equals("/api/products") && m.equals("GET")) {
            List<Object> list = new ArrayList<>();
            for (Product prod : productService.catalog()) list.add(productService.summary(prod));
            sendJson(ex, 200, list);

        } else if (p.startsWith("/api/products/") && m.equals("GET")) {
            String[] parts = p.substring("/api/products/".length()).split("/");
            Product prod = productService.find(parts[0]);
            if (prod == null) { sendJson(ex, 404, error("No such product")); return; }
            if (parts.length == 1) {
                r.user.recordView(prod.getId());
                sendJson(ex, 200, productService.detail(prod, r.qInt("days", 90, 7, 90)));
            } else if (parts[1].equals("ai-summary")) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("text", productService.aiDescription(prod));
                out.put("poweredBy", assistant.poweredBy());
                sendJson(ex, 200, out);
            } else if (parts[1].equals("history")) {
                sendJson(ex, 200, productService.insight(prod, r.qInt("days", 90, 7, 90)));
            } else {
                sendJson(ex, 404, error("Unknown product endpoint"));
            }

        } else if (p.equals("/api/compare-products") && m.equals("GET")) {
            List<Product> picked = new ArrayList<>();
            for (String id : (r.q("ids") == null ? "" : r.q("ids")).split(",")) {
                Product prod = productService.find(id.trim());
                if (prod != null && !picked.contains(prod)) picked.add(prod);
            }
            if (picked.size() < 2) { sendJson(ex, 400, error("Pick at least two products to compare.")); return; }
            sendJson(ex, 200, productService.compareProducts(picked.subList(0, Math.min(4, picked.size()))));

        } else if (p.equals("/api/recommendations") && m.equals("GET")) {
            Category cat = null;
            for (Category c : Category.values()) if (c.getLabel().equalsIgnoreCase(r.q("category"))) cat = c;
            sendJson(ex, 200, productService.recommend(r.user, null, null, cat, r.qInt("limit", 8, 1, 18)));

        } else if (p.equals("/api/deals") && m.equals("GET")) {
            sendJson(ex, 200, productService.deals(r.qInt("limit", 6, 1, 18)));

        } else if (p.equals("/api/assistant") && m.equals("POST")) {
            Map<String, Object> body = JsonParser.parseObject(readBody(ex));
            Object msg = body.get("message");
            sendJson(ex, 200, assistant.reply(r.user, msg == null ? "" : msg.toString()));

        } else if (p.equals("/api/orders") && m.equals("GET")) {
            sendJson(ex, 200, orderService.getOrders(uid, r.q("marketplace"), r.q("filter")));

        } else if (p.startsWith("/api/orders/") && m.equals("GET")) {
            String id = URLDecoder.decode(p.substring("/api/orders/".length()), StandardCharsets.UTF_8);
            Order o = orderService.findById(uid, id);
            if (o == null) sendJson(ex, 404, error("Order not found: " + id));
            else sendJson(ex, 200, o);

        } else if (p.equals("/api/summary") && m.equals("GET")) {
            Map<String, Object> summary = orderService.getSummary(uid);
            summary.put("lastPriceRefresh", liveEngine.getLastPriceRefresh().toString());
            sendJson(ex, 200, summary);

        } else if (p.equals("/api/alerts") && m.equals("GET")) {
            sendJson(ex, 200, liveEngine.getAlerts(uid));

        } else if (p.equals("/api/inbox") && m.equals("GET")) {
            String token = inbox.tokenFor(uid);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("hookUrl", baseUrl(ex) + "/api/inbox/hook/" + token);
            out.put("activity", inbox.activity(uid));
            out.put("aiEngine", assistant.poweredBy());
            List<Map<String, String>> samples = new ArrayList<>();
            for (Map.Entry<String, String[]> e : MessageParser.samples().entrySet()) {
                Map<String, String> sm = new LinkedHashMap<>();
                sm.put("label", e.getKey());
                sm.put("source", e.getValue()[0]);
                sm.put("from", e.getValue()[1]);
                sm.put("subject", e.getValue()[2]);
                sm.put("body", e.getValue()[3]);
                samples.add(sm);
            }
            out.put("samples", samples);
            sendJson(ex, 200, out);

        } else if (p.equals("/api/inbox/preview") && m.equals("POST")) {
            Map<String, Object> b = JsonParser.parseObject(readBody(ex));
            sendJson(ex, 200, inbox.preview(uid, str(b.get("subject")), str(b.get("body")), str(b.get("from"))));

        } else if (p.equals("/api/inbox/import") && m.equals("POST")) {
            Map<String, Object> b = JsonParser.parseObject(readBody(ex));
            sendJson(ex, 200, inbox.importMessage(uid, str(b.get("subject")), str(b.get("body")),
                    str(b.get("from")), str(b.get("source"))));

        } else if (p.equals("/api/inbox/token/regenerate") && m.equals("POST")) {
            inbox.regenerateToken(uid);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("hookUrl", baseUrl(ex) + "/api/inbox/hook/" + inbox.tokenFor(uid));
            sendJson(ex, 200, out);

        } else if (p.equals("/api/inbox/gmail-script") && m.equals("GET")) {
            String script = inbox.gmailScript(baseUrl(ex) + "/api/inbox/hook/" + inbox.tokenFor(uid));
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"shopsync-gmail.gs\"");
            send(ex, 200, script.getBytes(StandardCharsets.UTF_8));

        } else if (p.equals("/api/demo/order") && m.equals("POST")) {
            Order o = liveEngine.placeRandomOrder(uid);
            sendJson(ex, o == null ? 503 : 201, o == null ? error("No stock available") : o);

        } else {
            sendJson(ex, 404, error("Unknown endpoint: " + m + " " + p));
        }
    }

    // ============================================================ inbox webhook

    /**
     * POST /api/inbox/hook/{secret}: lets Gmail scripts, iPhone Shortcuts or SMS-forwarding apps
     * send messages without logging in. Accepts JSON ({subject, body|text|message, from, source})
     * or plain text.
     */
    private void handleInboxHook(Request r) throws IOException {
        HttpExchange ex = r.ex;
        if (!r.method.equals("POST")) { sendJson(ex, 405, error("Use POST to send a message here.")); return; }
        String userId = inbox.userForToken(r.path.substring("/api/inbox/hook/".length()));
        if (userId == null || users.findById(userId) == null) { sendJson(ex, 404, error("Unknown forwarding address.")); return; }

        String raw = readBody(ex);
        String subject = null, body = raw, from = r.q("from"), source = r.q("source");
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        if ((type != null && type.contains("json")) || raw.trim().startsWith("{")) {
            Map<String, Object> b = JsonParser.parseObject(raw);
            subject = str(b.get("subject"));
            body = firstNonNull(str(b.get("body")), str(b.get("text")), str(b.get("message")), str(b.get("content")), str(b.get("sms")));
            from = firstNonNull(str(b.get("from")), str(b.get("sender")), from);
            source = firstNonNull(str(b.get("source")), source, "SMS");
        }
        if ((body == null || body.isBlank()) && (subject == null || subject.isBlank())) {
            sendJson(ex, 400, error("The message was empty."));
            return;
        }
        Map<String, Object> result = inbox.importMessage(userId, subject, body, from, source == null ? "SMS" : source);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", result.get("action"));
        out.put("reason", result.get("reason"));
        sendJson(ex, 200, out);
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    /** The public address of this server, e.g. https://shopsync.onrender.com (Render passes the scheme in a header). */
    private static String baseUrl(HttpExchange ex) {
        String proto = ex.getRequestHeaders().getFirst("X-Forwarded-Proto");
        String host = ex.getRequestHeaders().getFirst("Host");
        return (proto == null ? "http" : proto.split(",")[0].trim()) + "://" + (host == null ? "localhost" : host);
    }

    private static boolean isHttps(HttpExchange ex) {
        String proto = ex.getRequestHeaders().getFirst("X-Forwarded-Proto");
        return proto != null && proto.startsWith("https");
    }

    // ================================================================== auth

    private void handleAuth(Request r) throws IOException {
        HttpExchange ex = r.ex;
        try {
            if (r.path.equals("/api/auth/me") && r.method.equals("GET")) {
                User u = currentUser(ex);
                if (u == null) sendJson(ex, 401, error("Not signed in"));
                else sendJson(ex, 200, meJson(u));

            } else if (r.path.equals("/api/auth/login") && r.method.equals("POST")) {
                Map<String, Object> body = JsonParser.parseObject(readBody(ex));
                User u = users.authenticate(str(body.get("email")), str(body.get("password")));
                startSession(ex, u);
                sendJson(ex, 200, meJson(u));

            } else if (r.path.equals("/api/auth/signup") && r.method.equals("POST")) {
                Map<String, Object> body = JsonParser.parseObject(readBody(ex));
                User u = users.register(str(body.get("name")), str(body.get("email")), str(body.get("password")));
                onSignup.accept(u);
                startSession(ex, u);
                sendJson(ex, 201, meJson(u));

            } else if (r.path.equals("/api/auth/logout") && r.method.equals("POST")) {
                sessions.destroy(readCookie(ex, SessionManager.COOKIE_NAME));
                ex.getResponseHeaders().add("Set-Cookie", SessionManager.COOKIE_NAME
                        + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
                sendJson(ex, 200, error("Signed out"));

            } else {
                sendJson(ex, 404, error("Unknown auth endpoint"));
            }
        } catch (UserStore.AuthException e) {
            sendJson(ex, 400, error(e.getMessage()));
        }
    }

    private Map<String, Object> meJson(User u) {
        Map<String, Object> m = new LinkedHashMap<>(u.toJsonMap());
        m.put("aiEngine", assistant.poweredBy());
        return m;
    }

    private void startSession(HttpExchange ex, User u) {
        String token = sessions.create(u);
        ex.getResponseHeaders().add("Set-Cookie", SessionManager.COOKIE_NAME + "=" + token
                + "; Path=/; Max-Age=" + SessionManager.LIFETIME.getSeconds() + "; HttpOnly; SameSite=Lax"
                + (isHttps(ex) ? "; Secure" : ""));
    }

    private User currentUser(HttpExchange ex) {
        return users.findById(sessions.resolve(readCookie(ex, SessionManager.COOKIE_NAME)));
    }

    private static String readCookie(HttpExchange ex, String name) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) return null;
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) return kv[1];
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    // =============================================================== helpers

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY_BYTES) throw new IOException("Request body too large");
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String val = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(val, StandardCharsets.UTF_8));
        }
        return map;
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        send(ex, status, bytes);
    }

    // ================================================================ static

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        boolean signedIn = currentUser(ex) != null;

        // The app requires a session; the login page is for signed-out visitors.
        if (path.equals("/") || path.equals("/index.html")) {
            if (!signedIn) { redirect(ex, "/login.html"); return; }
            path = "/index.html";
        } else if (path.equals("/login.html") && signedIn) {
            redirect(ex, "/");
            return;
        }

        // Resolve inside web/ and refuse anything that tries to escape it (../ attacks).
        Path file = webRoot.resolve(path.substring(1)).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            send(ex, 404, "404 Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        ex.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        send(ex, 200, Files.readAllBytes(file));
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int status, byte[] bytes) throws IOException {
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static Path defaultWebRoot() {
        return Paths.get("web");
    }
}
