#!/usr/bin/env node
// CH002: render oryginalnego HTML w Chrome na Androidzie przez zdalne DevTools.
// Wymaga: lokalny serwer HTTP pakietu referencyjnego, `adb reverse tcp:8765 tcp:8765`
// oraz `adb forward tcp:9222 localabstract:chrome_devtools_remote`.
// Oryginalny HTML nie jest modyfikowany; w pamięci strony zdejmowany jest tylko
// prezentacyjny promień i cień iframe (tak jak przy wzorcach macOS).
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

const arg = (name, def) => { const i = process.argv.indexOf("--" + name); return i > 0 ? process.argv[i + 1] : def; };
const devtools = "http://localhost:" + arg("port", "9222");
const url = arg("url", "http://localhost:8765/" + encodeURIComponent("Chatra — Liquid Glass.html"));
const out = path.resolve(arg("out", "docs/implementacja_android/CH002_Android"));
const runs = Number(arg("runs", "2"));
const base = path.resolve("docs/implementacja_android/Chatra_Pakiet_referencyjny");
const reference = JSON.parse(fs.readFileSync(path.join(base, "pomiary_przegladarki.json"), "utf8"));
const sha = (b) => crypto.createHash("sha256").update(b).digest("hex");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function connect(wsUrl) {
  const ws = new WebSocket(wsUrl);
  let id = 0;
  const pending = new Map(), listeners = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(Error(msg.error.message)) : resolve(msg.result);
    } else if (msg.method) listeners.forEach((l) => l(msg));
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    pending.set(++id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
  const once = (method) => new Promise((r) => { const l = (m) => { if (m.method === method) { listeners.splice(listeners.indexOf(l), 1); r(m.params); } }; listeners.push(l); });
  return new Promise((r) => { ws.onopen = () => r({ send, once, close: () => ws.close() }); });
}

const evaluate = async (cdp, expression) => {
  const r = await cdp.send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (r.exceptionDetails) throw Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
  return r.result.value;
};

// Czeka, aż runtime bundlera wypełni 10 iframe i załaduje fonty w każdym z nich.
const READY = `(async () => {
  for (let t = 0; t < 200; t++) {
    const f = [...document.querySelectorAll("iframe")];
    if (f.length === 10 && f.every((x) => x.contentDocument?.body?.innerText.trim().length > 20)) {
      await Promise.all(f.map((x) => x.contentDocument.fonts.ready));
      for (const x of f) { x.style.borderRadius = "0"; x.style.boxShadow = "none"; }
      await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
      return true;
    }
    await new Promise((r) => setTimeout(r, 100));
  }
  return false;
})()`;

const ENV = `(() => ({
  userAgent: navigator.userAgent, language: navigator.language, languages: navigator.languages,
  devicePixelRatio, innerWidth, innerHeight, visualViewportScale: visualViewport.scale,
  screen: { width: screen.width, height: screen.height },
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  prefersDark: matchMedia("(prefers-color-scheme: dark)").matches,
  prefersReducedMotion: matchMedia("(prefers-reduced-motion: reduce)").matches,
  frames: [...document.querySelectorAll("iframe")].map((f) => {
    const r = f.getBoundingClientRect(), d = f.contentDocument, w = f.contentWindow;
    return { title: f.title, x: r.left + scrollX, y: r.top + scrollY, w: r.width, h: r.height,
      innerWidth: w.innerWidth, innerHeight: w.innerHeight,
      runningAnimations: d.getAnimations().filter((a) => a.playState === "running").length,
      fontFaces: [...d.fonts].filter((x) => x.status === "loaded").map((x) => x.family + " " + x.weight + " " + x.style + " " + x.unicodeRange.slice(0, 24)) };
  }),
}))()`;

// Ten sam wybór elementów co pomiary_przegladarki.json: tag + tekst albo aria-label (długie teksty
// są tam ucięte, więc wystarcza prefiks), n-te wystąpienie.
const MEASURE = (i, refs) => `(() => {
  const d = document.querySelectorAll("iframe")[${i}].contentDocument, w = d.defaultView;
  const norm = (s) => (s || "").replace(/\\s+/g, " ").trim();
  const labels = (e) => [e.innerText, e.getAttribute("aria-label"), e.textContent].map(norm);
  const seen = {};
  return ${JSON.stringify(refs)}.map((ref) => {
    const text = norm(ref.text), key = ref.tag + "|" + text;
    const n = seen[key] = (seen[key] ?? -1) + 1;
    const all = [...d.getElementsByTagName(ref.tag)];
    const innermost = (list) => list.filter((e) => !list.some((o) => o !== e && e.contains(o)));
    const exact = innermost(all.filter((e) => labels(e).includes(text)));
    const el = (exact.length ? exact : innermost(all.filter((e) => text && labels(e).some((l) => l.startsWith(text)))))[n];
    if (!el) return { tag: ref.tag, text: ref.text.slice(0, 60), found: false };
    const r = el.getBoundingClientRect(), cs = w.getComputedStyle(el);
    return { tag: ref.tag, text: ref.text.slice(0, 60), found: true, x: r.x, y: r.y, w: r.width, h: r.height,
      dx: r.x - ref.x, dy: r.y - ref.y, dw: r.width - ref.w, dh: r.height - ref.h,
      size: cs.fontSize, line: cs.lineHeight, weight: cs.fontWeight, font: cs.fontFamily, boxSizing: cs.boxSizing };
  });
})()`;

