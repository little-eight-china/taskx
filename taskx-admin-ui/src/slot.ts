/** Same as Java {@code CRC32} / Python {@code zlib.crc32}: unsigned IEEE then {@code % slotCount}. */
const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

export function slotOf(taskId: string, slotCount = 32): number | null {
  if (!taskId.trim() || slotCount <= 0) {
    return null;
  }
  const bytes = new TextEncoder().encode(taskId);
  let crc = 0xffffffff;
  for (const b of bytes) {
    crc = (CRC_TABLE[(crc ^ b) & 0xff] ^ (crc >>> 8)) >>> 0;
  }
  return ((crc ^ 0xffffffff) >>> 0) % slotCount;
}
