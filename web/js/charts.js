// Tiny dependency-free SVG line chart with animated drawing, a highlighted
// date band (e.g. the festive sale) and a hover tooltip.
window.ShopCharts = (() => {
  "use strict";
  const NS = "http://www.w3.org/2000/svg";

  const el = (tag, attrs = {}, parent) => {
    const n = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
    if (parent) parent.appendChild(n);
    return n;
  };
  const inr = (n) => "₹" + Math.round(n).toLocaleString("en-IN");
  const shortInr = (n) => n >= 100000 ? "₹" + (n / 100000).toFixed(1) + "L"
    : n >= 1000 ? "₹" + (n / 1000).toFixed(n >= 10000 ? 0 : 1) + "k" : "₹" + Math.round(n);
  const fmtDate = (d) => new Date(d + "T00:00:00").toLocaleDateString("en-IN", { day: "numeric", month: "short" });

  /**
   * @param container element to draw into (its contents are replaced)
   * @param opts { series:[{name,color,points:[{date,price}],width,dashed,area}], band:{from,to,label},
   *               height, compact }
   */
  function lineChart(container, opts) {
    container.innerHTML = "";
    container.classList.add("chart-wrap");
    const compact = !!opts.compact;
    const W = Math.max(260, container.clientWidth || 600);
    const H = opts.height || (compact ? 110 : 280);
    const pad = compact ? { l: 6, r: 6, t: 8, b: 8 } : { l: 56, r: 14, t: 16, b: 30 };
    const series = opts.series.filter((s) => s.points && s.points.length);
    if (!series.length) { container.textContent = "No data"; return; }

    // all dates across series, in order
    const dateSet = new Set();
    series.forEach((s) => s.points.forEach((p) => dateSet.add(p.date)));
    const dates = [...dateSet].sort();
    const idx = new Map(dates.map((d, i) => [d, i]));

    let min = Infinity, max = -Infinity;
    series.forEach((s) => s.points.forEach((p) => { min = Math.min(min, p.price); max = Math.max(max, p.price); }));
    const span = (max - min) || max * 0.05 || 1;
    min -= span * 0.12; max += span * 0.12;

    const x = (i) => pad.l + (dates.length === 1 ? 0 : i / (dates.length - 1)) * (W - pad.l - pad.r);
    const y = (v) => pad.t + (1 - (v - min) / (max - min)) * (H - pad.t - pad.b);

    const svg = el("svg", { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: "img" }, container);
    const defs = el("defs", {}, svg);

    // festive-sale band
    if (opts.band && idx.has(opts.band.from) && idx.has(opts.band.to)) {
      const x1 = x(idx.get(opts.band.from)), x2 = x(idx.get(opts.band.to));
      el("rect", { x: x1, y: pad.t, width: Math.max(2, x2 - x1), height: H - pad.t - pad.b, rx: 6,
        style: "fill: var(--surface-2)" }, svg);
      if (!compact) {
        const t = el("text", { x: (x1 + x2) / 2, y: pad.t + 14, "text-anchor": "middle",
          style: "fill: var(--muted); font-size: 11px; font-weight: 500" }, svg);
        t.textContent = opts.band.label || "Sale";
      }
    }

    // grid + axis labels
    if (!compact) {
      for (let g = 0; g <= 4; g++) {
        const v = min + (max - min) * (g / 4);
        const yy = y(v);
        el("line", { x1: pad.l, x2: W - pad.r, y1: yy, y2: yy, style: "stroke: var(--border)" }, svg);
        const t = el("text", { x: pad.l - 10, y: yy + 4, "text-anchor": "end", style: "fill: var(--muted); font-size: 11px" }, svg);
        t.textContent = shortInr(v);
      }
      const ticks = Math.min(6, dates.length);
      for (let k = 0; k < ticks; k++) {
        const i = Math.round(k * (dates.length - 1) / Math.max(1, ticks - 1));
        const t = el("text", { x: x(i), y: H - 8, "text-anchor": k === 0 ? "start" : k === ticks - 1 ? "end" : "middle",
          style: "fill: var(--muted); font-size: 11px" }, svg);
        t.textContent = fmtDate(dates[i]);
      }
    }

    // lines (with optional gradient area)
    series.forEach((s, si) => {
      const pts = s.points.map((p) => [x(idx.get(p.date)), y(p.price)]);
      const d = pts.map((p, i) => (i ? "L" : "M") + p[0].toFixed(1) + " " + p[1].toFixed(1)).join(" ");
      if (s.area) {
        const gid = "g" + Math.random().toString(36).slice(2);
        const lg = el("linearGradient", { id: gid, x1: 0, x2: 0, y1: 0, y2: 1 }, defs);
        el("stop", { offset: "0%", style: `stop-color: ${s.color}; stop-opacity: .08` }, lg);
        el("stop", { offset: "100%", style: `stop-color: ${s.color}; stop-opacity: 0` }, lg);
        const area = el("path", { d: d + ` L ${pts[pts.length - 1][0]} ${H - pad.b} L ${pts[0][0]} ${H - pad.b} Z`,
          fill: `url(#${gid})` }, svg);
        area.style.opacity = 0;
        area.style.transition = "opacity .6s ease .3s";
        requestAnimationFrame(() => { area.style.opacity = 1; });
      }
      const path = el("path", { d, fill: "none", "stroke-linejoin": "round", "stroke-linecap": "round",
        style: `stroke: ${s.color}; stroke-width: ${s.width || 2}px; ${s.dashed ? "stroke-dasharray: 5 5;" : ""}` }, svg);
      if (!s.dashed) {   // animated "drawing" of the line
        const len = path.getTotalLength();
        path.style.strokeDasharray = len;
        path.style.strokeDashoffset = len;
        path.getBoundingClientRect();
        path.style.transition = `stroke-dashoffset ${compact ? .7 : .9}s cubic-bezier(.2,.7,.2,1) ${si * 0.05}s`;
        requestAnimationFrame(() => { path.style.strokeDashoffset = 0; });
      }
    });

    // end-point dots
    series.forEach((s) => {
      const last = s.points[s.points.length - 1];
      el("circle", { cx: x(idx.get(last.date)), cy: y(last.price), r: compact ? 3 : 4,
        style: `fill: ${s.color}; stroke: var(--surface); stroke-width: 2px` }, svg);
    });

    if (compact && !opts.tooltip) return;

    // hover: crosshair + dots + tooltip
    const cross = el("line", { y1: pad.t, y2: H - pad.b, style: "stroke: var(--border-strong); stroke-width: 1px; opacity: 0" }, svg);
    const dots = series.map((s) => el("circle", { r: 5, style: `fill: ${s.color}; stroke: var(--surface); stroke-width: 2px; opacity: 0` }, svg));
    const tip = document.createElement("div");
    tip.className = "chart-tip";
    container.appendChild(tip);
    const lookup = series.map((s) => new Map(s.points.map((p) => [p.date, p.price])));
    const overlay = el("rect", { x: pad.l, y: 0, width: W - pad.l - pad.r, height: H, fill: "transparent" }, svg);

    function move(evt) {
      const rect = svg.getBoundingClientRect();
      const px = (evt.clientX - rect.left) * (W / rect.width);
      const i = Math.max(0, Math.min(dates.length - 1, Math.round((px - pad.l) / (W - pad.l - pad.r) * (dates.length - 1))));
      const date = dates[i];
      const cx = x(i);
      cross.setAttribute("x1", cx); cross.setAttribute("x2", cx); cross.style.opacity = 1;
      let rows = "";
      series.forEach((s, si) => {
        const v = lookup[si].get(date);
        if (v == null) { dots[si].style.opacity = 0; return; }
        dots[si].setAttribute("cx", cx); dots[si].setAttribute("cy", y(v)); dots[si].style.opacity = 1;
        rows += `<div class="tt-row"><span><i style="--c:${s.color}"></i>${s.name}</span><b>${inr(v)}</b></div>`;
      });
      tip.innerHTML = `<div class="tt-date">${fmtDate(date)}</div>${rows}`;
      const scale = rect.width / W;
      let left = cx * scale + 14;
      if (left + 180 > rect.width) left = cx * scale - 194;
      tip.style.left = left + "px";
      tip.style.top = (pad.t * scale) + "px";
      tip.classList.add("show");
    }
    function leave() {
      cross.style.opacity = 0;
      dots.forEach((d) => { d.style.opacity = 0; });
      tip.classList.remove("show");
    }
    overlay.addEventListener("mousemove", move);
    overlay.addEventListener("touchmove", (e) => move(e.touches[0]), { passive: true });
    overlay.addEventListener("mouseleave", leave);
  }

  /** Small sparkline SVG string for cards. */
  function sparkline(values, w = 90, h = 26) {
    if (!values || values.length < 2) return "";
    const min = Math.min(...values), max = Math.max(...values);
    const span = max - min || 1;
    const pts = values.map((v, i) => `${(i / (values.length - 1) * w).toFixed(1)},${(h - 3 - (v - min) / span * (h - 6)).toFixed(1)}`).join(" ");
    const color = values[values.length - 1] <= values[0] ? "var(--good)" : "var(--bad)";
    return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" aria-hidden="true"><polyline points="${pts}" fill="none" style="stroke:${color}" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round"/></svg>`;
  }

  return { lineChart, sparkline, inr };
})();