// Rendered Fonts: CSS.getPlatformFontsForNode dla każdego elementu z bezpośrednim tekstem.
async function renderedFonts(cdp) {
  const { root } = await cdp.send("DOM.getDocument", { depth: -1, pierce: true });
  const docs = [];
  const findFrames = (n) => { if (n.nodeName === "IFRAME" && n.contentDocument) docs.push(n.contentDocument); (n.children || []).forEach(findFrames); };
  findFrames(root);
  const result = [];
  for (const doc of docs) {
    const fonts = {}, targets = [];
    const walk = (n) => {
      if (n.nodeType === 1 && !["SCRIPT", "STYLE", "HEAD"].includes(n.nodeName) && (n.children || []).some((c) => c.nodeType === 3 && c.nodeValue.trim())) targets.push(n.nodeId);
      (n.children || []).forEach(walk);
    };
    walk(doc);
    for (const nodeId of targets) {
      const { fonts: list } = await cdp.send("CSS.getPlatformFontsForNode", { nodeId });
      for (const f of list) {
        const k = f.familyName + (f.postScriptName ? " (" + f.postScriptName + ")" : "") + (f.isCustomFont ? " [web font]" : " [system]");
        fonts[k] = (fonts[k] || 0) + f.glyphCount;
      }
    }
    result.push({ textNodes: targets.length, glyphsByFont: fonts });
  }
  return result;
}

async function run(n, cdp) {
  await cdp.send("Page.reload", { ignoreCache: true });
  await cdp.once("Page.loadEventFired");
  if (!(await evaluate(cdp, READY))) throw Error("iframes not ready");
  await sleep(1500);
  const env = await evaluate(cdp, ENV);
  const screens = [];
  for (let i = 0; i < 10; i++) {
    const id = "S" + String(i + 1).padStart(2, "0"), f = env.frames[i];
    const shot = await cdp.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true, fromSurface: true, clip: { x: f.x, y: f.y, width: 390, height: 844, scale: 1 } });
    const png = Buffer.from(shot.data, "base64");
    const file = path.join(out, `run${n}`, `android-${id}.png`);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, png);
    const measured = await evaluate(cdp, MEASURE(i, reference[i].elements));
    screens.push({ id, title: f.title, png: path.relative(out, file), sha256: sha(png), width: png.readUInt32BE(16), height: png.readUInt32BE(20), measured });
    console.log(`run${n} ${id}: ${png.readUInt32BE(16)}x${png.readUInt32BE(20)} ${sha(png).slice(0, 12)} found ${measured.filter((m) => m.found).length}/${measured.length}`);
  }
  return { env, screens, fonts: await renderedFonts(cdp) };
}

// Chrome na Androidzie nie tworzy kart przez DevTools; kartę otwiera intent (adb), skrypt ją odnajduje.
let target;
for (let t = 0; t < 50 && !target; t++, await sleep(200)) target = (await (await fetch(devtools + "/json/list")).json()).find((x) => x.type === "page" && x.url === url);
if (!target) throw Error("Brak karty z " + url + "; otwórz ją: adb shell am start -a android.intent.action.VIEW -d '" + url + "' --ez create_new_tab true com.android.chrome");
const cdp = await connect(target.webSocketDebuggerUrl);
try {
  for (const d of ["Page", "Runtime", "DOM", "CSS"]) await cdp.send(d + ".enable");
  await cdp.send("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: "light" }, { name: "prefers-reduced-motion", value: "reduce" }] });
  await cdp.send("Emulation.setAutoDarkModeOverride", { enabled: false });
  await cdp.send("Emulation.setLocaleOverride", { locale: "pl-PL" });
  await cdp.send("Emulation.setTimezoneOverride", { timezoneId: "Europe/Warsaw" });
  const browser = await (await fetch(devtools + "/json/version")).json();
  const results = [];
  for (let n = 1; n <= runs; n++) results.push(await run(n, cdp));
  const stable = results[0].screens.map((s, i) => ({ id: s.id, identical: results.every((r) => r.screens[i].sha256 === s.sha256) }));
  const report = { task: "CH002", date: new Date().toISOString(), url, browser: browser.Browser, androidPackage: browser["Android-Package"], emulatedMedia: { prefersColorScheme: "light", prefersReducedMotion: "reduce", autoDark: false, locale: "pl-PL", timezone: "Europe/Warsaw" }, stable, runs: results };
  fs.writeFileSync(path.join(out, "android_capture.json"), JSON.stringify(report, null, 1));
  console.log("stable:", stable.map((s) => s.id + (s.identical ? "=" : "≠")).join(" "));
} finally {
  cdp.close();
  await fetch(devtools + "/json/close/" + target.id);
}
