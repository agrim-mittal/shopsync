package com.shopsync;

import com.shopsync.ai.GeminiClient;
import com.shopsync.ai.LlmClient;
import com.shopsync.auth.SessionManager;
import com.shopsync.auth.User;
import com.shopsync.auth.UserStore;
import com.shopsync.data.Catalog;
import com.shopsync.data.SampleOrders;
import com.shopsync.inbox.ImportedOrderStore;
import com.shopsync.inbox.InboxService;
import com.shopsync.inbox.MessageParser;
import com.shopsync.marketplace.AmazonMarketplace;
import com.shopsync.marketplace.CromaMarketplace;
import com.shopsync.marketplace.FlipkartMarketplace;
import com.shopsync.marketplace.Marketplace;
import com.shopsync.marketplace.MyntraMarketplace;
import com.shopsync.model.Product;
import com.shopsync.server.WebServer;
import com.shopsync.service.LiveUpdateEngine;
import com.shopsync.service.OrderTrackingService;
import com.shopsync.service.PriceComparisonService;
import com.shopsync.service.ProductService;
import com.shopsync.service.ShoppingAssistant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ShopSync: one dashboard to compare prices, track orders and get AI shopping
 * advice across online marketplaces.
 *
 * Usage:  java -cp out com.shopsync.Main [port]
 * Then open http://localhost:8080 and sign in (demo: demo@shopsync.in / demo123).
 *
 * Optional: set GEMINI_API_KEY to let a real AI model write the assistant's replies.
 */
public class Main {

    public static final String DEMO_EMAIL = "demo@shopsync.in";
    public static final String DEMO_PASSWORD = "demo123";

    public static void main(String[] args) throws Exception {
        // Port: command-line argument, else the PORT environment variable (set by Render), else 8080.
        String envPort = System.getenv("PORT");
        int port = args.length > 0 ? Integer.parseInt(args[0])
                : envPort != null && !envPort.isBlank() ? Integer.parseInt(envPort.trim()) : 8080;

        // 1. Catalogue and marketplace connectors.
        List<Product> catalog = Catalog.load();
        Map<String, Marketplace> marketplaces = new LinkedHashMap<>();
        register(marketplaces, new AmazonMarketplace(catalog));
        register(marketplaces, new FlipkartMarketplace(catalog));
        register(marketplaces, new MyntraMarketplace(catalog));
        register(marketplaces, new CromaMarketplace(catalog));

        // 2. Accounts. Every new customer gets sample orders to track; a demo account is ready to use.
        UserStore users = new UserStore();
        SessionManager sessions = new SessionManager();
        User demo = users.register("Agrim Mittal", DEMO_EMAIL, DEMO_PASSWORD);
        SampleOrders.loadFor(demo.getId(), marketplaces);

        // 3. Services, including the AI assistant (uses Gemini if GEMINI_API_KEY is set).
        LlmClient llm = GeminiClient.fromEnvironment();
        PriceComparisonService priceService = new PriceComparisonService(marketplaces.values());
        ImportedOrderStore importedOrders = new ImportedOrderStore();
        OrderTrackingService orderService = new OrderTrackingService(marketplaces.values(), importedOrders);
        ProductService productService = new ProductService(catalog, marketplaces.values(), orderService, llm);
        ShoppingAssistant assistant = new ShoppingAssistant(productService, orderService, llm);
        LiveUpdateEngine liveEngine = new LiveUpdateEngine(marketplaces.values());
        InboxService inbox = new InboxService(new MessageParser(llm), importedOrders, orderService, liveEngine);
        liveEngine.start(4, 20);   // prices every 4 s; every 20 s one order (idle 3+ min) moves a stage

        // 4. Web server.
        Path webRoot = WebServer.defaultWebRoot();
        if (!Files.isDirectory(webRoot)) {
            System.err.println("Cannot find the 'web' folder. Run the program from the ShopSync project folder.");
            System.exit(1);
        }
        WebServer server = new WebServer(port, webRoot, users, sessions, priceService, orderService,
                productService, assistant, liveEngine, inbox,
                newUser -> SampleOrders.loadFor(newUser.getId(), marketplaces));
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            liveEngine.stop();
            priceService.shutdown();
        }));

        System.out.println("==================================================");
        System.out.println("  ShopSync is running");
        System.out.println("  Open   http://localhost:" + port + "   in your browser");
        System.out.println("  Demo login:  " + DEMO_EMAIL + "  /  " + DEMO_PASSWORD);
        System.out.println("  AI engine:   " + assistant.poweredBy());
        System.out.println("  Press Ctrl+C to stop");
        System.out.println("==================================================");
    }

    private static void register(Map<String, Marketplace> map, Marketplace m) {
        map.put(m.getName(), m);
    }
}
