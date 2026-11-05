# ShopSync: Unified Marketplace Dashboard with an AI Shopping Assistant

One place to **compare live prices** across Amazon, Flipkart, Myntra and Croma, **track every order**,
see **90-day price history**, compare **store policies** (returns, COD, warranty), and chat with an
**AI assistant** that recommends, compares and explains products.

Built in **plain Java (JDK 11+)** with no frameworks and no external libraries. The Java backend
runs its own web server, handles logins, and serves a responsive, animated web app.

> Marketplace prices, orders, price history and policies are **simulated** with realistic data.
> Every store connector sits behind one Java interface, so a real API can replace it later.

---

## 1. How to run

**Requirement:** JDK 11 or newer (`java -version` to check).

| OS | Command (inside the `ShopSync` folder) |
|---|---|
| macOS / Linux | `chmod +x run.sh` (first time only), then `./run.sh` |
| Windows | double-click `run.bat` |

Open **http://localhost:8080**, then click **"Try the demo account"**
(`demo@shopsync.in` / `demo123`) or create your own account.

### Optional: use a real AI model (free)
The assistant works fully offline with its built-in engine. To have Google Gemini write its replies
and product descriptions (using ShopSync's real data, so it can't invent prices):

1. Get a free key at https://aistudio.google.com/apikey
2. Start the app with the key set:
   - macOS / Linux: `GEMINI_API_KEY=your_key ./run.sh`
   - Windows (cmd): `set GEMINI_API_KEY=your_key` then `run.bat`
3. The terminal shows `AI engine: Gemini (gemini-3.8-flash)`. Change the model with `GEMINI_MODEL` (e.g. if Google retires this one, the error message names the replacement).

If the key is missing or the call fails, ShopSync quietly falls back to the built-in engine.

---

## 2. Put it online (Render)

The project is ready for [Render](https://render.com): it includes a `Dockerfile` (Render runs Java through
Docker) and a `render.yaml` blueprint, and the app reads Render's `PORT` setting automatically.

