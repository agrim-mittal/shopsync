// ShopSync web app: talks to the Java backend's REST API.
(() => {
  "use strict";

  // ======================================================================= state & helpers
  const S = {
    user: null,
    view: "discover",
    query: "",
    category: "All",
    sort: "savings",
    market: "All",
    statusFilter: "All",
    kinds: {},                   // productId -> kind, for icons
    compare: [],                 // [{id, name, kind}]
    shopData: [],
    prices: new Map(),           // key -> last price, used to flash changes
    orderStatus: new Map(),
    openOrders: new Set(),
    ordersSig: "",
    ordersRendered: false,
    seenAlertId: 0,
    alertsLoaded: false,
    unread: 0,
    modal: null,                 // { id, detail, range, hidden:Set }
    chatStarted: false,
    chatBusy: false,
  };
  const STORES = ["Amazon", "Flipkart", "Myntra", "Croma"];
  const VERDICT = { GREAT: "Great time to buy", GOOD: "Good price", FAIR: "Fair price", WAIT: "Wait for a drop" };

  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const inr = (n) => n == null ? "—" : "₹" + Math.round(n).toLocaleString("en-IN");
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const storeColor = (name) => `var(--${String(name).toLowerCase()})`;
  const icon = (name, size) => Icons.svg(name, size);
  const pIcon = (kindOrId, size = 22) => Icons.product(S.kinds[kindOrId] || kindOrId, size);
  const md = (text) => esc(text)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\n/g, "<br>");

  function timeAgo(iso) {
    const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (s < 45) return "just now";
    if (s < 3600) return Math.floor(s / 60) + " min ago";
    if (s < 86400) return Math.floor(s / 3600) + " h ago";
    return Math.floor(s / 86400) + " d ago";
  }
  const fmtDate = (iso) => new Date(iso.length <= 10 ? iso + "T00:00:00" : iso)
    .toLocaleDateString("en-IN", { day: "numeric", month: "short" });
  const fmtDateTime = (iso) => new Date(iso)
    .toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

  async function api(path, options = {}) {
    const res = await fetch(path, options);
    if (res.status === 401) { window.location.href = "/login.html"; throw new Error("signed out"); }
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || path + " failed");
    return data;
  }
  const post = (path, body) => api(path, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body || {}),
  });

  function toast(msg, iconName = "info") {
    const t = document.createElement("div");
    t.className = "toast";
    t.innerHTML = `${icon(iconName, 16)}<span>${esc(msg)}</span>`;
    $("#toastStack").appendChild(t);
    setTimeout(() => { t.classList.add("out"); setTimeout(() => t.remove(), 300); }, 3600);
    while ($("#toastStack").children.length > 3) $("#toastStack").firstChild.remove();
  }

  /** Animates a number from its previous value to the target. */
  function countUp(elm, to, prefix = "") {
    const from = Number(elm.dataset.current || 0);
    elm.dataset.current = to;
    const start = performance.now(), dur = 700;
    const step = (now) => {
      const p = Math.min(1, (now - start) / dur);
      const eased = 1 - Math.pow(1 - p, 3);
      elm.textContent = prefix + Math.round(from + (to - from) * eased).toLocaleString("en-IN");
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  /** Changes a price element's text and briefly colours it if it moved. */
  function setPrice(elm, key, value) {
    if (!elm) return;
    const prev = S.prices.get(key);
    elm.textContent = inr(value);
    if (prev !== undefined && prev !== value) {
      elm.classList.remove("flash-down", "flash-up");
      void elm.offsetWidth;
      elm.classList.add(value < prev ? "flash-down" : "flash-up");
    }
    S.prices.set(key, value);
  }

  // ======================================================================= motion helpers
  /** Slides the sidebar's highlight pill to the active item. */
  function moveGlider() {
    const active = $(".side-nav .nav-item.active");
    const glider = $(".nav-glider");
    if (!active || !glider) return;
    glider.style.transform = `translateY(${active.offsetTop}px)`;
    glider.style.height = active.offsetHeight + "px";
    glider.classList.add("ready");
  }

  /** Slides each segmented control's background to its active button. */
  function syncSegs() {
    $$(".seg").forEach((seg) => {
      const a = $("button.active", seg);
      if (!a || !a.offsetWidth) return;
      seg.style.setProperty("--x", a.offsetLeft + "px");
      seg.style.setProperty("--w", a.offsetWidth + "px");
      seg.classList.add("ready");
    });
  }
  document.addEventListener("click", (e) => { if (e.target.closest(".seg")) requestAnimationFrame(syncSegs); });

  // fade sections in as they scroll into view
  const revealer = new IntersectionObserver((entries) => {
    entries.forEach((en) => { if (en.isIntersecting) { en.target.classList.add("in"); revealer.unobserve(en.target); } });
  }, { threshold: 0.08 });
  const observeReveals = () => $$(".reveal:not(.in)").forEach((n) => revealer.observe(n));

  // soft spotlight that follows the mouse across cards
  document.addEventListener("mousemove", (e) => {
    const card = e.target.closest && e.target.closest(".card.hoverable");
    if (!card) return;
    const r = card.getBoundingClientRect();
    card.style.setProperty("--mx", (e.clientX - r.left) + "px");
    card.style.setProperty("--my", (e.clientY - r.top) + "px");
  });

  // ======================================================================= theme
  const themeBtn = $("#themeToggle");
  const syncTheme = () => { themeBtn.innerHTML = icon(document.documentElement.dataset.theme === "dark" ? "sun" : "moon", 17); };
  themeBtn.addEventListener("click", () => {
    const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem("ss-theme", next); } catch (e) { /* ignore */ }
    syncTheme();
    if (S.modal) drawHistory();
  });

  // ======================================================================= navigation
  function setView(view) {
    if (!view) return;
    S.view = view;
    $$(".nav-item[data-view]").forEach((b) => b.classList.toggle("active", b.dataset.view === view));
    $$(".view").forEach((v) => v.classList.toggle("active", v.id === "view-" + view));
    window.scrollTo({ top: 0 });
    renderTray();
    moveGlider();
    requestAnimationFrame(() => { syncSegs(); observeReveals(); });
    if (view === "discover") loadDiscover();
    if (view === "shop") loadShop(true);
    if (view === "orders") { S.ordersSig = ""; S.ordersRendered = false; loadOrders(); }
  }
  $$(".nav-item[data-view]").forEach((b) => b.addEventListener("click", () => setView(b.dataset.view)));

  // ======================================================================= overview
  const PROMPTS = ["Best earbuds under ₹5,000", "Compare iPhone 15 vs Galaxy S24",
    "Return policy for Nike Air Max", "Should I buy the Sony XM5 now?", "Gift ideas under ₹2,000"];

  function setupOverview() {
    const h = new Date().getHours();
    const part = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
    $("#greeting").textContent = `${part}, ${S.user.firstName}`;
    $("#heroDate").textContent = new Date().toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" });
    $("#heroChips").innerHTML = PROMPTS.slice(0, 4).map((p) => `<button class="qchip" data-ask="${esc(p)}">${esc(p)}</button>`).join("");
    $("#heroAskInput").placeholder = "Ask the assistant, e.g. “" + PROMPTS[0] + "”";
    $$(".m-value[data-count]").forEach((elm) => countUp(elm, Number(elm.dataset.count)));
  }

  $("#heroAsk").addEventListener("submit", (e) => {
    e.preventDefault();
    const q = $("#heroAskInput").value.trim();
    if (!q) return;
    $("#heroAskInput").value = "";
    openChat(q);
  });
  document.addEventListener("click", (e) => {
    const chip = e.target.closest("[data-ask]");
    if (chip) openChat(chip.dataset.ask);
    const opener = e.target.closest("[data-open-chat]");
    if (opener) openChat(opener.dataset.prompt);
  });

  function recCard(r, i) {
    const p = r.product;
    return `
      <article class="card hoverable rec-card" data-open="${p.id}" style="--i:${i}">
        <div class="rec-top">
          <div class="p-icon">${pIcon(p.kind)}</div>
          <span class="verdict v-${r.verdict}">${VERDICT[r.verdict]}</span>
        </div>
        <div>
          <h3>${esc(p.shortName)}</h3>
          <div class="sub">${esc(p.brand)} · ${r.rating.toFixed(1)} rating · ${r.stores} stores</div>
        </div>
        <div class="reasons">${r.reasons.map((x) => `<span class="reason">${esc(x)}</span>`).join("")}</div>
        <div class="rec-price">
          <span class="price">${inr(r.bestPrice)}</span>
          <span class="store-name s-${r.bestMarketplace} muted" style="font-size:12.5px"><span class="dot-store"></span>${r.bestMarketplace}</span>
        </div>
      </article>`;
  }

  function dealRow(d, i) {
    const p = d.product;
    return `
      <div class="deal-row" data-open="${p.id}" style="--i:${i}">
        <div class="deal-prod"><div class="p-icon sm">${pIcon(p.kind, 18)}</div>${esc(p.shortName)}</div>
        <div class="hide-md"><span class="store-name s-${d.bestMarketplace}"><span class="dot-store"></span>${d.bestMarketplace}</span></div>
        <div class="num price price-col" data-price="deal|${p.id}">${inr(d.bestPrice)}</div>
        <div class="num muted hide-md">${inr(d.average30)}</div>
        <div class="num drop">${d.belowAverage > 0 ? "−" + d.belowAverage.toFixed(1) + "%" : "0%"}</div>
      </div>`;
  }

  async function loadDiscover(refresh = false) {
    try {
      const [recs, deals, summary] = await Promise.all([
        refresh ? null : api("/api/recommendations?limit=8"),
        api("/api/deals?limit=6"),
        api("/api/summary"),
      ]);
      if (recs) {
        $("#recRow").innerHTML = recs.slice(0, 8).map(recCard).join("");
        $("#recRow").classList.add("stagger");
      }
      if (!refresh) {
        $("#dealGrid").innerHTML = `<div class="deal-row head"><div>Product</div><div class="hide-md">Lowest at</div><div class="num">Price</div><div class="num hide-md">30-day avg</div><div class="num">vs. average</div></div>`
          + deals.map(dealRow).join("");
        deals.forEach((d) => S.prices.set("deal|" + d.product.id, d.bestPrice));
      } else {
        deals.forEach((d) => setPrice($(`[data-price="deal|${d.product.id}"]`), "deal|" + d.product.id, d.bestPrice));
      }
      const savings = Math.round(deals.reduce((sum, d) => sum + Math.max(0, d.average30 - d.bestPrice), 0));
      if (Number($("#heroSavings").dataset.current ?? -1) !== savings) countUp($("#heroSavings"), savings, "₹");
      if (Number($("#heroActive").dataset.current ?? -1) !== summary.active) countUp($("#heroActive"), summary.active);
      $("#ordersBadge").textContent = summary.active || "";
    } catch (err) { console.error(err); }
  }

  // ======================================================================= compare prices
  let searchTimer;
  $("#globalSearch").addEventListener("input", (e) => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      S.query = e.target.value.trim();
      if (S.view !== "shop") setView("shop"); else loadShop(true);
    }, 250);
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "/" && !/input|textarea/i.test(document.activeElement.tagName)) {
      e.preventDefault();
      $("#globalSearch").focus();
    }
    if (e.key === "Escape") { closeModal("productModal"); closeModal("compareModal"); closeModal("importModal"); closeChat(); closeAlerts(); }
  });
  $("#categoryChips").addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    $$("#categoryChips button").forEach((c) => c.classList.toggle("active", c === b));
    S.category = b.dataset.cat;
    loadShop(true);
  });
  $("#sortSelect").addEventListener("change", (e) => { S.sort = e.target.value; renderShop(true); });

  const avgRating = (offers) => offers.reduce((s, o) => s + o.rating, 0) / Math.max(1, offers.length);

  function sortShop(list) {
    const by = {
      savings: (a, b) => b.savings - a.savings,
      "price-asc": (a, b) => a.bestPrice - b.bestPrice,
      "price-desc": (a, b) => b.bestPrice - a.bestPrice,
      rating: (a, b) => avgRating(b.offers) - avgRating(a.offers),
    }[S.sort];
    return list.slice().sort(by);
  }

  const cmpBox = (on) => `<span class="box">${on ? icon("check", 10) : ""}</span>`;

  function productCard(c, i) {
    const p = c.product;
    const inCompare = S.compare.some((x) => x.id === p.id);
    const bars = STORES.map((store) => {
      if (!c.offers.some((x) => x.marketplace === store)) return "";
      return `<div class="sbar s-${store}" data-store="${store}">
          <span class="store-name"><span class="dot-store"></span>${store}</span>
          <div class="track"><div class="fill"></div></div>
          <span class="val"></span>
        </div>`;
    }).join("");
    return `
      <article class="card hoverable p-card" data-pid="${p.id}" data-open="${p.id}" style="--i:${i}">
        <div class="p-head">
          <div class="p-icon">${pIcon(p.kind)}</div>
          <div>
            <h3>${esc(p.name)}</h3>
            <div class="sub">${esc(p.brand)} · ${esc(p.category)}</div>
          </div>
        </div>
        <div class="p-best">
          <span class="price" data-role="best">${inr(c.bestPrice)}</span>
          <span class="on">at <span data-role="bestStore">${c.bestMarketplace}</span></span>
          <span class="off" data-role="save"></span>
        </div>
        <div class="store-bars">${bars}</div>
        <div class="p-foot">
          <span class="rating">${avgRating(c.offers).toFixed(1)}</span>
          <button class="cmp-toggle ${inCompare ? "on" : ""}" data-compare="${p.id}">${cmpBox(inCompare)}Compare</button>
        </div>
      </article>`;
  }

  /** Updates prices, bars and best-store highlight inside an existing card (no re-render). */
  function fillCard(card, c) {
    if (!card) return;
    const inStock = c.offers.filter((o) => o.inStock);
    const min = Math.min(...inStock.map((o) => o.totalCost));
    setPrice($("[data-role=best]", card), "best|" + c.product.id, c.bestPrice);
    $("[data-role=bestStore]", card).textContent = c.bestMarketplace;
    $("[data-role=save]", card).textContent = c.savings > 0 ? "Save " + inr(c.savings) : "";
    c.offers.forEach((o) => {
      const bar = $(`.sbar[data-store="${o.marketplace}"]`, card);
      if (!bar) return;
      bar.classList.toggle("best", o.inStock && o.totalCost === min);
      bar.classList.toggle("oos", !o.inStock);
      $(".fill", bar).style.width = o.inStock ? Math.round(min / o.totalCost * 100) + "%" : "0%";
      if (o.inStock) setPrice($(".val", bar), "bar|" + c.product.id + "|" + o.marketplace, o.totalCost);
      else $(".val", bar).textContent = "Out of stock";
    });
  }

  function renderShop(full) {
    const grid = $("#productGrid");
    const data = sortShop(S.shopData);
    if (!data.length) {
      grid.innerHTML = `<div class="empty">No products match “${esc(S.query)}”.</div>`;
      return;
    }
    if (full) {
      grid.classList.add("stagger");
      grid.innerHTML = data.map(productCard).join("");
      requestAnimationFrame(() => data.forEach((c) => fillCard($(`[data-pid="${c.product.id}"]`, grid), c)));
    } else {
      data.forEach((c) => fillCard($(`[data-pid="${c.product.id}"]`, grid), c));
    }
  }

  async function loadShop(full) {
    if (full) $("#productGrid").innerHTML = Array.from({ length: 6 }, () => '<div class="skeleton"></div>').join("");
    const params = new URLSearchParams({ q: S.query, category: S.category });
    const t0 = performance.now();
    try {
      S.shopData = await api("/api/compare?" + params);
      const ms = Math.round(performance.now() - t0);
      const listings = S.shopData.reduce((n, c) => n + c.offers.length, 0);
      $("#resultMeta").textContent = `${S.shopData.length} products · ${listings} listings from 4 stores · fetched in parallel in ${ms} ms`;
      renderShop(full);
    } catch (err) { console.error(err); }
  }

  // ---- compare tray
  document.addEventListener("click", (e) => {
    const toggle = e.target.closest("[data-compare]");
    if (toggle) {
      e.stopPropagation();
      const id = toggle.dataset.compare;
      const found = S.shopData.find((c) => c.product.id === id);
      const product = found ? found.product : (S.modal && S.modal.detail.product.id === id ? S.modal.detail.product : null);
      toggleCompare(id, product);
      return;
    }
    const opener = e.target.closest("[data-open]");
    if (opener && !e.target.closest("button:not([data-open])")) openProduct(opener.dataset.open);
  });

  function toggleCompare(id, product) {
    const idx = S.compare.findIndex((x) => x.id === id);
    if (idx >= 0) S.compare.splice(idx, 1);
    else {
      if (S.compare.length >= 3) { toast("You can compare up to 3 products at once"); return; }
      S.compare.push({ id, name: product ? product.shortName || product.name : id, kind: product ? product.kind : "" });
    }
    renderTray();
  }

  function renderTray() {
    $("#trayItems").innerHTML = S.compare.map((x) =>
      `<span class="tray-item"><span class="n">${esc(x.name)}</span><button data-untray="${x.id}" aria-label="Remove">${icon("close", 12)}</button></span>`).join("");
    $("#compareTray").classList.toggle("show", S.compare.length > 0 && S.view === "shop");
    $("#trayCompare").textContent = S.compare.length < 2 ? "Select one more" : `Compare ${S.compare.length}`;
    $("#trayCompare").disabled = S.compare.length < 2;
    $$(".cmp-toggle").forEach((b) => {
      const on = S.compare.some((x) => x.id === b.dataset.compare);
      b.classList.toggle("on", on);
      $(".box", b).innerHTML = on ? icon("check", 10) : "";
    });
  }
  $("#trayItems").addEventListener("click", (e) => {
    const b = e.target.closest("[data-untray]");
    if (b) toggleCompare(b.dataset.untray);
  });
  $("#trayClear").addEventListener("click", () => { S.compare = []; renderTray(); });
  $("#trayCompare").addEventListener("click", () => openCompare(S.compare.map((x) => x.id)));

  async function openCompare(ids) {
    openModal("compareModal");
    $("#compareModalBody").innerHTML = '<div class="skeleton" style="min-height:420px"></div>';
    try {
      const data = await api("/api/compare-products?ids=" + ids.join(","));
      $("#compareModalBody").innerHTML = compareTable(data, false);
    } catch (err) {
      $("#compareModalBody").innerHTML = `<div class="empty">${esc(err.message)}</div>`;
    }
  }

  function compareTable(data, mini) {
    const head = data.products.map((c, i) => {
      const p = c.product;
      return mini ? `<th>${esc(p.shortName)}</th>` : `
        <th><div class="cmp-prod">
          <div class="p-icon">${pIcon(p.kind)}</div>
          <strong>${esc(p.name)}</strong>
          <span class="verdict v-${c.verdict}" style="width:max-content">${VERDICT[c.verdict]}</span>
          ${i === data.bestValueIndex ? '<span class="bv-badge">Best overall value</span>' : ""}
          <button class="link-btn" style="text-align:left" data-open="${p.id}">View details</button>
        </div></th>`;
    }).join("");
    const rowHtml = (r) => `<tr><th>${esc(r.label)}</th>${r.values.map((v, i) =>
      `<td class="${i === r.winner && data.products.length > 1 ? "win" : ""}">${esc(r.label === "Price verdict" ? VERDICT[v] || v : v)}</td>`).join("")}</tr>`;
    if (mini) {
      return `<div class="mini-table-wrap"><table class="mini-table"><thead><tr><th></th>${head}</tr></thead>
        <tbody>${data.rows.filter((r) => r.label !== "90-day low").map(rowHtml).join("")}</tbody></table></div>`;
    }
    return `
      <h2 class="cmp-title">Product comparison</h2>
      <p class="muted" style="margin:0">Best price, ratings, delivery, return policy and specifications across all stores.</p>
      <div class="cmp-verdict">${icon("spark", 16)}<div><strong>Summary.</strong> ${esc(data.verdict)}</div></div>
      <div class="cmp-table-wrap"><table class="cmp-table">
        <thead><tr><th></th>${head}</tr></thead>
        <tbody>${data.rows.map(rowHtml).join("")}<tr class="cmp-group"><th colspan="${data.products.length + 1}">Specifications</th></tr>${data.specRows.map(rowHtml).join("")}</tbody>
      </table></div>`;
  }

  // ======================================================================= product modal
  function openModal(id) {
    const m = $("#" + id);
    m.classList.add("open");
    m.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
    document.body.classList.add("modal-open");
  }
  function closeModal(id) {
    const m = $("#" + id);
    if (!m.classList.contains("open")) return;
    m.classList.remove("open");
    m.setAttribute("aria-hidden", "true");
    if (!$$(".modal.open").length) { document.body.style.overflow = ""; document.body.classList.remove("modal-open"); }
    if (id === "productModal") S.modal = null;
  }
  $$(".modal").forEach((m) => m.addEventListener("click", (e) => { if (e.target.closest("[data-close]")) closeModal(m.id); }));

  async function openProduct(id) {
    if (window.innerWidth < 900) closeChat();
    openModal("productModal");
    const body = $("#productModalBody");
    body.innerHTML = '<div class="skeleton" style="min-height:120px;margin-bottom:14px"></div><div class="skeleton" style="min-height:340px"></div>';
    try {
      const detail = await api(`/api/products/${id}?days=90`);
      S.modal = { id, detail, range: 90, hidden: new Set() };
      renderProduct(detail);
    } catch (err) {
      body.innerHTML = `<div class="empty">${esc(err.message)}</div>`;
    }
  }

  function offerRows(detail) {
    const min = Math.min(...detail.offers.filter((o) => o.inStock).map((o) => o.totalCost));
    return detail.offers.map((o) => {
      const best = o.inStock && o.totalCost === min;
      return `
      <div class="offer ${o.inStock ? "" : "oos"}">
        <div><span class="store-name s-${o.marketplace}"><span class="dot-store"></span>${o.marketplace}</span>${best ? '<span class="best-tag">Lowest price</span>' : ""}</div>
        <div class="o-meta">
          <span>${o.rating.toFixed(1)} rating (${o.reviewCount.toLocaleString("en-IN")})</span>
          <span>${o.inStock ? "Delivery in " + o.deliveryDays + " day" + (o.deliveryDays > 1 ? "s" : "") : "Out of stock"}</span>
          <span>${o.freeDelivery ? "Free delivery" : "+₹40 delivery"}</span>
          <span>Sold by ${esc(o.seller)}</span>
        </div>
        <div class="o-price">
          <span class="price" data-offer="${o.marketplace}">${o.trend === "DOWN" ? '<span class="trend-DOWN">▼</span> ' : o.trend === "UP" ? '<span class="trend-UP">▲</span> ' : ""}${inr(o.totalCost)}</span>
          <small class="off">${o.discountPercent > 0 ? o.discountPercent + "% off MRP" : ""}</small>
        </div>
      </div>`;
    }).join("");
  }

  function policyTable(policies, best, mini) {
    if (!policies.length) return "";
    const cls = (p) => (p.marketplace === best ? "best-col" : "");
    const yesNo = (v) => v ? '<span class="yes">Yes</span>' : '<span class="no">No</span>';
    const rows = [
      ["Return window", (p) => `${p.returnDays} days`],
      ["Return type", (p) => esc(p.returnType)],
      ["Refund available", (p) => yesNo(p.refundAllowed)],
      ["Cash on delivery", (p) => yesNo(p.cashOnDelivery)],
    ];
    if (!mini) rows.push(
      ["Exchange offer", (p) => yesNo(p.exchangeOffer)],
      ["No-cost EMI", (p) => yesNo(p.noCostEmi)],
      ["Warranty", (p) => esc(p.warranty)],
      ["Notes", (p) => p.highlights.length ? "<ul>" + p.highlights.map((h) => `<li>${esc(h)}</li>`).join("") + "</ul>" : "—"]);
    const head = policies.map((p) => `<th class="${cls(p)}"><span class="store-name s-${p.marketplace}"><span class="dot-store"></span>${p.marketplace}</span>${p.marketplace === best ? '<span class="flex-badge">Most flexible</span>' : ""}</th>`).join("");
    const body = rows.map(([label, f]) => `<tr><th>${label}</th>${policies.map((p) => `<td class="${cls(p)}">${f(p)}</td>`).join("")}</tr>`).join("");
    const table = `<table class="${mini ? "mini-table" : "policy-table"}"><thead><tr><th></th>${head}</tr></thead><tbody>${body}</tbody></table>`;
    return mini ? `<div class="mini-table-wrap">${table}</div>` : `<div style="overflow-x:auto">${table}</div>`;
  }

  function renderProduct(d) {
    const p = d.product;
    const inCompare = S.compare.some((x) => x.id === p.id);
    const ins = d.insight;
    $("#productModalBody").innerHTML = `
      <div class="pm-head">
        <div class="p-icon lg">${pIcon(p.kind, 30)}</div>
        <div>
          <h2 id="pmTitle">${esc(p.name)}</h2>
          <div class="pm-tags">
            <span>${esc(p.brand)}</span><span class="sep">·</span><span>${esc(p.category)}</span><span class="sep">·</span>
            <span>${d.rating.toFixed(1)} average rating</span>
            <span class="verdict v-${ins.verdict}">${VERDICT[ins.verdict]}</span>
          </div>
        </div>
      </div>
      <div class="pm-grid">
        <div>
          <div class="panel">
            <h3>Summary <span class="ai-label">${icon("spark", 11)}<span id="aiBy">AI generated</span></span></h3>
            <div class="ai-text" id="aiText"></div>
            <div class="ai-foot">
              <button class="cmp-toggle ${inCompare ? "on" : ""}" data-compare="${p.id}">${cmpBox(inCompare)}Add to compare</button>
              <button class="btn-ghost" data-open-chat data-prompt="Should I buy the ${esc(p.shortName)} now?">Ask the assistant</button>
            </div>
          </div>
          <div class="specs">${p.specs.map((s) => `<div class="spec"><small>${esc(s.label)}</small><span>${esc(s.value)}</span></div>`).join("")}</div>
        </div>
        <div class="panel">
          <h3>Prices <span class="sub">MRP ${inr(p.mrp)} · updated live</span></h3>
          <div class="offer-list" id="offerList">${offerRows(d)}</div>
        </div>

        <div class="panel full">
          <h3>Price history
            <div class="range-tabs" id="rangeTabs">
              <button data-range="30">30D</button><button data-range="60">60D</button><button class="active" data-range="90">90D</button>
            </div>
          </h3>
          <div class="legend" id="chartLegend"></div>
          <div id="historyChart"></div>
          <div class="history-stats">
            <div class="hs"><small>Current best</small><strong>${inr(ins.current)}</strong></div>
            <div class="hs"><small>90-day low · ${ins.lowestStore}, ${fmtDate(ins.lowestDate)}</small><strong>${inr(ins.lowest)}</strong></div>
            <div class="hs"><small>30-day average</small><strong>${inr(ins.average30)}</strong></div>
            <div class="hs"><small>90-day high</small><strong>${inr(ins.highest)}</strong></div>
          </div>
          <div class="verdict-line v-${ins.verdict}">${icon("info", 16)}<span>${esc(ins.verdictText)}</span></div>
        </div>

        <div class="panel full">
          <h3>Store policies <span class="sub">Returns, payment and warranty</span></h3>
          ${policyTable(d.policies, d.mostFlexibleStore, false)}
          <p class="disclaimer">${icon("info", 14)}${esc(d.policyDisclaimer)}</p>
        </div>
      </div>`;

    $("#rangeTabs").addEventListener("click", (e) => {
      const b = e.target.closest("[data-range]");
      if (!b) return;
      $$("#rangeTabs button").forEach((x) => x.classList.toggle("active", x === b));
      S.modal.range = Number(b.dataset.range);
      drawHistory();
    });
    $("#chartLegend").addEventListener("click", (e) => {
      const b = e.target.closest("[data-series]");
      if (!b) return;
      const name = b.dataset.series;
      if (S.modal.hidden.has(name)) S.modal.hidden.delete(name); else S.modal.hidden.add(name);
      drawHistory();
    });
    d.offers.forEach((o) => S.prices.set("modal|" + o.marketplace, o.totalCost));
    drawHistory();

    if (d.aiAvailable) {
      $("#aiText").innerHTML = '<span class="muted">Generating summary…</span>';
      api(`/api/products/${p.id}/ai-summary`).then((r) => {
        if (!S.modal || S.modal.id !== p.id) return;
        $("#aiBy").textContent = r.poweredBy;
        typewrite($("#aiText"), r.text);
      }).catch(() => typewrite($("#aiText"), d.description));
    } else {
      typewrite($("#aiText"), d.description);
    }
  }

  function saleBand(points, ins) {
    if (!points.length) return null;
    const last = new Date(points[points.length - 1].date + "T00:00:00");
    // build YYYY-MM-DD from local date parts (toISOString would shift to UTC)
    const local = (daysAgo) => {
      const t = new Date(last);
      t.setDate(t.getDate() - daysAgo);
      return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}-${String(t.getDate()).padStart(2, "0")}`;
    };
    return { from: local(ins.saleFromDaysAgo), to: local(ins.saleToDaysAgo), label: "Festive sale" };
  }

  function drawHistory() {
    const m = S.modal;
    if (!m || !$("#historyChart")) return;
    const ins = m.detail.insight;
    const cut = (pts) => pts.slice(-m.range);
    const all = [{ name: "Lowest price", color: "var(--text)", points: cut(ins.best), width: 2.2, area: true }]
      .concat(Object.entries(ins.series).map(([store, pts]) => ({ name: store, color: storeColor(store), points: cut(pts), width: 1.3 })));
    $("#chartLegend").innerHTML = all.map((s) =>
      `<button data-series="${s.name}" class="${m.hidden.has(s.name) ? "off" : ""}"><span class="swatch" style="--c:${s.color}"></span>${s.name}</button>`).join("");
    const visible = all.filter((s) => !m.hidden.has(s.name));
    ShopCharts.lineChart($("#historyChart"), {
      series: visible.length ? visible : all.slice(0, 1),
      band: saleBand(cut(ins.best), ins),
      height: 260,
    });
  }

  /** Streams text into an element word by word. */
  function typewrite(elm, text, speed = 14) {
    if (!elm) return;
    const words = String(text).split(/(\s+)/);
    let i = 0;
    clearInterval(elm._typer);
    elm.innerHTML = '<span class="caret"></span>';
    elm._typer = setInterval(() => {
      i += 3;
      elm.innerHTML = md(words.slice(0, i).join("")) + (i < words.length ? '<span class="caret"></span>' : "");
      if (i >= words.length) clearInterval(elm._typer);
    }, speed);
  }

  async function refreshModalPrices() {
    const m = S.modal;
    if (!m) return;
    try {
      const d = await api(`/api/products/${m.id}?days=90`);
      if (!S.modal || S.modal.id !== m.id) return;
      S.modal.detail.offers = d.offers;
      const list = $("#offerList");
      if (!list) return;
      list.innerHTML = offerRows(d);
      d.offers.forEach((o) => {
        const key = "modal|" + o.marketplace;
        const prev = S.prices.get(key);
        if (prev !== undefined && prev !== o.totalCost) {
          $(`[data-offer="${o.marketplace}"]`, list).classList.add(o.totalCost < prev ? "flash-down" : "flash-up");
        }
        S.prices.set(key, o.totalCost);
      });
    } catch (err) { console.error(err); }
  }

  // ======================================================================= orders
  const STEPS = ["PLACED", "PACKED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED"];
  const STEP_LABELS = ["Placed", "Packed", "Shipped", "Out", "Delivered"];

  function segHandler(containerId, key, attr) {
    $(containerId).addEventListener("click", (e) => {
      const b = e.target.closest("button");
      if (!b) return;
      $$(containerId + " button").forEach((x) => x.classList.toggle("active", x === b));
      S[key] = b.dataset[attr];
      S.ordersSig = ""; S.ordersRendered = false;
      loadOrders();
    });
  }
  segHandler("#marketChips", "market", "market");
  segHandler("#statusSeg", "statusFilter", "filter");

  $("#orderList").addEventListener("click", (e) => {
    const main = e.target.closest(".order-main");
    if (!main) return;
    const card = main.closest(".order");
    const id = card.dataset.id;
    if (S.openOrders.has(id)) S.openOrders.delete(id); else S.openOrders.add(id);
    card.classList.toggle("open");
  });
  $("#demoOrderBtn").addEventListener("click", async () => {
    try {
      const o = await post("/api/demo/order");
      toast(`New ${o.marketplace} order: ${o.productName}`, "box");
      loadOrders();
    } catch (err) { toast("Couldn't place a demo order"); }
  });

  function orderCard(o, i) {
    const prev = S.orderStatus.get(o.orderId);
    const changed = prev !== undefined && prev !== o.status;
    S.orderStatus.set(o.orderId, o.status);
    const cancelled = o.status === "CANCELLED";
    const reached = cancelled ? 1 : STEPS.indexOf(o.status) + 1;
    const eta = o.status === "DELIVERED" ? "Delivered" : cancelled ? "Cancelled"
      : o.expectedDelivery ? "Arriving " + fmtDate(o.expectedDelivery) : "Date not shared yet";
    const timeline = o.events.slice().reverse().map((e) => `
      <div class="tl-item"><div class="t">${esc(e.statusLabel)}</div>
      <div class="m">${esc(e.message)} · ${esc(e.location)} · ${fmtDateTime(e.time)}</div></div>`).join("");
    return `
      <article class="order ${S.openOrders.has(o.orderId) ? "open" : ""} ${changed ? "updated" : ""}" data-id="${esc(o.orderId)}" style="--i:${i}">
        <div class="order-main">
          <div class="p-icon sm">${pIcon(o.productId, 18)}</div>
          <div>
            <h3>${esc(o.productName)}${o.quantity > 1 ? ` × ${o.quantity}` : ""}</h3>
            <div class="order-meta">
              <span class="store-name s-${o.marketplace}"><span class="dot-store"></span>${o.marketplace}</span>
              ${o.imported ? `<span class="src-tag">${icon(o.source === "SMS" ? "chat" : "mail", 11)}via ${esc(o.source === "Pasted" ? "import" : o.source)}</span>` : ""}
              <span>#${esc(o.orderId)}</span>${o.courier ? `<span>${esc(o.courier)}</span>` : ""}${o.trackingNumber ? `<span>AWB ${esc(o.trackingNumber)}</span>` : ""}<span>Updated ${timeAgo(o.lastUpdate)}</span>
            </div>
          </div>
          <div class="progress-col">
            <span class="status-pill st-${o.status}">${esc(o.statusLabel)}</span>
            <div class="stepper ${cancelled ? "cancel" : ""}">${STEPS.map((_, k) => `<span class="${k < reached ? "on" : ""}"></span>`).join("")}</div>
            <div class="step-labels">${cancelled ? "<span>Order cancelled</span>" : STEP_LABELS.map((l) => `<span>${l}</span>`).join("")}</div>
          </div>
          <div class="amount">${o.amount > 0 ? inr(o.amount) : "—"}<small>${eta}</small></div>
        </div>
        <div class="timeline"><div><div class="tl-inner">${timeline}</div></div></div>
      </article>`;
  }

  async function loadOrders() {
    try {
      const params = new URLSearchParams({ marketplace: S.market, filter: S.statusFilter });
      const [orders, s] = await Promise.all([api("/api/orders?" + params), api("/api/summary")]);
      $("#ordersBadge").textContent = s.active || "";
      const stats = [["Total orders", s.total, ""], ["In progress", s.active, ""], ["Out for delivery", s.outForDelivery, ""], ["Total spent", Math.round(s.totalSpent), "₹"]];
      if (!$("#orderStats").children.length) {
        $("#orderStats").innerHTML = stats.map(([l], i) => `<div class="metric"><span class="m-label">${l}</span><span class="m-value" data-stat="${i}">0</span></div>`).join("");
      }
      stats.forEach(([, v, prefix], i) => {
        const elm = $(`[data-stat="${i}"]`);
        if (Number(elm.dataset.current ?? -1) !== v) countUp(elm, v, prefix);
      });

      const sig = JSON.stringify(orders.map((o) => [o.orderId, o.status, o.lastUpdate]));
      if (sig === S.ordersSig) return;
      S.ordersSig = sig;
      const list = $("#orderList");
      list.classList.toggle("stagger", !S.ordersRendered);
      list.classList.toggle("no-anim", S.ordersRendered);
      list.innerHTML = orders.length ? orders.map(orderCard).join("") : `<div class="empty">No orders here yet.</div>`;
      S.ordersRendered = true;
    } catch (err) { console.error(err); }
  }

  // ======================================================================= import from email / SMS
  const IMP = { source: "Email", samples: [], hookUrl: "" };

  function setImportTab(tab) {
    $$("#importTabs button").forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
    $$("#importModal .imp-pane").forEach((p) => { p.hidden = p.dataset.pane !== tab; });
    requestAnimationFrame(syncSegs);
    if (tab === "log" || tab === "auto") loadInbox();
  }

  async function loadInbox() {
    try {
      const d = await api("/api/inbox");
      IMP.samples = d.samples;
      IMP.hookUrl = d.hookUrl;
      $("#impSamples").innerHTML = d.samples.map((x, i) => `<button class="qchip" data-sample="${i}">${esc(x.label)}</button>`).join("");
      $("#hookUrl").textContent = d.hookUrl;
      $("#curlExample").textContent = `curl -X POST -H "Content-Type: application/json" \\\n  -d '{"source":"SMS","body":"Your Nykaa order NYK555123 has been shipped via Delhivery. AWB 1234567890"}' \\\n  ${d.hookUrl}`;
      $("#impActivity").innerHTML = d.activity.length ? d.activity.map((a, i) => `
        <div class="act-row" style="animation-delay:${Math.min(i, 8) * 30}ms">
          <span class="src">${esc(a.source)}<br>${timeAgo(a.time)}</span>
          <div class="txt"><div>${esc(a.preview || "(empty)")}</div><small>${esc(a.reason || "")}</small></div>
          <span class="action-pill act-${a.action}">${esc(a.action)}</span>
        </div>`).join("") : `<div class="empty">Imported and forwarded messages will be listed here.</div>`;
    } catch (err) { console.error(err); }
  }

  function openImport() {
    openModal("importModal");
    setImportTab("paste");
    loadInbox();
    setTimeout(() => $("#impBody").focus(), 200);
  }
  $("#importBtn").addEventListener("click", openImport);
  $("#importTabs").addEventListener("click", (e) => { const b = e.target.closest("button"); if (b) setImportTab(b.dataset.tab); });
  $("#impSource").addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    $$("#impSource button").forEach((x) => x.classList.toggle("active", x === b));
    IMP.source = b.dataset.source;
  });
  $("#impSamples").addEventListener("click", (e) => {
    const b = e.target.closest("[data-sample]");
    if (!b) return;
    const x = IMP.samples[Number(b.dataset.sample)];
    $("#impFrom").value = x.from;
    $("#impSubject").value = x.subject;
    $("#impBody").value = x.body;
    $$("#impSource button").forEach((btn) => btn.classList.toggle("active", btn.dataset.source === (x.source === "SMS" ? "SMS" : "Email")));
    IMP.source = x.source === "SMS" ? "SMS" : "Email";
    syncSegs();
    previewImport();
  });

  const impPayload = () => ({ from: $("#impFrom").value.trim(), subject: $("#impSubject").value.trim(),
    body: $("#impBody").value.trim(), source: IMP.source });

  function renderParsed(p, action, note, done) {
    const label = { create: "Will add a new order", update: "Will update an existing order", ignore: "Not an order message",
      created: "Added to your orders", updated: "Order updated", unchanged: "Already up to date", ignored: "Ignored" }[action] || action;
    const row = (k, v) => `<div>${k}</div><div>${v == null || v === "" ? '<span class="none">—</span>' : esc(v)}</div>`;
    $("#impResult").innerHTML = `
      <div class="res-head"><span data-icon="spark" data-size="16"></span><strong>What ShopSync found</strong>
        <span class="action-pill act-${action}">${label}</span></div>
      <div class="fields">
        ${row("Store", p.store)}${row("Order number", p.orderId)}${row("Product", p.product)}
        ${row("Amount", p.amount ? inr(p.amount) : null)}${row("Status", p.statusLabel)}${row("Courier", p.courier)}
        ${row("Tracking / AWB", p.trackingNumber)}${row("Expected delivery", p.expectedDelivery ? fmtDate(p.expectedDelivery) : null)}
      </div>
      <div class="conf"><span>Confidence</span><div class="conf-bar"><span style="width:0"></span></div><span>${p.confidence}%</span></div>
      <div class="muted" style="font-size:12px;margin-top:6px">Read by ${esc(p.parser === "rules" ? "ShopSync's built-in rules" : p.parser)}</div>
      ${note ? `<div class="res-note ${done ? "done" : ""}">${esc(note)}</div>` : ""}`;
    Icons.hydrate($("#impResult"));
    requestAnimationFrame(() => { $(".conf-bar span", $("#impResult")).style.width = p.confidence + "%"; });
  }

  async function previewImport() {
    const body = impPayload();
    if (!body.body && !body.subject) { toast("Paste a message first"); return; }
    try {
      const r = await post("/api/inbox/preview", body);
      const note = r.action === "ignore" ? (r.parsed.notes[0] || "This doesn't look like an order message.")
        : r.action === "update" ? `Matches your ${r.matchedOrder.marketplace} order #${r.matchedOrder.orderId} (${r.matchedOrder.statusLabel}).` : "";
      renderParsed(r.parsed, r.action, note, false);
    } catch (err) { toast(err.message); }
  }

  async function saveImport() {
    const body = impPayload();
    if (!body.body && !body.subject) { toast("Paste a message first"); return; }
    const btn = $("#impSave");
    btn.disabled = true; btn.classList.add("loading");
    try {
      const r = await post("/api/inbox/import", body);
      renderParsed(r.parsed, r.action, r.reason, r.action === "created" || r.action === "updated");
      if (r.action === "created" || r.action === "updated") {
        toast(r.reason, "box");
        $("#impBody").value = ""; $("#impSubject").value = ""; $("#impFrom").value = "";
        S.ordersSig = "";
        loadOrders();
      }
    } catch (err) { toast(err.message); }
    finally { btn.disabled = false; btn.classList.remove("loading"); }
  }
  $("#impPreview").addEventListener("click", previewImport);
  $("#impSave").addEventListener("click", saveImport);

  $("#copyHook").addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(IMP.hookUrl); toast("Forwarding address copied", "check"); }
    catch (e) {
      const r = document.createRange(); r.selectNode($("#hookUrl"));
      getSelection().removeAllRanges(); getSelection().addRange(r);
      toast("Press Cmd/Ctrl + C to copy");
    }
  });
  $("#regenHook").addEventListener("click", async () => {
    if (!confirmReset()) return;
    const r = await post("/api/inbox/token/regenerate");
    IMP.hookUrl = r.hookUrl;
    $("#hookUrl").textContent = r.hookUrl;
    loadInbox();
    toast("New forwarding address created. Update your Gmail script and phone automations.", "info");
  });
  function confirmReset() {
    const b = $("#regenHook");
    if (b.dataset.armed) { delete b.dataset.armed; b.textContent = "Reset"; return true; }
    b.dataset.armed = "1"; b.textContent = "Click again to confirm";
    setTimeout(() => { if (b.dataset.armed) { delete b.dataset.armed; b.textContent = "Reset"; } }, 3000);
    return false;
  }

  // ======================================================================= notifications
  const alertsPanel = $("#alertsPanel");
  function closeAlerts() { alertsPanel.classList.remove("open"); alertsPanel.setAttribute("aria-hidden", "true"); }
  $("#alertsBtn").addEventListener("click", () => {
    alertsPanel.classList.add("open");
    alertsPanel.setAttribute("aria-hidden", "false");
    S.unread = 0;
    $("#alertCount").classList.add("hidden");
  });
  $("#closeAlerts").addEventListener("click", closeAlerts);
  $("#alertList").addEventListener("click", (e) => {
    const item = e.target.closest("[data-alert-product]");
    if (item) { closeAlerts(); openProduct(item.dataset.alertProduct); }
  });

  async function loadAlerts() {
    const alerts = await api("/api/alerts");
    const fresh = alerts.filter((a) => a.id > S.seenAlertId);
    if (fresh.length && S.alertsLoaded) {
      S.unread += fresh.length;
      toast(fresh[0].title, fresh[0].type === "PRICE_DROP" ? "trendDown" : "box");
      const bell = $("#alertsBtn");
      bell.classList.remove("bell-ring"); void bell.offsetWidth; bell.classList.add("bell-ring");
      if (fresh.some((a) => a.type === "ORDER_UPDATE") && S.view === "orders") loadOrders();
    }
    if (alerts.length) S.seenAlertId = Math.max(S.seenAlertId, alerts[0].id);
    S.alertsLoaded = true;
    const count = $("#alertCount");
    count.textContent = S.unread;
    count.classList.toggle("hidden", S.unread === 0);
    $("#alertList").innerHTML = alerts.length ? alerts.map((a) => `
      <div class="alert-item" ${a.productId ? `data-alert-product="${a.productId}"` : ""}>
        <div class="ic-wrap">${icon(a.type === "PRICE_DROP" ? "trendDown" : "box", 16)}</div>
        <div><div class="t">${esc(a.title)}</div><div class="d">${esc(a.detail)}</div><div class="time">${timeAgo(a.time)}</div></div>
      </div>`).join("") : `<div class="empty">Price drops and order updates will appear here.</div>`;
  }

  // ======================================================================= assistant
  const chatPanel = $("#chatPanel");
  const chatBody = $("#chatBody");

  function openChat(prompt) {
    chatPanel.classList.add("open");
    chatPanel.setAttribute("aria-hidden", "false");
    document.body.classList.add("chat-open");
    if (!S.chatStarted) {
      S.chatStarted = true;
      sendToAssistant("hi", true);
    }
    if (prompt) setTimeout(() => sendToAssistant(prompt), S.chatBusy ? 900 : 100);
    setTimeout(() => $("#chatInput").focus(), 250);
  }
  function closeChat() {
    chatPanel.classList.remove("open");
    chatPanel.setAttribute("aria-hidden", "true");
    document.body.classList.remove("chat-open");
  }
  $("#chatFab").addEventListener("click", () => openChat());
  $("#chatClose").addEventListener("click", closeChat);
  $("#chatForm").addEventListener("submit", (e) => {
    e.preventDefault();
    const text = $("#chatInput").value.trim();
    if (!text || S.chatBusy) return;
    $("#chatInput").value = "";
    sendToAssistant(text);
  });
  $("#chatSuggestions").addEventListener("click", (e) => {
    const chip = e.target.closest("[data-suggest]");
    if (chip && !S.chatBusy) sendToAssistant(chip.dataset.suggest);
  });
  chatBody.addEventListener("click", (e) => {
    const cmp = e.target.closest("[data-open-compare]");
    if (cmp) openCompare(cmp.dataset.openCompare.split(","));
  });

  const scrollChat = () => { chatBody.scrollTop = chatBody.scrollHeight; };

  function addUserMsg(text) {
    const m = document.createElement("div");
    m.className = "msg user";
    m.innerHTML = `<div class="bubble">${esc(text)}</div>`;
    chatBody.appendChild(m);
    scrollChat();
  }

  /** Builds the HTML for an assistant reply's cards/tables; charts are drawn via callbacks pushed to `after`. */
  function richBlocks(r, after) {
    let html = "";
    if (r.products && r.products.length) {
      html += `<div class="mini-cards">${r.products.map((c) => `
        <button class="mini-card" data-open="${c.product.id}">
          <span class="mc-top">${pIcon(c.product.kind, 16)}<strong>${esc(c.product.shortName)}</strong></span>
          <span class="price">${inr(c.bestPrice)}</span>
          <small>${c.bestMarketplace} · ${c.rating.toFixed(1)} rating</small>
        </button>`).join("")}</div>`;
    }
    if (r.comparison) {
      html += compareTable(r.comparison, true);
      html += `<button class="chat-action" data-open-compare="${r.comparison.products.map((c) => c.product.id).join(",")}">Open full comparison</button>`;
    }
    if (r.policies) {
      r.policies.forEach((b) => {
        html += `<div class="block-title">${esc(b.title)}</div>` + policyTable(b.policies, b.mostFlexibleStore, true);
      });
      if (r.policies.length) html += `<div class="powered">${esc(r.policies[0].disclaimer)}</div>`;
    }
    if (r.history) {
      const id = "cc" + Math.random().toString(36).slice(2);
      html += `<div class="chat-chart"><div class="cc-head"><span>Lowest price, last 90 days</span><span class="verdict v-${r.history.insight.verdict}">${VERDICT[r.history.insight.verdict]}</span></div><div id="${id}"></div></div>
        <button class="chat-action" data-open="${r.history.product.product.id}">View full price history</button>`;
      after.push(() => {
        const ins = r.history.insight;
        const target = document.getElementById(id);
        if (target) ShopCharts.lineChart(target, { series: [{ name: "Lowest price", color: "var(--text)", points: ins.best, width: 1.8, area: true }],
          band: saleBand(ins.best, ins), height: 110, compact: true, tooltip: true });
      });
    }
    if (r.orders && r.orders.length) {
      html += `<div class="mini-cards">${r.orders.map((o) => `
        <div class="mini-card"><span class="mc-top">${pIcon(o.productId, 16)}<strong>${esc(o.productName)}</strong></span>
        <span class="status-pill st-${o.status}" style="margin:0">${esc(o.statusLabel)}</span>
        <small>${esc(o.marketplace)}${o.expectedDelivery ? " · arriving " + fmtDate(o.expectedDelivery) : ""}</small></div>`).join("")}</div>`;
    }
    return html;
  }

  async function sendToAssistant(text, silent = false) {
    if (S.chatBusy) return;
    S.chatBusy = true;
    if (!silent) addUserMsg(text);
    $("#chatSuggestions").innerHTML = "";
    const m = document.createElement("div");
    m.className = "msg bot";
    m.innerHTML = `<div class="bot-avatar">${icon("spark", 14)}</div><div class="bot-wrap"><div class="bubble typing"><span></span><span></span><span></span></div></div>`;
    chatBody.appendChild(m);
    scrollChat();
    try {
      const [r] = await Promise.all([post("/api/assistant", { message: text }), new Promise((ok) => setTimeout(ok, 400))]);
      $("#aiEngine").textContent = r.poweredBy;
      const wrap = $(".bot-wrap", m);
      wrap.innerHTML = `<div class="bubble"></div>`;
      typewrite($(".bubble", wrap), r.text, 12);
      const after = [];
      const extra = richBlocks(r, after);
      chatPanel.classList.toggle("expanded", !!(r.comparison && r.comparison.products.length > 2) || !!(r.policies && r.policies.some((b) => b.policies.length > 2)));
      const words = r.text.split(/\s+/).length;
      setTimeout(() => {
        if (extra) wrap.insertAdjacentHTML("beforeend", extra);
        after.forEach((fn) => fn());
        $("#chatSuggestions").innerHTML = (r.suggestions || []).map((s) =>
          `<button class="qchip" data-suggest="${esc(s)}">${esc(s)}</button>`).join("");
        scrollChat();
      }, Math.min(1200, words * 5 + 100));
    } catch (err) {
      $(".bot-wrap", m).innerHTML = `<div class="bubble">Sorry, I couldn't reach the server. Please try again.</div>`;
    } finally {
      S.chatBusy = false;
      scrollChat();
    }
  }

  // ======================================================================= session & polling
  $("#logoutBtn").addEventListener("click", async () => {
    try { await post("/api/auth/logout"); } catch (e) { /* ignore */ }
    window.location.href = "/login.html";
  });

  async function poll() {
    try {
      await Promise.all([
        loadAlerts(),
        S.view === "shop" ? loadShop(false) : S.view === "orders" ? loadOrders() : loadDiscover(true),
        refreshModalPrices(),
      ]);
      $("#lastSync").textContent = new Date().toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit", second: "2-digit" });
      $(".live-pill").classList.remove("offline");
    } catch (err) {
      $("#lastSync").textContent = "offline";
      $(".live-pill").classList.add("offline");
    }
  }

  async function init() {
    Icons.hydrate();
    syncTheme();
    try {
      S.user = await api("/api/auth/me");
    } catch (e) { return; }
    $("#userName").textContent = S.user.name;
    $("#userEmail").textContent = S.user.email;
    $("#userAvatar").textContent = S.user.name.split(/\s+/).map((w) => w[0]).slice(0, 2).join("").toUpperCase();
    $("#aiEngine").textContent = S.user.aiEngine;
    try {
      (await api("/api/products")).forEach((s) => { S.kinds[s.product.id] = s.product.kind; });
    } catch (e) { /* icons fall back to a generic box */ }
    setupOverview();
    moveGlider();
    syncSegs();
    observeReveals();
    await loadDiscover();
    observeReveals();
    poll();
    setInterval(poll, 4000);
    window.addEventListener("resize", () => { moveGlider(); syncSegs(); if (S.modal) drawHistory(); });
    if (document.fonts) document.fonts.ready.then(() => { moveGlider(); syncSegs(); });
  }
  init();
})();
