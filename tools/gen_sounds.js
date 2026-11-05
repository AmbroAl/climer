// Genera i suoni dei segnali (WAV mono 16 bit 48 kHz, la frequenza nativa di quasi tutti i telefoni) in res/raw.
// Picchi a 0,6 del massimo: margine per gli equalizzatori di sistema (es. Honor) che altrimenti saturano.
// Uso: node tools/gen_sounds.js app/src/main/res/raw
const fs = require('fs'); const path = require('path');
const out = process.argv[2]; const SR = 48000;
function wav(samples) {
  const b = Buffer.alloc(44 + samples.length * 2);
  b.write('RIFF', 0); b.writeUInt32LE(36 + samples.length * 2, 4); b.write('WAVE', 8); b.write('fmt ', 12);
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28);
  b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(samples.length * 2, 40);
  samples.forEach((s, i) => b.writeInt16LE(Math.round(Math.max(-1, Math.min(1, s)) * 32767), 44 + i * 2));
  return b;
}
// tono con dissolvenza; decay > 0 per suoni tipo campana
function tone(freq, ms, { amp = 0.6, decay = 0, harmonics = [1] } = {}) {
  const n = Math.round(SR * ms / 1000), fade = Math.round(SR * 0.006), s = [];
  for (let i = 0; i < n; i++) {
    const t = i / SR; let v = 0;
    harmonics.forEach((h, k) => { v += Math.sin(2 * Math.PI * freq * (k + 1) * t) * h; });
    v /= harmonics.reduce((a, b) => a + b, 0);
    const env = Math.min(1, i / fade, (n - i) / fade) * (decay ? Math.exp(-t * decay) : 1);
    s.push(v * env * amp);
  }
  return s;
}
const silence = ms => new Array(Math.round(SR * ms / 1000)).fill(0);
const cat = (...parts) => [].concat(...parts);
const files = {
  cue_short: tone(1000, 120),
  cue_tick: tone(2000, 25, { amp: 0.4 }),
  cue_beep: tone(880, 350),
  cue_pip: cat(tone(1320, 90), silence(70), tone(1320, 90)),
  cue_chime: cat(tone(1319, 380, { decay: 4, harmonics: [1, 0.4, 0.2] }), tone(1047, 600, { decay: 3, harmonics: [1, 0.4, 0.2] })),
  cue_alarm: cat(tone(1500, 80), silence(50), tone(1500, 80), silence(50), tone(1500, 80)),
  cue_finish: cat(tone(784, 180, { decay: 2, harmonics: [1, 0.3] }), tone(988, 180, { decay: 2, harmonics: [1, 0.3] }), tone(1319, 700, { decay: 2.5, harmonics: [1, 0.3] }))
};
fs.mkdirSync(out, { recursive: true });
for (const [name, s] of Object.entries(files)) fs.writeFileSync(path.join(out, name + '.wav'), wav(s));
console.log(Object.keys(files).join(', '));