**Step 1: put the code on GitHub** (create an empty repository at https://github.com/new, e.g. `shopsync`):
```bash
cd ~/Downloads/ShopSync
git init
git add .
git commit -m "ShopSync"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/shopsync.git
git push -u origin main
```
When asked for a password, use a GitHub *personal access token* (GitHub → Settings → Developer settings →
Personal access tokens), or upload the folder with GitHub Desktop instead.

**Step 2: create the service on Render**
1. Sign in at https://dashboard.render.com with your GitHub account.
2. Click **New + → Blueprint**, pick the `shopsync` repository. Render reads `render.yaml`.
3. It asks for `GEMINI_API_KEY`: paste your key, or leave it empty to use the built-in AI.
4. Click **Apply**. The first build takes a few minutes; your site will be at `https://shopsync-xxxx.onrender.com`.

(Alternative: **New + → Web Service**, pick the repo, Render detects the Dockerfile; choose the **Free** instance
type and add `GEMINI_API_KEY` under Environment.)

**Good to know about the free plan**
- It sleeps after 15 minutes without visitors; the next visit takes up to a minute to wake it.
- Data is kept in memory, so accounts and imported orders reset whenever it restarts or redeploys
  (the demo account is always recreated). A database would fix this (see Future scope).
- Every `git push` to `main` redeploys automatically.

---

## 3. Features

**Login & accounts**
- Sign in / create account page with a password strength meter and one-click demo login
- Passwords hashed with **PBKDF2-HMAC-SHA256 + per-user salt**; sessions use a random token in an HttpOnly cookie
- Each customer sees their own orders, alerts and personalised recommendations

**Discover (home)**
- Personal greeting, key metrics and an "Ask the assistant" bar with suggested questions
- **Picked for you**: AI recommendations scored on your orders, products you've viewed, ratings, discount and price trend, each with its reasons ("Because you viewed…", "Near its 90-day low")
- **Biggest price drops today**: products furthest below their 30-day average

**Shop & Compare**
- Search all 4 stores **in parallel**; each card shows every store's live price as bars, best deal and savings
- Prices update every 4 seconds; changes are briefly highlighted
- Tick **Compare** on 2–3 products and open a side-by-side table with the winner of each row marked and an AI verdict

**Product page**
- **AI summary**: description, who it's for, best deal and buying advice 
- Live offers from each store, plus specs
- **Price history chart** (30/60/90 days): one line per store plus the best-price line, festive-sale band, hover tooltip, and a verdict: *Great time to buy / Good price / Fair price / Wait for a drop*
- **Store policies compared**: return window, refund vs replacement-only, COD, exchange, no-cost EMI, warranty and highlights, with the most flexible store marked

**AI shopping assistant (chat)**
- "Best earbuds under ₹5,000" → recommendations with product cards
- "Compare iPhone 15 vs Galaxy S24" → comparison table + verdict
- "Return policy for Nike Air Max" → policy table across stores
- "Should I buy the Sony XM5 now?" → price-history chart + verdict
- "Tell me about the air fryer", "Gift ideas under 2k", "Where are my orders?", "Similar to Levi's jeans"
- Follow-ups work: "what about **its** return policy?"

**Orders**: one timeline for all stores, progress bars, expandable tracking history, live status changes.

**Import orders from emails and messages (any store)**
- Orders page → **Import from email / SMS**. Paste an order email or text from *any* shop (Nykaa, AJIO,
  Meesho, Zepto, Lenskart…) and click **Read message** to see what was found: store, order number, product,
  amount, status, courier, tracking/AWB number and expected delivery, with a confidence score.
- Later messages update the *same* order (matched by order number, tracking number, or store + product), so
  "placed → shipped → out for delivery → delivered" builds a real timeline. OTPs and adverts are ignored.
- **Forward automatically**: every user gets a private forwarding address (secret URL). Send messages to it from
  - **Gmail**: a ready-made Google Apps Script (downloaded with your address filled in) checks for order emails every 15 minutes;
  - **iPhone**: a Shortcuts automation for messages containing "order";
  - **Android**: any "SMS forwarder to webhook" app.
  This needs the site to be online (e.g. on Render) so Gmail and phones can reach it.
- With a Gemini key, Gemini reads the messages (returns structured JSON); otherwise built-in rules do.

**Design**: a clean, minimal interface: neutral greys with a single blue accent, the Inter typeface,
simple line icons, light and dark themes, subtle transitions, and a mobile layout with bottom navigation.
Motion is reduced automatically if the device asks for it.

---

## 4. Project structure

```
ShopSync/
├── run.sh / run.bat
├── src/com/shopsync/
│   ├── Main.java                      wires everything together and starts the server
│   ├── auth/                          User, UserStore, PasswordHasher (PBKDF2), SessionManager
│   ├── model/                         Product, Offer, PricePoint, Policy, Order, OrderStatus,
│   │                                  TrackingEvent, ProductComparison, Alert, Category
│   ├── marketplace/                   Marketplace (interface) ← SimulatedMarketplace (abstract)
│   │                                  ← Amazon / Flipkart / Myntra / Croma
│   ├── service/
│   │   ├── PriceComparisonService     parallel search across stores (ExecutorService)
│   │   ├── ProductService             details, policies, AI descriptions, compare, recommendations, deals
│   │   ├── PriceInsight               price-history analysis and buying verdict
│   │   ├── ShoppingAssistant          the AI agent: intents, entities, grounded replies
│   │   ├── OrderTrackingService       per-user orders and stats
│   │   └── LiveUpdateEngine           background price/order updates (ScheduledExecutorService)
│   ├── ai/                            LlmClient (interface), GeminiClient (java.net.http)
│   ├── inbox/                         MessageParser (rules + optional AI), InboxService (create/update orders,
│   │                                  forwarding addresses, Gmail script), ImportedOrderStore, ParsedMessage
│   ├── server/WebServer.java          REST API, auth cookies, static files (JDK HttpServer)
│   ├── data/                          Catalog, PolicyBook, SampleOrders, TrackingMessages
│   └── util/                          Json (writer), JsonParser, JsonSerializable
└── web/
    ├── login.html, index.html
    ├── css/base.css, login.css, app.css
    └── js/login.js, icons.js (line icons), charts.js (hand-written SVG charts), app.js
```

### How the AI assistant works
```
 message ─▶ 1. understand: intent (recommend / compare / describe / policy / history / orders)
                           + entities (products, budget "under 2k", category, "it" = last product)
         ─▶ 2. fetch REAL data from the services (prices, history, policies, orders)
         ─▶ 3. reply: built-in templates, or Gemini phrases an answer from those facts only
         ─▶ 4. JSON with text + cards / comparison table / policy table / chart data for the UI
```
Grounding the model in fetched data is the same idea as retrieval-augmented generation (RAG):
the model never makes up a price, because it only sees ShopSync's numbers.

### REST API (all JSON; everything except auth needs a session cookie)

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/signup`, `/api/auth/login`, `/api/auth/logout` | accounts |
| GET | `/api/auth/me` | current user |
| GET | `/api/compare?q=&category=` | live search across stores |
| GET | `/api/products/{id}?days=90` | details, offers, policies, price insight |
| GET | `/api/products/{id}/ai-summary` | AI-written description |
| GET | `/api/compare-products?ids=P01,P02` | side-by-side comparison |
| GET | `/api/recommendations`, `/api/deals` | personalised picks, price drops |
| POST | `/api/assistant` `{"message": "..."}` | AI assistant |
| GET | `/api/orders`, `/api/orders/{id}`, `/api/summary`, `/api/alerts` | tracking |
| POST | `/api/demo/order` | place a random demo order |
| GET | `/api/inbox` | forwarding address, recent imports, sample messages |
| POST | `/api/inbox/preview`, `/api/inbox/import` | read a pasted message / add or update the order |
| POST | `/api/inbox/hook/{secret}` | **public** forwarding address (JSON or plain text), used by Gmail/phones |
| GET | `/api/inbox/gmail-script` | download the Gmail Apps Script with your address filled in |

---

## 5. Java concepts used (for the report / viva)

| Concept | Where |
|---|---|
| Interfaces | `Marketplace`, `LlmClient`, `JsonSerializable` |
| Abstract class, inheritance, Template Method | `SimulatedMarketplace` → 4 stores override `carries()`, `pricingFactor()`, `festiveSaleDiscount()`… |
| Polymorphism | services loop over `Collection<Marketplace>`; `LlmClient.NONE` vs `GeminiClient` |
| Encapsulation | `User` never exposes its hash/salt; `Offer` controls how prices change |
| Enums with behaviour | `OrderStatus.next()`, `PriceInsight.Verdict`, `ShoppingAssistant.Intent` |
| Fluent builder style | `new Product(...).spec(...).alias(...).goodFor(...)` |
| Collections & generics | `Map`, `TreeMap` (date-ordered history), `LinkedList`, `Deque`, `Set` |
| Comparators, lambdas, method refs | best-deal ordering, recommendation ranking |
| Multithreading | parallel search (`invokeAll`), scheduled updates, server thread pool |
| Thread safety | `synchronized`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, `ConcurrentLinkedDeque`, `AtomicLong`, `volatile` |
| Security | PBKDF2 hashing, salts, constant-time compare, HttpOnly/SameSite cookies, path-traversal guard, same error for wrong email/password |
| Regex | budget parsing ("under ₹30k"), email validation, reading order emails/SMS (order IDs, AWB numbers, amounts, dates, statuses) |
| Networking | `HttpServer` (server side), `java.net.http.HttpClient` (Gemini) |
| Parsing | hand-written recursive-descent `JsonParser` |
| Exception handling | custom `AuthException`; AI failures fall back gracefully |
| Date/Time API | 90-day history, sale windows, order ETAs |

---

## 6. Sample viva questions

- **How is the password stored?** As a PBKDF2 hash with a random salt and 120,000 iterations; the plain password is never stored, and login re-hashes and compares in constant time.
- **How does the server know who is logged in?** After login it creates a random 256-bit token, stores token → user id, and sends it as an HttpOnly cookie that the browser returns on every request.
- **Is the assistant "real AI"?** It's an AI agent pipeline: intent classification, entity extraction, tool use (calling the services) and response generation. Generation uses templates offline, or an LLM (Gemini) when a key is set, grounded only in fetched data.
- **How is the price verdict decided?** By comparing today's best price with the 90-day low and the 30-day average (`PriceInsight`).
- **Why simulate data?** Marketplaces don't offer open price/order APIs to students and scraping breaks their terms; the `Marketplace` interface means a real connector can be dropped in without changing anything else.

- **How do imported orders get updated?** Each new message is parsed; if its order number or tracking number
  matches an existing order (or same store and product while still in progress), the status moves forward
  instead of creating a duplicate. Older news (e.g. a late "shipped" after "delivered") is ignored.
- **How is the forwarding address secured?** It contains a random 144-bit secret tied to one user; anyone
  without it can't post to your account, and Reset invalidates the old one.

## 7. Future scope
Real connectors (affiliate APIs, courier tracking APIs, Gmail API with OAuth instead of a script), a database (PostgreSQL/MySQL via JDBC, e.g. Render Postgres)
for persistence, price-drop email alerts for a watchlist, and WebSockets instead of polling.
