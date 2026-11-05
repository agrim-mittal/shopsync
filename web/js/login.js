// ShopSync login / sign-up page
(() => {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const card = $("#authCard");
  const form = $("#authForm");
  const errorEl = $("#formError");
  const submit = $("#submitBtn");
  let mode = "login";

  Icons.hydrate();

  // ---------- live-looking preview: the lowest price ticks every few seconds ----------
  const pv = $("#pvPrice");
  if (pv) {
    const base = 65859;
    setInterval(() => {
      const next = base + Math.round((Math.random() * 2 - 1) * 60) * 10;
      pv.textContent = "₹" + next.toLocaleString("en-IN");
      pv.classList.remove("tick"); void pv.offsetWidth; pv.classList.add("tick");
    }, 2800);
  }

  // ---------- theme ----------
  const themeBtn = $("#themeToggle");
  const syncThemeIcon = () => {
    themeBtn.innerHTML = Icons.svg(document.documentElement.dataset.theme === "dark" ? "sun" : "moon", 17);
  };
  syncThemeIcon();
  themeBtn.addEventListener("click", () => {
    const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem("ss-theme", next); } catch (e) { /* storage may be unavailable */ }
    syncThemeIcon();
  });

  // ---------- tabs ----------
  function setMode(m) {
    mode = m;
    card.classList.toggle("signup-mode", m === "signup");
    document.querySelectorAll(".auth-tab").forEach((t) => t.classList.toggle("active", t.dataset.mode === m));
    $("#authTitle").textContent = m === "signup" ? "Create your account" : "Sign in to ShopSync";
    $("#authSub").textContent = m === "signup"
      ? "It takes less than a minute."
      : "Welcome back. Enter your details to continue.";
    submit.querySelector(".btn-label").textContent = m === "signup" ? "Create account" : "Sign in";
    $("#password").setAttribute("autocomplete", m === "signup" ? "new-password" : "current-password");
    errorEl.textContent = "";
  }
  document.querySelectorAll(".auth-tab").forEach((t) => t.addEventListener("click", () => setMode(t.dataset.mode)));

  // ---------- password helpers ----------
  $("#peekBtn").addEventListener("click", () => {
    const pw = $("#password");
    pw.type = pw.type === "password" ? "text" : "password";
    $("#peekBtn").textContent = pw.type === "password" ? "Show" : "Hide";
  });
  $("#password").addEventListener("input", (e) => {
    const v = e.target.value;
    let score = 0;
    if (v.length >= 6) score++;
    if (v.length >= 10) score++;
    if (/[A-Z]/.test(v) && /[a-z]/.test(v)) score++;
    if (/\d/.test(v)) score++;
    if (/[^A-Za-z0-9]/.test(v)) score++;
    const bar = $("#strengthBar");
    bar.style.width = (score / 5 * 100) + "%";
    bar.style.background = score <= 1 ? "var(--bad)" : score <= 3 ? "var(--warn)" : "var(--good)";
  });

  // ---------- submit ----------
  function fail(msg) {
    errorEl.textContent = msg;
    card.classList.remove("shake");
    void card.offsetWidth;
    card.classList.add("shake");
  }

  async function authenticate(payload, endpoint) {
    submit.disabled = true;
    submit.classList.add("loading");
    errorEl.textContent = "";
    try {
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || "Something went wrong. Please try again.");
      document.body.classList.add("leaving");
      setTimeout(() => { window.location.href = "/"; }, 300);
    } catch (err) {
      fail(err.message === "Failed to fetch" ? "Can't reach the server. Is ShopSync running?" : err.message);
      submit.disabled = false;
      submit.classList.remove("loading");
    }
  }

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const email = $("#email").value.trim();
    const password = $("#password").value;
    const name = $("#name").value.trim();
    if (mode === "signup" && name.length < 2) return fail("Please enter your name.");
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return fail("Please enter a valid email address.");
    if (password.length < 6) return fail("Password must be at least 6 characters.");
    authenticate(mode === "signup" ? { name, email, password } : { email, password },
      mode === "signup" ? "/api/auth/signup" : "/api/auth/login");
  });

  $("#demoBtn").addEventListener("click", () => {
    setMode("login");
    $("#email").value = "demo@shopsync.in";
    $("#password").value = "demo123";
    authenticate({ email: "demo@shopsync.in", password: "demo123" }, "/api/auth/login");
  });
})();
