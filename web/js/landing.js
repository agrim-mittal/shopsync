// ShopSync landing page: interactive previews of each feature.
(() => {
  "use strict";
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const inr = (n) => "₹" + Math.round(n).toLocaleString("en-IN");
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

  Icons.hydrate();

  // ------------------------------------------------------------- nav: shrink + active link pill
  const nav = $("#lpNav");
  const glider = $(".lp-link-glider");
  function moveGlider(a) {
    if (!a || !glider) return;
    glider.style.width = a.offsetWidth + "px";
    glider.style.transform = `translateX(${a.offsetLeft}px)`;
  }
  const links = $$("#lpLinks a");
  links.forEach((a) => a.addEventListener("mouseenter", () => moveGlider(a)));
  $("#lpLinks").addEventListener("mouseleave", () => moveGlider($("#lpLinks a.active")));
  const sections = links.map((a) => document.querySelector(a.getAttribute("href")));
  function onScroll() {
    nav.classList.toggle("scrolled", scrollY > 10);
    let current = links[0];
    sections.forEach((sec, i) => { if (sec && sec.getBoundingClientRect().top < innerHeight * 0.45) current = links[i]; });
    if (!current.classList.contains("active")) {
      links.forEach((a) => a.classList.toggle("active", a === current));
      moveGlider(current);
    }
    drawHowLine();
  }
  addEventListener("scroll", onScroll, { passive: true });
  addEventListener("resize", () => moveGlider($("#lpLinks a.active")));
  requestAnimationFrame(() => moveGlider($("#lpLinks a.active")));

  // ------------------------------------------------------------- hero bar chart (hover or auto-cycle)
  const MONTHS = [["Jun", 75399], ["Jul", 74199], ["Aug", 72999], ["Sep", 71499], ["Oct", 66999], ["Nov", 70239]];
  const maxP = 78000, minP = 60000;
  const bars = $("#heroBars");
  bars.innerHTML = MONTHS.map(([m, p], k) =>
    `<div class="lp-bar" data-k="${k}"><i style="--h:${20 + (p - minP) / (maxP - minP) * 75}%;--k:${k}"></i><small>${m}</small></div>`).join("")
    + `<div class="lp-tip" id="barTip"></div>`;
  let barIdx = 4, hovering = false;
  function setBar(k) {
    barIdx = k;
    $$(".lp-bar", bars).forEach((b, i) => b.classList.toggle("on", i === k));
    const b = $$(".lp-bar", bars)[k];
    const tip = $("#barTip");
    tip.innerHTML = `<small>${MONTHS[k][0]} lowest</small>${inr(MONTHS[k][1])}`;
    tip.style.left = (b.offsetLeft + b.offsetWidth / 2) + "px";
  }
  bars.addEventListener("mouseover", (e) => { const b = e.target.closest(".lp-bar"); if (b) { hovering = true; setBar(Number(b.dataset.k)); } });
  bars.addEventListener("mouseleave", () => { hovering = false; });
  setTimeout(() => setBar(4), 900);
  setInterval(() => { if (!hovering && !reduce) setBar((barIdx + 1) % MONTHS.length); }, 2600);

  // ------------------------------------------------------------- live ticker from the real API
  async function loadTicker(first) {
    try {
      const res = await fetch("/api/public/deals?limit=10");
      if (!res.ok) return;
      const deals = await res.json();
      if (first) {
        const item = (d) => `<span class="tk" data-id="${d.product.id}"><span class="tk-ic">${Icons.product(d.product.kind, 16)}</span>${esc(d.product.shortName)}
          <b data-price>${inr(d.bestPrice)}</b><span class="st s-${d.bestMarketplace}"><i class="dot-store"></i>${d.bestMarketplace}</span>
          <span class="drop">${d.belowAverage > 0 ? "−" + d.belowAverage.toFixed(1) + "%" : ""}</span></span>`;
        const html = deals.map(item).join("");
        $("#ticker").innerHTML = html + html;   // doubled for a seamless loop
        const saved = Math.round(deals.reduce((s, d) => s + Math.max(0, d.average30 - d.bestPrice), 0));
        const st = $("#statSaved");
        st.dataset.countto = saved;
        if (st.closest(".is-in")) Motion.countTo(st);
      } else {
        deals.forEach((d) => $$(`.tk[data-id="${d.product.id}"] [data-price]`).forEach((b) => {
          const txt = inr(d.bestPrice);
          if (b.textContent !== txt) { b.textContent = txt; b.classList.remove("tick-down"); void b.offsetWidth; b.classList.add("tick-down"); }
        }));
      }
    } catch (e) { /* the page still works without live data */ }
  }
  loadTicker(true);
  setInterval(() => loadTicker(false), 5000);

  // ------------------------------------------------------------- feature 1: compare
  const CMP = [
    [["Flipkart", 70449], ["Amazon", 71509], ["Croma", 72919]],
    [["Amazon", 23999], ["Flipkart", 24990], ["Croma", 26990]],
    [["Myntra", 5795], ["Flipkart", 6095], ["Amazon", 6249]],
  ];
  let cmpP = 0;
  function renderCmp(jitter) {
    const rows = CMP[cmpP].map(([s, p]) => [s, jitter ? p + Math.round((Math.random() - .5) * p * 0.012 / 10) * 10 : p]).sort((a, b) => a[1] - b[1]);
    const max = rows[rows.length - 1][1];
    $("#cmpRows").innerHTML = rows.map(([s, p], i) => `
      <div class="cmp-row ${i === 0 ? "best" : ""}"><span class="s-${s}"><i class="dot-store"></i>${s}</span>
      <span class="bar"><i style="--w:${Math.round(100 - 55 * (p - rows[0][1]) / Math.max(1, max - rows[0][1]))}%"></i></span>
      <b>${inr(p)}</b></div>`).join("") + `<small class="muted" style="margin-top:6px">Cheapest: <b>${rows[0][0]}</b>, saves ${inr(max - rows[0][1])}</small>`;
  }
  $("#cmpSeg").addEventListener("click", (e) => {
    const b = e.target.closest("[data-p]");
    if (!b) return;
    $$("#cmpSeg button").forEach((x) => x.classList.toggle("on", x === b));
    cmpP = Number(b.dataset.p);
    renderCmp(false);
  });
  renderCmp(false);
  setInterval(() => renderCmp(true), 3500);

  // ------------------------------------------------------------- feature 2: price alert
  const NOW = 23999;
  function renderAlert() {
    const t = Number($("#alRange").value);
    $("#alTarget").textContent = inr(t);
    const conds = $$(".cond-t.on").map((b) => b.dataset.c);
    const ratingOk = true, stockOk = true, storeOk = true;    // the sample product meets all of these
    const priceOk = NOW <= t;
    const pct = Math.max(4, Math.min(100, (1 - (NOW - t) / (NOW * 0.25)) * 100));
    const fill = $("#alMeter");
    fill.style.width = (priceOk ? 100 : pct) + "%";
    const st = $("#alStatus");
    const all = priceOk && ratingOk && stockOk && storeOk;
    st.className = "mini-status " + (all ? "ok" : pct > 70 ? "warn" : "");
    st.textContent = all ? `Buy now · ${conds.length + 1} conditions met` : pct > 70 ? "Almost there" : "Watching";
  }
  $("#alRange").addEventListener("input", renderAlert);
  $$(".cond-t").forEach((b) => b.addEventListener("click", () => { b.classList.toggle("on"); renderAlert(); }));
  renderAlert();

  // ------------------------------------------------------------- feature 3: final price
  const LISTED = 70239;
  const BANKS = { HDFC: ["HDFC card: 10% off (max ₹1,500)", 1500], ICICI: ["ICICI card: 10% off (max ₹1,250)", 1250], Axis: ["Axis card: 5% cashback", 3512] };
  function renderFinal() {
    const on = $$("#fpCards button.on").map((b) => b.dataset.b);
    const lines = [["Listed price", LISTED, ""], ["Platform fee", 7, ""]];
    const banks = on.filter((b) => BANKS[b]).map((b) => BANKS[b]).sort((a, b) => b[1] - a[1]);
    if (banks.length) lines.push([banks[0][0], -banks[0][1], "neg"]);
    if (on.includes("ex")) lines.push(["Exchange: old phone + bonus", -9500, "neg"]);
    $("#fpLines").innerHTML = lines.map(([l, v, c], i) => `<div class="${c}" style="animation-delay:${i * 50}ms"><span>${l}</span><span>${v < 0 ? "−" + inr(-v) : (i ? "+" : "") + inr(v)}</span></div>`).join("");
    const total = lines.reduce((s, l) => s + l[1], 0);
    animateNumber($("#fpTotal"), total);
  }
  function animateNumber(el, to) {
    const from = Number(el.dataset.v || to);
    el.dataset.v = to;
    const start = performance.now();
    const step = (now) => {
      const p = Math.min(1, (now - start) / 600);
      el.textContent = inr(from + (to - from) * (1 - Math.pow(1 - p, 3)));
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
  $("#fpCards").addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (b) { b.classList.toggle("on"); renderFinal(); }
  });
  renderFinal();

  // ------------------------------------------------------------- feature 4: return countdown
  const deadline = Date.now() + (22 * 3600 + 47 * 60 + 12) * 1000;
  function tickReturn() {
    const left = Math.max(0, deadline - Date.now());
    const h = Math.floor(left / 3600000), m = Math.floor(left / 60000) % 60, s = Math.floor(left / 1000) % 60;
    $("#rwClock").textContent = [h, m, s].map((x) => String(x).padStart(2, "0")).join(":");
    const frac = left / (7 * 24 * 3600000);              // of a 7-day window
    $("#rwRing").style.strokeDashoffset = String(314 * (1 - frac));
  }
  tickReturn();
  setInterval(tickReturn, 1000);

  // ------------------------------------------------------------- feature 5: budget
  const BUDGET = 70000;
  function renderBudget() {
    const v = Number($("#bgRange").value);
    const pct = v / BUDGET * 100;
    $("#bgSpent").textContent = inr(v);
    const fill = $("#bgFill");
    fill.style.width = Math.min(100, pct) + "%";
    const lvl = pct >= 100 ? ["Over budget", "bad", "var(--bad)"] : pct >= 90 ? ["90% used", "warn", "#d97706"]
      : pct >= 70 ? ["70% used", "warn", "#c99a06"] : ["On track", "ok", "var(--good)"];
    fill.style.background = lvl[2];
    const pill = $("#bgPill");
    pill.className = "mini-status " + lvl[1];
    pill.textContent = lvl[0] + " · " + Math.round(pct) + "%";
  }
  $("#bgRange").addEventListener("input", renderBudget);
  renderBudget();

  // ------------------------------------------------------------- feature 6: import from SMS
  const SMS = [
    ["Your AJIO order FN7781 for Levi's Slim Fit T-shirt (₹1,099) is confirmed.", "Order placed", 2],
    ["Shipped! AJIO order FN7781 is on its way with Delhivery, AWB 2338190045.", "Shipped · Delhivery", 3],
    ["Out for delivery: AJIO order FN7781 will reach you today.", "Out for delivery", 4],
  ];
  let smsI = 0;
  function typeSms() {
    const [text, status, steps] = SMS[smsI];
    const p = $("#imText");
    const card = $("#imCard");
    card.classList.remove("show");
    p.textContent = "";
    let i = 0;
    const t = setInterval(() => {
      i += 2;
      p.textContent = text.slice(0, i);
      if (i >= text.length) {
        clearInterval(t);
        setTimeout(() => {
          $("#imStatus").textContent = status;
          $$(".im-steps i").forEach((x, k) => x.classList.toggle("on", k < steps));
          card.classList.add("show");
        }, 300);
        smsI = (smsI + 1) % SMS.length;
        setTimeout(typeSms, 3600);
      }
    }, 28);
  }
  typeSms();

  // ------------------------------------------------------------- how it works: line fills as you scroll
  function drawHowLine() {
    const box = $("#howSteps");
    if (!box) return;
    const r = box.getBoundingClientRect();
    const p = Math.max(0, Math.min(1, (innerHeight * 0.75 - r.top) / (r.height + innerHeight * 0.2)));
    $("#howLine").style.transform = `scaleX(${p})`;
    $$(".how-step", box).forEach((s, i) => s.classList.toggle("lit", p > i * 0.42 + 0.05));
  }
  drawHowLine();

  // ------------------------------------------------------------- assistant chat demo
  const CHAT = [
    ["Best earbuds under ₹5,000?", "boAt Airdopes 141 at ₹1,159 on Flipkart is the best value: 4.3★ and 74% off MRP."],
    ["Final price of the Galaxy S24 with my HDFC card?", "Amazon works out cheapest: ₹70,909 after a ₹500 coupon and ₹1,500 HDFC discount."],
    ["Alert me if the Sony XM5 drops below ₹22,000", "Done. It's ₹23,999 now. It was below your target on 9 of the last 90 days, so it's worth waiting."],
    ["How much of my budget is left?", "₹26,290 of ₹1,10,000. Electronics is past 90%, so maybe hold off on gadgets until December."],
  ];
  let chatI = 0;
  const body = $("#chatDemo");
  function sleep(ms) { return new Promise((ok) => setTimeout(ok, ms)); }
  async function runChat() {
    for (;;) {
      const [q, a] = CHAT[chatI];
      const input = $("#chatTyping");
      for (let i = 0; i <= q.length; i++) { input.textContent = q.slice(0, i); await sleep(reduce ? 0 : 32); }
      await sleep(350);
      input.textContent = "";
      body.insertAdjacentHTML("beforeend", `<div class="lc-msg me">${esc(q)}</div>`);
      await sleep(400);
      body.insertAdjacentHTML("beforeend", `<div class="lc-msg bot typing"><i></i><i></i><i></i></div>`);
      await sleep(1100);
      body.lastElementChild.remove();
      body.insertAdjacentHTML("beforeend", `<div class="lc-msg bot">${esc(a)}</div>`);
      while (body.children.length > 6) body.firstElementChild.remove();
      chatI = (chatI + 1) % CHAT.length;
      await sleep(2600);
    }
  }
  runChat();

  // ------------------------------------------------------------- "ask the assistant" carousel
  const ASKS = [
    ["“Compare the iPhone 15 and Galaxy S24 across every store.”", "Side-by-side table, with the winner of each row marked"],
    ["“Which of my returns are closing soon?”", "Return windows and expiring warranties, sorted by urgency"],
    ["“Should I buy the Sony XM5 now or wait?”", "90-day price chart and a clear buy-or-wait verdict"],
    ["“What's Myntra's return policy for sneakers?”", "Store policies side by side: returns, COD, exchange"],
  ];
  let askI = 0, askTimer;
  $("#askDots").innerHTML = ASKS.map((_, i) => `<button aria-label="Example ${i + 1}" data-i="${i}"></button>`).join("");
  function showAsk(i) {
    askI = i;
    const card = $("#askCard");
    card.classList.add("swap");
    setTimeout(() => {
      $("#askText").textContent = ASKS[i][0];
      $("#askHint").textContent = ASKS[i][1];
      card.classList.remove("swap");
    }, 280);
    $$("#askDots button").forEach((b, k) => b.classList.toggle("on", k === i));
    clearTimeout(askTimer);
    askTimer = setTimeout(() => showAsk((askI + 1) % ASKS.length), 4200);
  }
  $("#askDots").addEventListener("click", (e) => { const b = e.target.closest("[data-i]"); if (b) showAsk(Number(b.dataset.i)); });
  showAsk(0);
})();
