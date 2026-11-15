'use strict';

/**
 * 生成模组图标：唱片机侧面的红色封面（128×128）。
 *
 * 用高分辨率（512）绘制后按 alpha 加权降采样到 128，避免 16×16 像素放大后的锯齿。
 * 输出覆盖 src/main/resources/assets/musicplayer/icon.png。
 *
 * 用法：node tools/icon/render-icon.js [输出路径]
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.resolve(__dirname, '..', '..');
const DATA_DIR = path.join(__dirname, 'data');
const DEFAULT_OUT = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'musicplayer', 'icon.png');

// ---------- PNG 编码 ----------
const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crc]);
}

function encodePNG(width, height, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // 位深
  ihdr[9] = 6; // RGBA
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0; // filter: none
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------- 原版唱片机侧面材质 ----------
const SIDE = JSON.parse(fs.readFileSync(path.join(DATA_DIR, 'jukebox-side.json'), 'utf8'))
  .map((row) => row.map((hex) => [
    parseInt(hex.slice(0, 2), 16),
    parseInt(hex.slice(2, 4), 16),
    parseInt(hex.slice(4, 6), 16),
  ]));

function texel(u, v) {
  const x = Math.min(15, Math.max(0, Math.floor(u)));
  const y = Math.min(15, Math.max(0, Math.floor(v)));
  return SIDE[y][x];
}

// ---------- 网易云 logo 蒙版（1024×1024 8 位灰度，deflate 压缩） ----------
const MASK_SIZE = 1024;
const MASK_RAW = zlib.inflateSync(fs.readFileSync(path.join(DATA_DIR, 'netease-mask.gray.z')));

const MASK_BBOX = (() => {
  let minX = MASK_SIZE, minY = MASK_SIZE, maxX = -1, maxY = -1;
  for (let y = 0; y < MASK_SIZE; y++) {
    for (let x = 0; x < MASK_SIZE; x++) {
      if (MASK_RAW[y * MASK_SIZE + x] > 8) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  return {
    minX: Math.max(0, minX - 2),
    minY: Math.max(0, minY - 2),
    maxX: Math.min(MASK_SIZE - 1, maxX + 2),
    maxY: Math.min(MASK_SIZE - 1, maxY + 2),
  };
})();

const LOGO_ASPECT = (MASK_BBOX.maxX - MASK_BBOX.minX) / (MASK_BBOX.maxY - MASK_BBOX.minY);

/** 在 logo 归一化坐标 (lx, ly) 处双线性采样蒙版，返回 0..1 的 alpha。 */
function logoAlpha(lx, ly) {
  if (lx < 0 || ly < 0 || lx > 1 || ly > 1) return 0;
  const mx = MASK_BBOX.minX + lx * (MASK_BBOX.maxX - MASK_BBOX.minX);
  const my = MASK_BBOX.minY + ly * (MASK_BBOX.maxY - MASK_BBOX.minY);
  const x0 = Math.floor(mx);
  const y0 = Math.floor(my);
  if (x0 < 0 || y0 < 0 || x0 >= MASK_SIZE - 1 || y0 >= MASK_SIZE - 1) return 0;
  const fx = mx - x0;
  const fy = my - y0;
  const i = y0 * MASK_SIZE + x0;
  const value = MASK_RAW[i] * (1 - fx) * (1 - fy)
    + MASK_RAW[i + 1] * fx * (1 - fy)
    + MASK_RAW[i + MASK_SIZE] * (1 - fx) * fy
    + MASK_RAW[i + MASK_SIZE + 1] * fx * fy;
  return value / 255;
}

// ---------- 颜色工具 ----------
const RED = [194, 12, 12];
const clamp = (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v);

function scaleColor(c, factor) {
  return [clamp(c[0] * factor, 0, 255), clamp(c[1] * factor, 0, 255), clamp(c[2] * factor, 0, 255)];
}

function addColor(c, amount) {
  return [clamp(c[0] + amount, 0, 255), clamp(c[1] + amount, 0, 255), clamp(c[2] + amount, 0, 255)];
}

