// Shared interaction layer: magnetic buttons, cursor glow, 3D tilt, scroll reveal,
// count-up numbers and a scroll progress bar. Works on content added later too.
window.Motion = (() => {
  "use strict";
  const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const fine = window.matchMedia && window.matchMedia("(pointer: fine)").matches;
  const MAGNETIC = "[data-magnetic], .btn-butter, .page-head .btn-primary";
  const GLOW = "[data-glow], .hero, .budget-hero";

  // ---- magnetic: the button leans a little towards the cursor
  let magnet = null;
  document.addEventListener("pointermove", (e) => {
    if (reduce || !fine) return;
    const btn = e.target.closest && e.target.closest(MAGNETIC);
    if (magnet && magnet !== btn) { magnet.style.transform = ""; magnet = null; }
    if (btn) {
      magnet = btn;
      btn.classList.add("magnetic");
      const r = btn.getBoundingClientRect();
      const dx = (e.clientX - (r.left + r.width / 2)) / r.width;
      const dy = (e.clientY - (r.top + r.height / 2)) / r.height;
      btn.style.transform = `translate(${dx * 8}px, ${dy * 6}px)`;
    }
    // ---- glow follows the cursor across dark panels
    const glow = e.target.closest && e.target.closest(GLOW);
    if (glow) {
      glow.classList.add("glow-follow");
      const r = glow.getBoundingClientRect();
      glow.style.setProperty("--gx", (e.clientX - r.left) + "px");
      glow.style.setProperty("--gy", (e.clientY - r.top) + "px");
    }
  }, { passive: true });

  // ---- tilt: [data-tilt] elements rotate slightly in 3D with the cursor
  function bindTilt(root = document) {
    if (reduce || !fine) return;
    root.querySelectorAll("[data-tilt]:not([data-tilt-bound])").forEach((el) => {
      el.dataset.tiltBound = "1";
      const max = Number(el.dataset.tilt || 6);
      el.style.transition = "transform .6s cubic-bezier(.2,.7,.2,1)";
      el.addEventListener("pointermove", (e) => {
        const r = el.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - .5;
        const y = (e.clientY - r.top) / r.height - .5;
        el.style.transition = "transform .12s linear";
        el.style.transform = `perspective(1200px) rotateY(${x * max}deg) rotateX(${-y * max}deg)`;
      });
      el.addEventListener("pointerleave", () => {
        el.style.transition = "transform .7s cubic-bezier(.34,1.4,.64,1)";
        el.style.transform = "";
      });
    });
  }

  // ---- count up numbers when they scroll into view: <span data-countto="1200" data-prefix="₹">
  function countTo(el) {
    const to = Number(el.dataset.countto);
    const prefix = el.dataset.prefix || "", suffix = el.dataset.suffix || "";
    const dec = Number(el.dataset.decimals || 0);
    if (reduce) { el.textContent = prefix + to.toLocaleString("en-IN", { minimumFractionDigits: dec, maximumFractionDigits: dec }) + suffix; return; }
    const start = performance.now(), dur = Number(el.dataset.duration || 1400);
    const step = (now) => {
      const p = Math.min(1, (now - start) / dur);
      const v = to * (1 - Math.pow(1 - p, 4));
      el.textContent = prefix + v.toLocaleString("en-IN", { minimumFractionDigits: dec, maximumFractionDigits: dec }) + suffix;
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  // ---- reveal: [data-reveal] fades/slides in once; children with [data-reveal-child] stagger
  const io = "IntersectionObserver" in window ? new IntersectionObserver((entries) => {
    entries.forEach((en) => {
      if (!en.isIntersecting) return;
      const el = en.target;
      el.classList.add("is-in");
      el.querySelectorAll("[data-countto]").forEach(countTo);
      if (el.matches("[data-countto]")) countTo(el);
      io.unobserve(el);
    });
  }, { threshold: 0.15, rootMargin: "0px 0px -40px 0px" }) : null;

  function bindReveal(root = document) {
    root.querySelectorAll("[data-reveal]:not(.is-in), [data-countto]:not([data-reveal])").forEach((el) => {
      if (io) io.observe(el); else { el.classList.add("is-in"); countTo(el); }
    });
  }

  // ---- scroll progress bar (if the page has one)
  const bar = document.querySelector(".scroll-progress i");
  if (bar) {
    const upd = () => {
      const h = document.documentElement.scrollHeight - innerHeight;
      bar.style.transform = `scaleX(${h > 0 ? scrollY / h : 0})`;
    };
    addEventListener("scroll", upd, { passive: true });
    upd();
  }

  function init(root) { bindTilt(root); bindReveal(root); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => init());
  else init();
  return { init, countTo };
})();
