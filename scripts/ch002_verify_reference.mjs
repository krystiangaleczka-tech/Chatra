#!/usr/bin/env node
// Kontrola integralności CH002. Nie zastępuje walidacji renderera Androida.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
const base=path.resolve(process.cwd(),"docs/implementacja_android/Chatra_Pakiet_referencyjny");
const read=(x)=>fs.readFileSync(path.join(base,x));
const json=(x)=>JSON.parse(read(x).toString("utf8"));
const sha=(b)=>crypto.createHash("sha256").update(b).digest("hex");
const chk=(v,msg)=>{if(!v)throw Error(msg)};
const manifest=json("manifest_referencji.json"), measure=json("pomiary_przegladarki.json"), styles=json("style_zrodlowe.json");
const hash="80e7652c4de8a483b39a38e6cd319524817af11986499f0f7aee28d4efa4cbe3";
const original=read("Chatra — Liquid Glass.html");
chk(original.length===3996110&&manifest.source_bytes===3996110,"HTML: bad length");
chk(sha(original)===hash&&manifest.source_sha256===hash,"HTML SHA mismatch");
chk(manifest.screens.length===10&&measure.length===10&&styles.length===10,"Not 10 screens");
let count=0;
for(let i=0;i<10;i++){
 const id="S"+String(i+1).padStart(2,"0"),ref=manifest.screens[i],buf=read(ref.path);
 chk(buf.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])),"Not PNG "+id);
 chk(buf.readUInt32BE(16)===390&&buf.readUInt32BE(20)===844,"Wrong PNG size "+id);
 chk(sha(buf)===ref.sha256,"PNG SHA mismatch "+id);
 chk(ref.width===390&&ref.height===844,"Manifest dimensions mismatch "+id);
 chk(measure[i].elements.length>0&&styles[i].elements.length>0,"Missing geometry/CSS "+id);
 for(const e of measure[i].elements)chk([e.x,e.y,e.w,e.h].every(Number.isFinite)&&e.w>0&&e.h>0,"Bad geometry "+id);
 count+=measure[i].elements.length;
 console.log("PASS "+id+": PNG sha256, 390x844, "+measure[i].elements.length+" boxes");
}
console.log("PASS HTML SHA256; 10 PNG; "+count+" measured elements");
console.log("PENDING: Android actual font, viewport capture ×2, device manifest and visual review");
if(process.argv.includes("--strict-android")){console.error("BLOCKED: no Android renderer evidence yet");process.exitCode=2;}
