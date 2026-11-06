#!/usr/bin/env node
// Requires Node 22+, adb, a booted Android 12+ device, and a demoDebug APK.
// Checks the actual WebView render; app screenshots include native demo controls.
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const adb = process.env.ADB ?? 'adb';
const apk = resolve(process.argv[2] ?? 'app/build/outputs/apk/demo/debug/app-demo-debug.apk');
const out = resolve(process.argv[3] ?? 'build/ch004/device');
const fixtures = JSON.parse(readFileSync('build/ch006/screen-fixtures.json', 'utf8'));
const command = (...args) => execFileSync(adb, args, { timeout: 30000, maxBuffer: 20 * 1024 * 1024 });
const pause = (ms) => new Promise((r) => setTimeout(r, ms));
const pkg = 'pl.chatra.demo';
mkdirSync(out, { recursive: true });
command('install', '-r', apk);
const report = {
  device: command('shell', 'getprop', 'ro.product.model').toString().trim(),
  api: command('shell', 'getprop', 'ro.build.version.sdk').toString().trim(),
  renderer: 'Android WebView, bundled HTML (not native Compose screens)',
  captures: 'Full app screenshots; not directly comparable with CH002 golden dimensions',
  screens: [],
};

async function connect(url) {
  const ws = new WebSocket(url);
  let id = 0;
  const pending = new Map();
  ws.onmessage = (message) => {
    const result = JSON.parse(message.data);
    const item = pending.get(result.id);
    if (!item) return;
    pending.delete(result.id);
    result.error ? item.reject(Error(result.error.message)) : item.resolve(result.result);
  };
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  return {
    close: () => ws.close(),
    evaluate: (expression) => new Promise((resolve, reject) => {
      const requestId = ++id;
      const timer = setTimeout(() => { pending.delete(requestId); reject(Error('CDP timeout')); }, 30000);
      pending.set(requestId, {
        resolve: (r) => { clearTimeout(timer); r.exceptionDetails ? reject(Error(JSON.stringify(r.exceptionDetails))) : resolve(r.result.value); },
        reject: (e) => { clearTimeout(timer); reject(e); },
      });
      ws.send(JSON.stringify({ id: requestId, method: 'Runtime.evaluate', params: { expression, returnByValue: true, awaitPromise: true } }));
    }),
  };
}

for (let index = 0; index < 10; index++) {
  const id = `S${String(index + 1).padStart(2, '0')}`;
  command('shell', 'am', 'force-stop', pkg);
  command('shell', 'am', 'start', '-W', '-n', `${pkg}/pl.chatra.MainActivity`, '--ei', 'screen', String(index + 1));
  let target;
  for (let attempt = 0; attempt < 120 && !target; attempt++) {
    try {
      const sockets = command('shell', 'cat', '/proc/net/unix').toString();
      const socket = sockets.match(/@(webview_devtools_remote_\d+)/)?.[1];
      if (socket) {
        command('forward', 'tcp:9223', `localabstract:${socket}`);
        const targets = await (await fetch('http://127.0.0.1:9223/json/list')).json();
        target = targets.find((x) => x.url === 'https://demo.chatra.invalid/reference.html');
      }
    } catch { /* App/WebView may still be starting. */ }
    if (!target) await pause(500);
  }
  if (!target) throw Error(`${id}: no demo WebView target`);
  const cdp = await connect(target.webSocketDebuggerUrl);
  try {
    const result = await cdp.evaluate(`(async () => {
      for (let attempt = 0; attempt < 120; attempt++) {
        const frames = [...document.querySelectorAll('iframe')];
        const frame = frames[${index}], doc = frame?.contentDocument;
        if (frames.length === 10 && doc?.body?.innerText.trim().length > 20 && frame.parentElement.style.display === 'block') {
          await doc.fonts.ready;
          return { title: doc.title, text: doc.body.innerText, fonts: [...doc.fonts].filter(f => f.status === 'loaded').map(f => f.family), width: frame.contentWindow.innerWidth, height: frame.contentWindow.innerHeight };
        }
        await new Promise(r => setTimeout(r, 500));
      }
      throw Error('Reference frame did not render');
    })()`);
    if (!result.fonts.some((f) => f.includes('Inter'))) throw Error(`${id}: Inter did not load`);
    if (result.width !== 390 || result.height !== 844) throw Error(`${id}: incorrect reference viewport`);
    for (const scenario of fixtures.scenarios.filter((s) => s.screenId === id)) {
      if (!result.text.includes(scenario.anchor)) throw Error(`${id}: missing ${scenario.id}`);
    }
    writeFileSync(resolve(out, `demo-${id}-app.png`), command('exec-out', 'screencap', '-p'));
    report.screens.push({ id, title: result.title, fonts: [...new Set(result.fonts)], viewport: [result.width, result.height], status: 'PASS' });
    writeFileSync(resolve(out, 'report.json'), JSON.stringify(report, null, 2) + '\n');
    console.log(`${id}: actual Android WebView render, Inter, fixture anchors PASS`);
  } finally { cdp.close(); }
}
command('forward', '--remove', 'tcp:9223');
console.log(`PASS: 10 Android demo screens. Screenshots and report: ${out}`);