function mixColor(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

// ---------- 封面 ----------
// 槽深 1 像素的明暗：上沿投影最重、左右侧壁次之，下沿受光提亮。
const DEPTH = { top0: 0.45, top1: 0.72, top2: 0.92, left: 0.66, right: 0.82, bottom: 42, bottom8: 10 };

// 封面最上面两行保留原版材质：上排左右各 2 像素，第二排左右各 1 像素。
function isCoverCorner(iu, iv) {
  if (iv === 0) return iu < 2 || iu > 7;
  if (iv === 1) return iu === 0 || iu === 9;
  return false;
}

/**
 * 唱片机侧面的 10×10 封面。
 *
 * @param u 贴图 u 坐标（0..16）
 * @param v 贴图 v 坐标（0..16）
 * @returns {number[]|null} RGB，不在封面区域时返回 null
 */
function sideCover(u, v) {
  const pu0 = 3, pv0 = 4;
  const pu1 = pu0 + 10, pv1 = pv0 + 10;
  if (u < pu0 || u >= pu1 || v < pv0 || v >= pv1) return null;

  const iu = Math.floor(u - pu0);
  const iv = Math.floor(v - pv0);

  if (isCoverCorner(iu, iv)) return texel(u, v);

  let mul = 1;
  if (iv === 0) mul = DEPTH.top0;
  else if (iv === 1) mul = DEPTH.top1;
  else if (iv === 2) mul = DEPTH.top2;
  if (iu === 0) mul = Math.min(mul, DEPTH.left);
  else if (iu === 9) mul = Math.min(mul, DEPTH.right);

  let add = 0;
  if (iv === 9) add = DEPTH.bottom;
  else if (iv === 8) add = DEPTH.bottom8;

  let c = addColor(scaleColor(RED, mul), add);

  // 上沿有投影、下沿受光，视觉中心略偏下，logo 比槽底正中下移 0.5 像素。
  const logoH = 6.0;
  const logoW = logoH * LOGO_ASPECT;
  const lx = (u - (pu0 + 5 - logoW / 2)) / logoW;
  const ly = (v - (pv0 + 5.5 - logoH / 2)) / logoH;
  const la = logoAlpha(lx, ly);
  if (la > 0) c = mixColor(c, [255, 255, 255], la);
  return c;
}

// ---------- 渲染 ----------
const SIZE = 512; // 绘制分辨率
const SS = 4;     // 每像素超采样数（单轴）

function render(shader) {
  const S = SIZE * SS;
  const acc = new Float64Array(SIZE * SIZE * 4);
  for (let sy = 0; sy < S; sy++) {
    const fy = (sy + 0.5) / SS;
    for (let sx = 0; sx < S; sx++) {
      const c = shader((sx + 0.5) / SS, fy);
      if (!c) continue;
      const a = c[3] === undefined ? 255 : c[3];
      if (a <= 0) continue;
      const i = (Math.floor(sy / SS) * SIZE + Math.floor(sx / SS)) * 4;
      const af = a / 255;
      acc[i] += c[0] * af;
      acc[i + 1] += c[1] * af;
      acc[i + 2] += c[2] * af;
      acc[i + 3] += af;
    }
  }
  const count = SS * SS;
  const out = Buffer.alloc(SIZE * SIZE * 4);
  for (let i = 0; i < SIZE * SIZE; i++) {
    const a = acc[i * 4 + 3] / count;
    if (a <= 0) continue;
    const sum = acc[i * 4 + 3];
    out[i * 4] = clamp(Math.round(acc[i * 4] / sum), 0, 255);
    out[i * 4 + 1] = clamp(Math.round(acc[i * 4 + 1] / sum), 0, 255);
    out[i * 4 + 2] = clamp(Math.round(acc[i * 4 + 2] / sum), 0, 255);
    out[i * 4 + 3] = clamp(Math.round(a * 255), 0, 255);
  }
  return out;
}

/** alpha 加权的盒式降采样。 */
function downscale(src, size, target) {
  const out = Buffer.alloc(target * target * 4);
  const factor = size / target;
  for (let y = 0; y < target; y++) {
    for (let x = 0; x < target; x++) {
      let r = 0, g = 0, b = 0, a = 0, n = 0;
      const y0 = Math.floor(y * factor);
      const y1 = Math.floor((y + 1) * factor);
      const x0 = Math.floor(x * factor);
      const x1 = Math.floor((x + 1) * factor);
      for (let sy = y0; sy < y1; sy++) {
        for (let sx = x0; sx < x1; sx++) {
          const i = (sy * size + sx) * 4;
          const af = src[i + 3] / 255;
          r += src[i] * af;
          g += src[i + 1] * af;
          b += src[i + 2] * af;
          a += af;
          n++;
        }
      }
      const o = (y * target + x) * 4;
      if (a > 0) {
        out[o] = Math.round(r / a);
        out[o + 1] = Math.round(g / a);
        out[o + 2] = Math.round(b / a);
        out[o + 3] = Math.round((a / n) * 255);
      }
    }
  }
  return out;
}

/** 平面侧面视图：整张 16×16 唱片机侧面贴图 + 红色封面。 */
function flatSide(x, y) {
  const u = x / (SIZE / 16);
  const v = y / (SIZE / 16);
  const c = sideCover(u, v) || texel(u, v);
  return [c[0], c[1], c[2], 255];
}

// ---------- 输出 ----------
const outPath = process.argv[2] ? path.resolve(process.argv[2]) : DEFAULT_OUT;
const rgba = render(flatSide);
const icon = downscale(rgba, SIZE, 128);
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, encodePNG(128, 128, icon));
console.log('wrote', outPath);
