// Editable 32px native artwork. Run: node tools/art/spacetime-beacon.js
// No drawing dependencies: rasterize authored shapes, then encode deterministic RGBA PNGs.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const output = path.join(root, 'src/main/resources/assets/gtsr/textures/items');
const preview = path.join(root, 'build/beacon-fix-v1219');
const size = 32;
const image = (w = size, h = size) => ({ w, h, pixels: Buffer.alloc(w * h * 4) });
function pixel(img, x, y, color) {
  if (x < 0 || y < 0 || x >= img.w || y >= img.h) return;
  const index = (y * img.w + x) * 4;
  img.pixels[index] = parseInt(color.slice(1, 3), 16);
  img.pixels[index + 1] = parseInt(color.slice(3, 5), 16);
  img.pixels[index + 2] = parseInt(color.slice(5, 7), 16);
  img.pixels[index + 3] = 255;
}
function rect(img, x, y, w, h, color) {
  for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) pixel(img, xx, yy, color);
}
function circle(img, radius, color) {
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    if ((x + .5 - 16) ** 2 + (y + .5 - 16) ** 2 <= radius ** 2) pixel(img, x, y, color);
  }
}
function polygon(img, points, color) {
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    let inside = false;
    for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
      const a = points[i], b = points[j];
      if (((a[1] > y + .5) !== (b[1] > y + .5)) &&
          x + .5 < (b[0] - a[0]) * (y + .5 - a[1]) / (b[1] - a[1]) + a[0]) inside = !inside;
    }
    if (inside) pixel(img, x, y, color);
  }
}
const base = image();
circle(base, 14.8, '#121b24');
circle(base, 13.8, '#8098a2');
circle(base, 12.6, '#364852');
circle(base, 11.3, '#101c29');
// Small steel bevel and restrained cyan cardinal marks. The center remains a dark dial.
rect(base, 11, 2, 10, 1, '#c4d2d4');
rect(base, 2, 11, 1, 10, '#acbec6');
rect(base, 11, 29, 10, 1, '#445762');
rect(base, 29, 11, 1, 10, '#445762');
rect(base, 15, 5, 2, 3, '#64dbe7');
rect(base, 15, 25, 2, 2, '#437b89');
rect(base, 5, 15, 2, 2, '#437b89');
rect(base, 25, 15, 2, 2, '#437b89');
for (const [x, y] of [[8, 8], [22, 8], [8, 22], [22, 22]]) rect(base, x, y, 2, 2, '#566c78');
const needle = image();
// Bold red north and darker silver south stay identifiable after 16px scaling.
polygon(needle, [[16, 6], [19, 17], [13, 17]], '#af393b');
polygon(needle, [[16, 6], [16, 17], [13, 17]], '#ff8971');
polygon(needle, [[16, 26], [13, 15], [19, 15]], '#8499a8');
polygon(needle, [[16, 26], [16, 15], [19, 15]], '#c6d9df');
rect(needle, 15, 15, 2, 2, '#e8e6c7');
function frame(angle) {
  const result = { ...base, pixels: Buffer.from(base.pixels) };
  const radians = angle * Math.PI / 180;
  const cos = Math.cos(radians), sin = Math.sin(radians);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const dx = x + .5 - 16, dy = y + .5 - 16;
    const sx = Math.floor(16 + dx * cos + dy * sin), sy = Math.floor(16 - dx * sin + dy * cos);
    if (sx < 0 || sx >= size || sy < 0 || sy >= size) continue;
    const source = (sy * size + sx) * 4;
    if (needle.pixels[source + 3]) needle.pixels.copy(result.pixels, (y * size + x) * 4, source, source + 4);
  }
  return result;
}
const crcTable = Array.from({ length: 256 }, (_, value) => {
  for (let i = 0; i < 8; i++) value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  return value >>> 0;
});
function chunk(type, bytes) {
  const kind = Buffer.from(type);
  let crc = 0xffffffff;
  for (const byte of Buffer.concat([kind, bytes])) crc = crcTable[(crc ^ byte) & 255] ^ (crc >>> 8);
  const length = Buffer.alloc(4), checksum = Buffer.alloc(4);
  length.writeUInt32BE(bytes.length); checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
  return Buffer.concat([length, kind, bytes, checksum]);
}
function png(img) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(img.w); header.writeUInt32BE(img.h, 4); header[8] = 8; header[9] = 6;
  const raw = Buffer.alloc((img.w * 4 + 1) * img.h);
  for (let y = 0; y < img.h; y++) img.pixels.copy(raw, y * (img.w * 4 + 1) + 1, y * img.w * 4, (y + 1) * img.w * 4);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
fs.mkdirSync(output, { recursive: true }); fs.mkdirSync(preview, { recursive: true });
const outputs = [];
function save(file, img) {
  const encoded = png(img); fs.writeFileSync(file, encoded);
  outputs.push({ path: path.relative(root, file).replaceAll('\\', '/'),
    width: img.w, height: img.h, sha256: crypto.createHash('sha256').update(encoded).digest('hex') });
}
save(path.join(output, 'spacetime_anchor_beacon.png'), base);
save(path.join(output, 'spacetime_anchor_beacon_needle.png'), needle);
const sheet = image(8 * 144, 240);
for (let y = 0; y < sheet.h; y++) for (let x = 0; x < sheet.w; x++)
  pixel(sheet, x, y, (Math.floor(x / 16) + Math.floor(y / 16)) % 2 ? '#41454b' : '#35393f');
for (let col = 0; col < 8; col++) {
  const icon = frame(col * 45);
  for (const [resolution, scale, top] of [[32, 4, 8], [16, 4, 160]]) {
    for (let y = 0; y < resolution; y++) for (let x = 0; x < resolution; x++) {
      const offset = ((y * size / resolution) * size + x * size / resolution) * 4;
      if (!icon.pixels[offset + 3]) continue;
      const color = '#' + icon.pixels.subarray(offset, offset + 3).toString('hex');
      rect(sheet, col * 144 + (144 - resolution * scale) / 2 + x * scale, top + y * scale, scale, scale, color);
    }
  }
}
save(path.join(preview, 'native-preview.png'), sheet);
for (let index = 0; index < 128; index++) save(path.join(preview, `frame-${String(index).padStart(3, '0')}.png`), frame(index * 360 / 128));
const manifest = { source: 'tools/art/spacetime-beacon.js', sourceSha256: crypto.createHash('sha256')
  .update(fs.readFileSync(__filename)).digest('hex'), frameCount: 128, alpha: [0, 255], outputs };
fs.writeFileSync(path.join(preview, 'art-receipt.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify({ assets: outputs.slice(0, 2), preview: outputs[2], frames: 128 }));
