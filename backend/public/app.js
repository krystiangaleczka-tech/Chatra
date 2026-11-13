"use strict";
(() => {
  const token = window.location.hash.slice(1);
  history.replaceState(null, "", window.location.pathname);
  const status = document.getElementById("status");
  const list = document.getElementById("files");
  const expiry = document.getElementById("expiry");
  const chunkSize = 262144;
  const extensions = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp", "image/gif": "gif", "image/heic": "heic", "video/mp4": "mp4", "video/3gpp": "3gp"};
  async function request(path, body) {
    const response = await fetch(path, {method: "POST", credentials: "omit", cache: "no-store", referrerPolicy: "no-referrer", headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)});
    if (!response.ok) throw new Error(response.status === 429 ? "Osiągnięto limit pobierania. Spróbuj później." : "Link wygasł, został odwołany lub jest nieprawidłowy.");
    return response;
  }
  async function download(file, button, position) {
    button.disabled = true;
    const pieces = [];
    try {
      for (let offset = 0; offset < file.size; offset += chunkSize) {
        const response = await request("/v1/public-shares/chunk", {token, position, offset});
        const bytes = new Uint8Array(await response.arrayBuffer());
        if (bytes.length !== Math.min(chunkSize, file.size - offset)) throw new Error("Nie udało się pobrać całego pliku. Spróbuj ponownie.");
        pieces.push(bytes);
        button.textContent = `${Math.floor((offset + bytes.length) * 100 / file.size)}%`;
      }
      const data = new Uint8Array(file.size);
      let offset = 0;
      for (const piece of pieces) { data.set(piece, offset); offset += piece.length; }
      const digest = [...new Uint8Array(await crypto.subtle.digest("SHA-256", data))].map(b => b.toString(16).padStart(2, "0")).join("");
      if (digest !== file.sha256) throw new Error("Plik nie przeszedł sprawdzenia integralności. Spróbuj ponownie.");
      const url = URL.createObjectURL(new Blob([data], {type: "application/octet-stream"}));
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `Chatra-${position + 1}.${extensions[file.mime] || "bin"}`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
      status.textContent = "Plik sprawdzony. Zapisz go w miejscu wybranym w przeglądarce.";
    } catch (error) { status.textContent = error.message; }
    finally { button.disabled = false; button.textContent = "Pobierz ponownie"; }
  }
  async function open() {
    try {
      if (!/^[0-9a-f]{32}\.[0-9a-f]{64}$/.test(token)) throw new Error("Otwórz pełny link otrzymany od nadawcy.");
      const response = await request("/v1/public-shares/resolve", {token});
      const data = await response.json();
      if (!Array.isArray(data.files) || data.files.length < 1 || data.files.length > 5) throw new Error("Nie udało się odczytać plików.");
      status.textContent = `${data.files.length} ${data.files.length === 1 ? "plik" : data.files.length < 5 ? "pliki" : "plików"} do pobrania. Sprawdzimy integralność przed zapisaniem.`;
      data.files.forEach((file, position) => {
        if (!Number.isSafeInteger(file.size) || file.size < 1 || file.size > 26214400 || !/^[0-9a-f]{64}$/.test(file.sha256)) throw new Error("Nieprawidłowe dane pliku.");
        const row = document.createElement("div"); row.className = "file";
        const label = document.createElement("span"); label.textContent = `Plik ${position + 1} · ${(file.size / 1048576).toLocaleString("pl-PL", {maximumFractionDigits: 1})} MiB`;
        const button = document.createElement("button"); button.type = "button"; button.textContent = "Pobierz plik";
        button.addEventListener("click", () => download(file, button, position));
        row.append(label, button); list.appendChild(row);
      });
      expiry.textContent = `Dostęp do ${new Date(data.expiresAt * 1000).toLocaleString("pl-PL")}.`;
    } catch (error) { list.replaceChildren(); expiry.textContent = ""; status.textContent = error.message; }
  }
  open();
})();
