// Minimal line icons (24×24, stroke-based) used instead of emoji throughout the UI.
window.Icons = (() => {
  "use strict";
  const P = {
    home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M10 21v-6h4v6"/>',
    bag: '<path d="M5 8h14l-1 13H6z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/>',
    box: '<path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5z"/><path d="M3 7.5 12 12l9-4.5M12 12v9"/>',
    spark: '<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6.3 6.3l2.5 2.5M15.2 15.2l2.5 2.5M6.3 17.7l2.5-2.5M15.2 8.8l2.5-2.5"/>',
    chat: '<path d="M4 5h16v11H9l-5 4z"/><path d="M8 9.5h8M8 12.5h5"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>',
    bell: '<path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
    logout: '<path d="M10 4H5v16h5"/><path d="M14 8l4 4-4 4M18 12H9"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
    send: '<path d="M4 12 20 4l-6 16-3-7z"/><path d="m11 13 9-9"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    trendDown: '<path d="M3 7l6 6 4-4 8 8"/><path d="M21 11v6h-6"/>',
    chart: '<path d="M4 4v16h16"/><path d="m7 14 4-4 3 3 5-6"/>',
    shield: '<path d="M12 3 5 6v6c0 4.5 3 7.5 7 9 4-1.5 7-4.5 7-9V6z"/>',
    tag: '<path d="M3 12V4h8l10 10-8 8z"/><circle cx="7.5" cy="8.5" r="1.3"/>',
    truck: '<path d="M2 6h12v10H2zM14 10h4l4 3v3h-8"/><circle cx="6.5" cy="17.5" r="1.8"/><circle cx="17.5" cy="17.5" r="1.8"/>',
    mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3.5 6.5 8.5 6.5 8.5-6.5"/>',
    copy: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
    // product kinds
    phone: '<rect x="7" y="2.5" width="10" height="19" rx="2.2"/><path d="M11 18.5h2"/>',
    headphones: '<path d="M4 18v-5a8 8 0 0 1 16 0v5"/><rect x="3" y="14" width="4.5" height="6.5" rx="1.5"/><rect x="16.5" y="14" width="4.5" height="6.5" rx="1.5"/>',
    earbuds: '<circle cx="8" cy="8.5" r="3.2"/><path d="M8 11.7V19"/><circle cx="16" cy="8.5" r="3.2"/><path d="M16 11.7V19"/>',
    laptop: '<rect x="4.5" y="5" width="15" height="10.5" rx="1.5"/><path d="M2.5 19h19"/>',
    tv: '<rect x="2.5" y="4.5" width="19" height="13" rx="1.8"/><path d="M8.5 21h7"/>',
    watch: '<rect x="6.5" y="6.5" width="11" height="11" rx="3"/><path d="M9 6.5 10 3h4l1 3.5M9 17.5l1 3.5h4l1-3.5M12 10v2.5l1.5 1"/>',
    mouse: '<rect x="6.5" y="3" width="11" height="18" rx="5.5"/><path d="M12 7v3.5"/>',
    shoe: '<path d="M3 16.5V12l2.5-1.5H9V7.5h3l2 3.5 4.5 1.3A3 3 0 0 1 21 15.2v1.3z"/><path d="M3 16.5V19h18v-2.5"/>',
    jeans: '<path d="M7 3h10l1.2 18h-4.4L12 10.5 10.2 21H5.8z"/><path d="M7 6.5h10"/>',
    hoodie: '<path d="M9 4a3 3 0 0 0 6 0l4.5 3-1.8 4-1.7-.9V20H8v-9.9l-1.7.9-1.8-4z"/>',
    glasses: '<circle cx="6.5" cy="14" r="3.5"/><circle cx="17.5" cy="14" r="3.5"/><path d="M10 14h4M3 14l1.5-6M21 14l-1.5-6"/>',
    fryer: '<rect x="5" y="3.5" width="14" height="17" rx="3"/><path d="M5 12h14M10.5 15.5h3"/>',
    pot: '<path d="M4.5 10h15v6.5A3.5 3.5 0 0 1 16 20H8a3.5 3.5 0 0 1-3.5-3.5z"/><path d="M2.5 10h19M9 7h6M12 4v3"/>',
    bottle: '<path d="M9.5 2.5h5v3l1.5 2V19a2.5 2.5 0 0 1-2.5 2.5h-3A2.5 2.5 0 0 1 8 19V7.5l1.5-2z"/><path d="M8 11h8"/>',
  };

  const KIND = {
    smartphone: "phone", headphones: "headphones", earbuds: "earbuds", laptop: "laptop", television: "tv",
    smartwatch: "watch", mouse: "mouse", sneakers: "shoe", "running shoes": "shoe", jeans: "jeans",
    hoodie: "hoodie", sunglasses: "glasses", "air fryer": "fryer", cooker: "pot", flask: "bottle",
  };

  function svg(name, size = 20, stroke = 1.7) {
    const body = P[name] || P.box;
    return `<svg class="ic" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
  }

  /** Icon for a product, from its "kind" (e.g. "smartphone"). */
  function product(kind, size = 24) {
    return svg(KIND[kind] || "box", size, 1.5);
  }

  /** Replaces every <span data-icon="name"> in the page with its SVG. */
  function hydrate(root = document) {
    root.querySelectorAll("[data-icon]").forEach((n) => {
      n.innerHTML = svg(n.dataset.icon, Number(n.dataset.size || 18));
    });
  }

  return { svg, product, hydrate };
})();
